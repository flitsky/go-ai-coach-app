package com.worksoc.goaicoach.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStrings
import com.worksoc.goaicoach.ui.l10n.reviewGameActionFor
import com.worksoc.goaicoach.ui.l10n.reviewRecommendationMessageFor
import com.worksoc.goaicoach.ui.play.FinalJudgementDialog
import com.worksoc.goaicoach.ui.play.ReviewRecommendationOverlay
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 대국 뒤 「복기 하기」 추천(백로그 #200)을 **실제로 그려서** 본다 — 배지·굵은 글자는 단위 테스트가 볼 수 없다
 * (글자 굵기와 배지는 측정·그리기 결과다). 앱 데이터를 건드리지 않는다(갓 설치 초기화도 하지 않는다) —
 * 컴포저블만 따로 띄운다. 찍은 그림은 사람이 당겨 본다:
 *
 * ```
 * adb shell am instrument -w -e class com.worksoc.goaicoach.smoke.ReviewRecommendationUiTest \
 *     com.zenit9hub.ai.baduk.test/androidx.test.runner.AndroidJUnitRunner
 * adb pull /sdcard/Android/data/com.zenit9hub.ai.baduk/files/review-recommendation-shots
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ReviewRecommendationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val strings = UiStrings.forLanguage(UiLanguage.Korean)
    private val reviewLabel = reviewGameActionFor(UiLanguage.Korean)

    private val judgement = FinalScoreJudgement(
        winner = StoneColor.Black,
        margin = 12.5,
        ruleset = Ruleset.Japanese,
        isEstimatedDisplay = false,
        removedBlack = 0,
        removedWhite = 0,
        blackArea = 40.0,
        whiteAreaWithKomi = 27.5,
        capturedByBlack = 0,
        capturedByWhite = 0,
        komi = 6.5,
    )

    /** `setContent`는 테스트당 한 번뿐이라, 한 테스트에서 여러 값을 보려고 개수를 상태로 둔다. */
    private val mistakeCount = mutableStateOf<Int?>(null)
    private var dialogShown = false

    private fun showDialog(count: Int?) {
        mistakeCount.value = count
        if (!dialogShown) {
            dialogShown = true
            composeRule.setContent {
                MaterialTheme {
                    FinalJudgementDialog(
                        judgement = judgement,
                        strings = strings,
                        onDismiss = {},
                        onReview = {},
                        onReplay = {},
                        reviewMistakeCount = mistakeCount.value,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.fontWeight(): FontWeight? {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first().layoutInput.style.fontWeight
    }

    @Test
    fun aGameWithMistakesGetsABoldReviewButtonAndACountBadge() {
        showDialog(3)

        composeRule.onNodeWithContentDescription("5점 이상 실착 3개").assertExists()
        assertEquals(FontWeight.Bold, composeRule.onNodeWithText(reviewLabel, useUnmergedTree = true).fontWeight())
        saveDialog("dialog_3")
    }

    @Test
    fun nineOrMoreMistakesShowNinePlus() {
        showDialog(12)

        composeRule.onNodeWithText("9+", useUnmergedTree = true).assertExists()
        // 스크린 리더는 자르지 않은 실제 개수를 읽는다.
        composeRule.onNodeWithContentDescription("5점 이상 실착 12개").assertExists()
        saveDialog("dialog_12")
    }

    /** 0개와 기록 없음(`null`)은 **지금까지와 똑같다** — 배지도 굵은 글자도 없다. */
    @Test
    fun noMistakesOrNoDataLooksExactlyLikeBefore() {
        listOf(0, null).forEach { count ->
            showDialog(count)
            composeRule.onAllNodesWithContentDescription("실착", substring = true).assertCountEquals(0)
            composeRule.onAllNodesWithText("9+", useUnmergedTree = true).assertCountEquals(0)
            // 「지금까지와 똑같다」 = 옆의 「확인」과 같은 굵기(TextButton 기본 labelLarge, Medium 500)다.
            assertEquals(
                "count=$count 인데 「복기 하기」가 「확인」과 굵기가 다르다",
                composeRule.onNodeWithText(strings.reviewJudgement, useUnmergedTree = true).fontWeight(),
                composeRule.onNodeWithText(reviewLabel, useUnmergedTree = true).fontWeight(),
            )
            saveDialog("dialog_${count ?: "null"}")
        }
    }

    /** 말풍선: 판 **아래**에 서고, 누르면 복기, ✕는 닫기만. */
    @Test
    fun theBubbleSitsBelowTheBoardAndOpensReviewOrCloses() {
        var opened = 0
        var dismissed = 0
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val boardSide = 360f * density
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(400.dp, 800.dp)) {
                    ReviewRecommendationOverlay(
                        boardSlotInRoot = { Rect(left = 20f * density, top = 60f * density, right = 20f * density + boardSide, bottom = 60f * density + boardSide) },
                        originInRoot = { Offset.Zero },
                        character = BotCharacterCatalog.all.first(),
                        mistakeCount = 4,
                        language = UiLanguage.Korean,
                        closeLabel = strings.close,
                        onOpenReview = { opened += 1 },
                        onDismiss = { dismissed += 1 },
                        modifier = Modifier.size(400.dp, 800.dp),
                    )
                }
            }
        }
        val message = reviewRecommendationMessageFor(UiLanguage.Korean, 4)
        val bubbleTop = composeRule.onNodeWithText(message, useUnmergedTree = true).getBoundsInRoot().top
        assertTrue("말풍선이 판과 겹친다: top=$bubbleTop", bubbleTop.value >= 60f + 360f)

        composeRule.onNodeWithText(message, useUnmergedTree = true).performClick()
        composeRule.onNodeWithContentDescription(strings.close).performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
        assertEquals(1, dismissed)
    }

    /**
     * 가장 긴 문장(영어) × 글꼴 크게(1.3, 이 앱의 최대 — 함정 65)에서도 말풍선이 잘리지 않고 화면 폭 안에 선다(함정 9·21).
     * 그림은 사람이 본다(`bubble_en_1.3.png`).
     */
    @Test
    fun theLongestMessageAtTheLargestFontStillFits() {
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = density, fontScale = 1.3f)) {
                MaterialTheme {
                    Box(modifier = Modifier.size(411.dp, 700.dp).testTag(OverlayTag)) {
                        ReviewRecommendationOverlay(
                            boardSlotInRoot = { Rect(0f, 0f, 411f * density, 411f * density) },
                            originInRoot = { Offset.Zero },
                            character = BotCharacterCatalog.all.last(),
                            mistakeCount = 12,
                            language = UiLanguage.English,
                            closeLabel = UiStrings.forLanguage(UiLanguage.English).close,
                            onOpenReview = {},
                            onDismiss = {},
                            modifier = Modifier.size(411.dp, 700.dp),
                        )
                    }
                }
            }
        }
        val message = reviewRecommendationMessageFor(UiLanguage.English, 12)
        val bounds = composeRule.onNodeWithText(message, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("말풍선 글이 화면 밖으로 나간다: $bounds", bounds.right.value <= 411f && bounds.left.value >= 0f)
        assertTrue("말풍선이 판과 겹친다: $bounds", bounds.top.value >= 411f)
        save("bubble_en_1.3", composeRule.onNodeWithTag(OverlayTag).captureToImage().asAndroidBitmap())
    }

    private fun saveDialog(name: String) {
        composeRule.waitForIdle()
        save(name, composeRule.onNode(isDialog()).captureToImage().asAndroidBitmap())
    }

    private fun save(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "review-recommendation-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encode failed: $name" }
        }
    }

    private companion object {
        const val OverlayTag = "review-recommendation-overlay"
    }
}
