package com.worksoc.goaicoach.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.KgsRankTier
import com.worksoc.goaicoach.ui.designsystem.AppLightColorScheme
import com.worksoc.goaicoach.ui.designsystem.RankTierBadge
import com.worksoc.goaicoach.ui.home.MenuCard
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.kgsRankLabelFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureSubtitleFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureTitleFor
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 기력 구간의 테두리 일곱(백로그 #236)을 **한눈에 보는** 스크린샷 — 테두리는 캔버스로 그려서, 모양이 어긋나도 컴파일러와
 * 단위 테스트가 모른다. 실제 기력을 9단까지 올려 가며 볼 수도 없으니 구간을 나란히 찍어 사람이 본다:
 *
 * ```
 * adb pull /sdcard/Android/data/com.zenit9hub.ai.baduk/files/rank-tier-shots
 * ```
 *
 * 세 장이다 — 홈의 **진짜 카드**([MenuCard])에 앉힌 모습(구간마다 가장 약한 급수), 크게 키운 모습(선이 어긋났는지 본다),
 * 가장 긴 글자인 영어 표기(`20 kyu`)가 판에 들어가는지. [DesignTokenScreenshotTest]처럼 PNG를 쓰기만 하고 비교하지 않는다.
 * ⚠️ 앱의 저장 상태를 건드리지 않는다(갓 설치 상태로 되돌리지 않는다) — 화면 조각만 그린다.
 */
@RunWith(AndroidJUnit4::class)
class RankTierBadgeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tierFrames() {
        var page by mutableStateOf(Page.HomeCardsLower)
        composeRule.setContent {
            MaterialTheme(colorScheme = AppLightColorScheme) {
                Column(
                    modifier = Modifier
                        .testTag(GalleryTag)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (page) {
                        Page.HomeCardsLower -> KgsRankTier.entries.take(4).forEach { HomeCard(it.weakest) }
                        Page.HomeCardsUpper -> KgsRankTier.entries.drop(4).forEach { HomeCard(it.weakest) }
                        Page.Large -> KgsRankTier.entries.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { tier ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        RankTierBadge(
                                            rank = tier.strongest,
                                            label = kgsRankLabelFor(UiLanguage.Korean, tier.strongest),
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .size(180.dp),
                                        )
                                        Text(tier.name)
                                    }
                                }
                            }
                        }
                        Page.EnglishLabels -> KgsRankTier.entries.chunked(4).forEach { row ->
                            Row(
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { tier ->
                                    RankTierBadge(
                                        rank = tier.weakest,
                                        label = kgsRankLabelFor(UiLanguage.English, tier.weakest),
                                        modifier = Modifier.size(60.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Page.entries.forEach { next ->
            page = next
            composeRule.waitForIdle()
            save(next.fileName, composeRule.onNodeWithTag(GalleryTag).captureToImage().asAndroidBitmap())
        }
    }

    @Composable
    private fun HomeCard(rank: KgsRank) {
        MenuCard(
            title = rankMeasureTitleFor(UiLanguage.Korean),
            subtitle = rankMeasureSubtitleFor(UiLanguage.Korean),
            onClick = {},
            icon = { RankTierBadge(rank = rank, label = kgsRankLabelFor(UiLanguage.Korean, rank), modifier = Modifier.fillMaxWidth()) },
        )
    }

    private fun save(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), ShotDirName).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encode failed: $name" }
        }
    }

    private enum class Page(val fileName: String) {
        HomeCardsLower("01_home_cards_bronze_to_platinum"),
        HomeCardsUpper("02_home_cards_diamond_to_grandmaster"),
        Large("03_large"),
        EnglishLabels("04_english_labels"),
    }

    private companion object {
        const val ShotDirName = "rank-tier-shots"
        const val GalleryTag = "rank-tier-gallery"
    }
}
