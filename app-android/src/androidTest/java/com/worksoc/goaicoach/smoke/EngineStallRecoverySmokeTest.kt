package com.worksoc.goaicoach.smoke

import android.os.Process
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worksoc.goaicoach.MainActivity
import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot
import com.worksoc.goaicoach.engine.DebugEngineStallInjector
import com.worksoc.goaicoach.persistence.DiagnosticEventLog
import com.worksoc.goaicoach.persistence.RuntimeEventLog
import com.worksoc.goaicoach.persistence.UiLanguageStore
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.policy.SearchTimeLimit
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.ui.foundation.TestTags
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStrings
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **엔진이 멎었을 때의 복구를 진짜 앱에서** 확인한다 — refactor backlog #74의 자체 검수(2026-09-28 사용자 결정).
 *
 * #74의 멎음은 손으로 재현할 수 없을 만큼 드물다. 그래서 디버그 빌드의 멈춤 스위치
 * ([DebugEngineStallInjector] — `filesDir/debug-engine-stall`을 **다음 분석 하나**가 읽고 지운다)를 이 테스트가
 * 직접 건다. 테스트 APK는 앱과 같은 프로세스·같은 저장소라 앱의 `filesDir`에 그대로 쓸 수 있다.
 * 그 위는 전부 진짜다 — [MainActivity] → 진짜 KataGo 부트스트랩 → `GoCoachApp` → 「엔진 응답 지연」 팝업.
 *
 * - 「한 번 더 기다리기」 — `late:N`: 분석이 N밀리초 뒤 **시간 초과**로 끝난다(상태 B). 팝업의 기다리기를 누르면
 *   같은 국면을 정상 분석으로 다시 요청하고, AI의 돌이 놓인다.
 * - 「엔진 다시 시작하기」 — `wedge`: 분석이 forceReset까지 **절대 돌아오지 않는다**(상태 A — 와치독 한도를 넘겨
 *   팝업이 뜬다). 다시 시작을 누르면 KataGo 프로세스가 내려가고, 새 프로세스를 다시 맞춘 뒤 정상 분석으로 AI가 둔다.
 *
 * ## ⚠️ 진짜 KataGo가 있어야 한다
 * 멈춤 스위치는 **로컬 KataGo에만** 감긴다(`EngineBootstrap` — 모델이 없으면 스텁 엔진으로 떨어지고 스위치도 없다).
 * 모델은 APK에 없고 `make install-dev-engine TARGET=emu`(= `seed-engine`)가 앱 `filesDir/katago`에 넣는다.
 * 없으면 **건너뛰지 않고 실패한다** — 건너뛴 초록은 아무것도 재지 않는다.
 *
 * ## 조건 — 팝업이 빨리 뜨도록
 * 9줄 판, 탐색 제한 1초(가장 짧다 → 와치독 한도 1초 × 1.2 + 3초 + 여유 5초 = 9.2초), 흑=사람·백=AI(기본값).
 * 추천 수·착수 평가는 기본값대로 꺼져 있어야 한다 — 스위치는 **다음 분석 하나**가 먹으므로, 그 둘이 켜져 있으면
 * AI의 분석보다 먼저 먹는다. 첫 실행 처리(가이드 무장)를 건너뛰도록 온보딩을 본 것으로 둔다 — 코치마크가
 * 반상 터치를 먹지 않게.
 *
 * 에뮬레이터는 느리다(KataGo 재기동·모델 적재가 수십 초 걸릴 수 있다). 기다림의 상한은 넉넉히 둔다.
 *
 * ## ⚠️ 앞뒤로 남은 KataGo 프로세스를 내린다(2026-09-28 실측)
 * 계기 테스트는 한 프로세스에서 [MainActivity]를 **여러 번** 띄운다. 앱은 액티비티가 닫혀도 KataGo 자식 프로세스를
 * 내리지 않으므로(실사용에서는 프로세스 하나 = 액티비티 하나라 문제가 없다) 테스트마다 KataGo가 하나씩 쌓인다.
 * 2GB 에뮬레이터에서 `make test-device`로 넷을 함께 돌리면 `AppLaunchSmokeTest`가 남긴 것까지 셋이 겹쳐
 * **lowmemorykiller가 앱 프로세스를 죽였다**(빈 실패 메시지 + 뒤의 테스트가 아예 안 돈다). 그래서 이 테스트는
 * 시작 전과 끝난 뒤에 같은 uid의 KataGo를 내린다([stopLeftoverKataGoProcesses]) — 앱 코드는 건드리지 않는다.
 */
