package com.worksoc.goaicoach.smoke

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.engine.EngineStartupResult
import com.worksoc.goaicoach.engine.EngineIdentity
import com.worksoc.goaicoach.engine.SessionGenerationRelay
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.testsupport.FakeEngineSessionClient
import com.worksoc.goaicoach.ui.foundation.TestTags
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStrings
import com.worksoc.goaicoach.ui.shell.GoCoachApp
import com.worksoc.goaicoach.ui.splash.SplashVisibility
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 디자인 토큰 추출(refactor backlog #51)의 **"보이는 것은 하나도 안 바뀌었다"** 자가 점검용 스크린샷.
 *
 * 색·치수 리터럴을 토큰으로 옮기는 일은 컴파일러도 단위 테스트도 잡아 주지 않는다 — 값 하나가
 * 틀리면 **조용히 다른 색**이 된다. 그래서 바꾸기 **전** 코드와 **후** 코드에서 같은 화면을 찍어
 * 픽셀 단위로 비교한다. 이 테스트 자체는 PNG를 쓰기만 하고 비교는 하지 않는다(기준 이미지를
 * 저장소에 두면 기기·글꼴마다 달라 오히려 거짓 빨강이 난다) — 비교는 사람이 adb로 당겨서 한다:
 *
 * ```
 * adb pull /sdcard/Android/data/com.zenit9hub.ai.baduk/files/design-token-shots before/
 * ```
 *
 * 결정성을 위해: 갓 설치한 상태([resetToFreshInstallState]), 한국어(저장된 언어가 없으면 한국어로
 * 시작한다), 스플래시 생략(1초짜리 전면 오버레이), 엔진 없음(시간에 따라 달라지는 분석 표시 없음),
 * 사람 대 사람 대국(AI 차례가 오지 않는다). 테스트마다 새 액티비티라 화면 간 순서 의존도 없다.
 *
 * ⚠️ 대국 화면의 **착수 시계**는 벽시계라 초 단위가 넘어가면 달라질 수 있다. 그래서 대국 화면은
 * 전체와 별도로 **바둑판 노드만**도 찍어 둔다 — 판 비교는 시계와 무관하다.
 *
 * ⚠️ 대국 화면 왼쪽 위의 버전 표시(`v260928.2347`)는 `BuildConfig.BUILD_TIME`(빌드한 **분**)이다. 전후를
 * 따로 빌드하면 그 글자만 달라지므로, 비교할 두 빌드는 `app-android/build.gradle.kts`의 `buildTime`을
 * **같은 값으로 잠시 고정**해 만든다(커밋하지 말 것). #51에서는 그렇게 16장이 바이트까지 같았다.
 */
@RunWith(AndroidJUnit4::class)
class DesignTokenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val strings = UiStrings.forLanguage(UiLanguage.Korean)

    @Before
    fun freshStateWithoutSplash() {
        resetToFreshInstallState()
        SplashVisibility.resetForTest()
        SplashVisibility.hasPlayedInProcess = true
    }

    @Test
    fun home() {
        launchApp()
        capture("01_home")
    }

    @Test
    fun lobby() {
        launchApp()
        composeRule.onNodeWithText(strings.startMatch).performClick()
        capture("02_lobby")
    }

    @Test
    fun settings() {
        launchApp()
        composeRule.onNodeWithText("\u2699 ${strings.settingsTitle}").performClick()
        capture("03_settings")
    }

    @Test
    fun studyList() {
        launchApp()
        composeRule.onNodeWithText(strings.study).performClick()
        capture("04_study")
    }

    @Test
    fun historyList() {
        launchApp()
        composeRule.onNodeWithText(strings.gameHistoryTitle).performClick()
        capture("05_history")
    }

    @Test
    fun myPage() {
        launchApp()
        composeRule.onNodeWithText("\uD83E\uDDD1 ${strings.myPageTitle}").performClick()
        capture("06_mypage")
    }

    @Test
    fun boardAfterFirstMove() {
        launchApp()
        composeRule.onNodeWithText(strings.startMatch).performClick()
        composeRule.onNodeWithTag(TestTags.seatControllerPill(StoneColor.Black, SeatController.Human))
            .performClick()
        composeRule.onNodeWithTag(TestTags.seatControllerPill(StoneColor.White, SeatController.Human))
            .performClick()
        composeRule.onNodeWithText(strings.startMatchAction).performClick()
        // 판 한가운데(천원)를 누른다 — 좌표가 고정이라 매번 같은 판이 된다. 갓 설치 상태에서는 첫돌이
        // 코치마크의 흡수 층이 처음 몇 번의 터치를 "다음으로"로 먹으므로, 첫 수가 놓일 때까지 누른다.
        val firstMove = "${strings.moveCountPrefix} 1${strings.moveCountSuffix}"
        repeat(MaxBoardTaps) {
            if (composeRule.onAllNodesWithText(firstMove).fetchSemanticsNodes().isNotEmpty()) return@repeat
            composeRule.onNodeWithTag(TestTags.GoBoard).performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText(firstMove).assertExists()
        capture("07_board")
        save("08_board_only", composeRule.onNodeWithTag(TestTags.GoBoard).captureToImage().asAndroidBitmap())
    }

    private fun launchApp() {
        composeRule.setContent {
            GoCoachApp(
                engineClient = UnavailableEngineSessionClient(),
                engineIdentity = {
                    EngineIdentity(mode = EngineMode.Stub, name = "Fake Engine", diagnostic = "screenshot-test")
                },
                diagnosticEventLog = NoopDiagnosticEventLog,
                sessionGenerationRelay = SessionGenerationRelay(),
            )
        }
        composeRule.waitForIdle()
    }

    /**
     * 화면 하나를 찍는다. 코치마크·다이얼로그는 **별도 윈도우**라 루트가 여럿이 되는데, 가장 큰 루트(액티비티
     * 창)를 `name`으로, 나머지를 `name_overlay_N`으로 찍는다 — 오버레이도 테마 색을 쓰므로 비교 대상이다.
     */
    private fun capture(name: String) {
        composeRule.waitForIdle()
        val roots = composeRule.onAllNodes(isRoot())
        val nodes = roots.fetchSemanticsNodes()
        val main = nodes.indices.maxBy { nodes[it].size.width.toLong() * nodes[it].size.height }
        save(name, roots[main].captureToImage().asAndroidBitmap())
        nodes.indices.filter { it != main }.forEachIndexed { overlay, index ->
            save("${name}_overlay_$overlay", roots[index].captureToImage().asAndroidBitmap())
        }
    }

    private fun save(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), ShotDirName).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encode failed: $name" }
        }
    }

    private companion object {
        const val ShotDirName = "design-token-shots"
        const val MaxBoardTaps = 5
    }
}

/** [NewGameBoardTapSmokeTest]의 가짜와 같은 이유로 **끝내 준비되지 않는** 엔진 — 그쪽 KDoc 참고. */
private class UnavailableEngineSessionClient : FakeEngineSessionClient() {
    override suspend fun startSession(profile: EngineProfile, state: GameState): EngineStartupResult =
        error("fake engine unavailable in screenshot test")
}