@RunWith(AndroidJUnit4::class)
class EngineStallRecoverySmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val armFile = File(context.filesDir, DebugEngineStallInjector.ArmFileName)
    private val runtimeLogFile = File(context.filesDir, RuntimeEventLog.FileName)
    private val diagnosticLogFile = File(context.filesDir, DiagnosticEventLog.FileName)
    private var scenario: ActivityScenario<MainActivity>? = null

    /** 앱이 지금 쓰는 언어의 문구 — `ProvideUiLanguage`와 같은 규칙(저장된 언어, 없으면 한국어)으로 고른다. */
    private val strings: UiStrings by lazy {
        val saved = UiLanguageStore(context).loadName()
        UiStrings.forLanguage(UiLanguage.entries.firstOrNull { it.name == saved } ?: UiLanguage.Korean)
    }

    @Before
    fun freshAppWithTheShortestSearchOnNineByNine() {
        stopLeftoverKataGoProcesses()
        resetToFreshInstallState()
        armFile.delete()
        runtimeLogFile.delete()
        diagnosticLogFile.delete()
        UserPreferencesStore(context).save(
            UserPreferencesSnapshot(
                boardSize = BoardSize.Nine,
                searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinOneSecond),
                hasSeenOnboarding = true,
            ),
        )
        assertTrue(
            "진짜 KataGo 모델이 없다 — 멈춤 스위치는 로컬 KataGo에만 감긴다. `make install-dev-engine TARGET=emu`로 모델을 넣을 것.",
            File(context.filesDir, "katago/model.bin.gz").isFile,
        )
    }

    @After
    fun closeTheApp() {
        scenario?.close()
        armFile.delete()
        stopLeftoverKataGoProcesses()
    }

    @Test
    fun waitAgainAfterATimedOutSearchAsksTheEngineAgainAndTheAiStoneLands() {
        startNineByNineGameAgainstTheAi()

        // 다음 분석(= 사람이 둔 뒤의 AI 분석)이 3초 뒤 시간 초과로 끝난다. 와치독 한도(9.2초)보다 먼저 끝나므로,
        // 팝업은 **상태 B**(시간 초과로 끝나 선택을 기다린다) 때문에 뜬다.
        arm("late:3000")
        playHumanMoveAtTheCenter()

        waitForTheEngineStuckPopup()
        assertFalse("스위치는 AI의 분석이 먹었다", armFile.exists())
        assertTrue("탐색이 시간 초과로 끝났다(상태 B)", runtimeEvents("ai_turn_timeout") >= 1)
        assertEquals("AI는 아직 두지 않았다 — genMove로 몰래 두지 않는다", 0, runtimeEvents("ai_turn_success"))
        composeRule.onNodeWithText(moveCountText(1)).assertExists()

        composeRule.onNodeWithText(strings.engineStuckDialogWaitAction).performClick()

        waitForMoveCount(2, "「한 번 더 기다리기」 뒤 AI의 돌")
        waitUntilThePopupIsGone()
        assertEquals(1, runtimeEvents("ai_turn_success"))
        assertEquals("「AI turn failed」로 끝나지 않았다", 0, runtimeEvents("ai_turn_failure"))
    }

    @Test
    fun restartWhileTheEngineIsWedgedRecoversOnAFreshProcessAndTheAiStoneLands() {
        startNineByNineGameAgainstTheAi()

        // 다음 분석이 forceReset(「엔진 다시 시작하기」)까지 돌아오지 않는다 — 와치독 한도를 넘겨 팝업이 뜬다(상태 A).
        arm("wedge")
        playHumanMoveAtTheCenter()

        waitForTheEngineStuckPopup()
        assertFalse("스위치는 AI의 분석이 먹었다", armFile.exists())
        assertEquals("멎은 차례는 끝나지 않았다(상태 A)", 0, runtimeEvents("ai_turn_timeout") + runtimeEvents("ai_turn_success"))
        composeRule.onNodeWithText(moveCountText(1)).assertExists()

        composeRule.onNodeWithText(strings.engineStuckDialogResetAction).performClick()

        waitForMoveCount(2, "「엔진 다시 시작하기」 뒤 새 프로세스에서 AI의 돌")
        waitUntilThePopupIsGone()
        assertTrue("다시 시작이 기록됐다", diagnosticLogFile.readTextOrEmpty().contains("engine_force_reset_requested"))
        // 돌이 놓였다는 것만으로는 모자란다 — 다시 시작이 차례를 취소하지 않아도(#74 이전 배선) 닫힌 파이프의 예외가
        // "진짜 실패"로 읽혀 **같은 차례가 genMove로** 돌을 놓는다(2026-09-28 역검증에서 이 테스트가 그대로 초록이었다).
        // 그래서 ① 다시 시작이 도는 AI 차례를 **취소했다**는 기록(`cancelInFlightAutoAiTurn`만 이 메시지를 쓴다 —
        // 같은 코드를 쓰는 `cancelBackgroundOperations`는 메시지가 다르다)과 ② 그 돌이 genMove 폴백이 아니라
        // 새 차례의 정상 분석에서 왔다는 것을 함께 못박는다.
        assertTrue(
            "다시 시작이 도는 AI 차례를 먼저 취소했다",
            diagnosticLogFile.readTextOrEmpty().lines().any {
                it.contains("\"code\":\"engine_operation_cancelled\"") && it.contains("Cancelled the in-flight AI turn.")
            },
        )
        assertEquals(
            "AI의 돌은 genMove 폴백(「AI replied with …」)이 아니라 정상 분석에서 왔다",
            0,
            runtimeLogFile.readTextOrEmpty().lines().count { it.contains("summary=AI replied with") },
        )
        assertEquals(1, runtimeEvents("ai_turn_success"))
        assertEquals("닫힌 파이프의 예외가 「AI turn failed」로 새지 않았다", 0, runtimeEvents("ai_turn_failure"))
    }

    // ── 앱을 움직이는 손잡이 ──────────────────────────────────────────────────────────────────

    /** 홈 → 대국 설정 → (엔진이 준비되면) 대국 시작. 흑=사람·백=AI, 9줄, 탐색 1초는 저장된 설정에서 온다. */
    private fun startNineByNineGameAgainstTheAi() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        // 스플래시(약 1초)가 터치를 먹는 동안은 눌러도 넘어가지 않는다 — 대국 설정이 보일 때까지 다시 누른다.
        waitUntilDoing(StartupTimeoutMillis, "홈에서 대국 설정으로") {
            if (exists(strings.matchSetup)) return@waitUntilDoing true
            if (exists(strings.startMatch)) composeRule.onNodeWithText(strings.startMatch).performClick()
            false
        }

        // AI가 앉은 대국은 엔진이 준비돼야 시작 버튼이 열린다(첫 실행에는 모델 준비가 몇 초 걸린다).
        val startButton = hasText(strings.startMatchAction, substring = true) and isEnabled()
        composeRule.waitUntil(EngineTimeoutMillis) {
            composeRule.onAllNodes(startButton).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(startButton).performClick()
        composeRule.waitUntil(StartupTimeoutMillis) { exists(moveCountText(0)) }
    }

    /** 반상 가운데를 누른다. 새 대국의 엔진 준비가 끝나야 착수가 받아진다 — 받아질 때까지 다시 누른다. */
    private fun playHumanMoveAtTheCenter() {
        waitUntilDoing(EngineTimeoutMillis, "사람의 첫 수") {
            if (exists(moveCountText(1))) return@waitUntilDoing true
            composeRule.onNodeWithTag(TestTags.GoBoard).performClick()
            false
        }
    }

    private fun waitForTheEngineStuckPopup() {
        composeRule.waitUntil(EngineTimeoutMillis) {
            exists(strings.engineStuckDialogWaitAction) && exists(strings.engineStuckDialogResetAction)
        }
        composeRule.onNodeWithText(strings.engineStuckDialogTitle).assertExists()
    }

    private fun waitUntilThePopupIsGone() {
        composeRule.waitUntil(PopupTimeoutMillis) { !exists(strings.engineStuckDialogTitle) }
    }

    private fun waitForMoveCount(count: Int, what: String) {
        waitUntilDoing(EngineTimeoutMillis, what) { exists(moveCountText(count)) }
    }

    private fun arm(mode: String) {
        armFile.writeText(mode)
        assertTrue(armFile.isFile)
    }

    // ── 도우미 ────────────────────────────────────────────────────────────────────────────

    private fun moveCountText(count: Int): String = "${strings.moveCountPrefix} $count${strings.moveCountSuffix}"

    private fun exists(text: String): Boolean =
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** [step]이 참을 돌려줄 때까지 되풀이한다(한 번에 1초 쉰다). 끝내 안 되면 무엇을 기다렸는지와 최근 로그로 실패한다. */
    private fun waitUntilDoing(timeoutMillis: Long, what: String, step: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            if (step()) return
            check(System.currentTimeMillis() < deadline) {
                "$what — ${timeoutMillis}ms 안에 안 됐다. 최근 런타임 로그:\n" +
                    runtimeLogFile.readTextOrEmpty().lines().takeLast(8).joinToString("\n")
            }
            idleFor(RetryIntervalMillis)
        }
    }

    /** 실제 시간으로 [millis]만큼 쉰다 — `waitUntil`로 쉬어야 그동안 화면의 프레임 시계(와치독의 1초 틱)도 흐른다. */
    private fun idleFor(millis: Long) {
        val until = System.currentTimeMillis() + millis
        composeRule.waitUntil(millis + 5_000L) { System.currentTimeMillis() >= until }
    }

    private fun runtimeEvents(name: String): Int {
        val pattern = Regex("""event=$name\b""")
        return runtimeLogFile.readTextOrEmpty().lines().count { pattern.containsMatchIn(it) }
    }

    private fun File.readTextOrEmpty(): String = if (isFile) readText() else ""

    /**
     * 이 앱(같은 uid)이 띄운 KataGo 프로세스를 전부 내리고, 사라질 때까지 잠깐 기다린다. 앱은 같은 uid의 프로세스만
     * `/proc`에서 볼 수 있고 [Process.killProcess]도 같은 uid에만 닿는다 — 남의 프로세스는 건드릴 수 없다.
     */
    private fun stopLeftoverKataGoProcesses(): Int {
        val pids = File("/proc").listFiles().orEmpty().mapNotNull { dir ->
            val pid = dir.name.toIntOrNull() ?: return@mapNotNull null
            val commandLine = runCatching { File(dir, "cmdline").readText() }.getOrNull() ?: return@mapNotNull null
            pid.takeIf { KataGoExecutableName in commandLine }
        }
        pids.forEach(Process::killProcess)
        val deadline = System.currentTimeMillis() + 5_000L
        while (pids.any { File("/proc/$it").exists() } && System.currentTimeMillis() < deadline) Thread.sleep(100L)
        return pids.size
    }

    private companion object {
        const val StartupTimeoutMillis = 30_000L
        const val EngineTimeoutMillis = 120_000L
        const val PopupTimeoutMillis = 15_000L
        const val RetryIntervalMillis = 1_000L
        const val KataGoExecutableName = "libkatago.so"
    }
}
