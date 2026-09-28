package com.worksoc.goaicoach.smoke

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worksoc.goaicoach.MainActivity
import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot
import com.worksoc.goaicoach.engine.DebugEngineStallInjector
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.persistence.DiagnosticEventLog
import com.worksoc.goaicoach.persistence.RuntimeEventLog
import com.worksoc.goaicoach.persistence.UiLanguageStore
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.policy.SearchTimeLimit
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
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
 * **AI 대 AI가 진짜 KataGo 위에서 멈추지 않고 이어지는지**(refactor backlog #14·#15의 회귀 그물, 2026-09-28).
 *
 * #14(프로세스 기동·폐기의 세대, 시간 초과가 막힌 읽기를 끊기)와 #15(오퍼레이션 락 — 추천 수·형세는 포기, 취소된
 * 호출자는 곧바로 돌아가고 답은 배수가 받는다)는 둘 다 엔진 명령의 **순서와 기다림**을 바꾼다. 어긋나면 증상은
 * 하나다 — AI 대 AI가 몇 수 뒤에 멈추고 「엔진 응답 지연」이 뜬다(2026-09 AI 대 AI 멈춤 로그). 단위 테스트는 가짜
 * 엔진 위의 순서를 못박고, 이 테스트는 진짜 앱 → 진짜 KataGo에서 **수가 계속 놓이는 것**을 본다.
 *
 * 9줄 판·탐색 1초(가장 짧다 — 한 수가 빨리 돌아 같은 시간에 오퍼레이션이 가장 많이 겹친다)·흑백 모두 AI·지연 없음.
 * [TargetMoveCount]수가 놓일 때까지 기다리며 매초 본다 — 팝업이 뜨지 않았고 수순이 줄지 않았다. 끝에서 런타임 로그에
 * `ai_turn_failure`·`ai_turn_timeout`이 하나도 없어야 한다.
 *
 * ⚠️ 진짜 KataGo가 있어야 한다 — 없으면 스텁 엔진으로 떨어져 아무것도 재지 않으므로 **건너뛰지 않고 실패한다**.
 * `make install-dev-engine TARGET=emu`가 모델을 넣는다. 앞뒤로 남은 KataGo를 내리는 사유는
 * [EngineStallRecoverySmokeTest]의 KDoc(2GB 에뮬레이터의 lowmemorykiller).
 */
@RunWith(AndroidJUnit4::class)
class AiVsAiSoakSmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val runtimeLogFile = File(context.filesDir, RuntimeEventLog.FileName)
    private val diagnosticLogFile = File(context.filesDir, DiagnosticEventLog.FileName)
    private var scenario: ActivityScenario<MainActivity>? = null

    /** 앱이 지금 쓰는 언어의 문구 — `ProvideUiLanguage`와 같은 규칙(저장된 언어, 없으면 한국어)으로 고른다. */
    private val strings: UiStrings by lazy {
        val saved = UiLanguageStore(context).loadName()
        UiStrings.forLanguage(UiLanguage.entries.firstOrNull { it.name == saved } ?: UiLanguage.Korean)
    }

    @Before
    fun freshAppWithTwoAisOnNineByNineAndTheShortestSearch() {
        stopLeftoverKataGoProcesses()
        resetToFreshInstallState()
        File(context.filesDir, DebugEngineStallInjector.ArmFileName).delete()
        runtimeLogFile.delete()
        diagnosticLogFile.delete()
        UserPreferencesStore(context).save(
            UserPreferencesSnapshot(
                boardSize = BoardSize.Nine,
                playerSetup = PlayerSetup(
                    black = SidePlayerSetup(controller = SeatController.Ai),
                    white = SidePlayerSetup(controller = SeatController.Ai),
                ),
                searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinOneSecond),
                hasSeenOnboarding = true,
            ),
        )
        assertTrue(
            "진짜 KataGo 모델이 없다 — 스텁 엔진으로는 이 테스트가 아무것도 재지 않는다. `make install-dev-engine TARGET=emu`로 모델을 넣을 것.",
            File(context.filesDir, "katago/model.bin.gz").isFile,
        )
    }

    @After
    fun closeTheApp() {
        scenario?.close()
        stopLeftoverKataGoProcesses()
    }

    @Test
    fun aiVersusAiKeepsPlayingWithoutAStallPopupOrAFailedTurn() {
        startTheAiVersusAiGame()

        val first = waitForAMoveCount()
        var last = first
        val deadline = System.currentTimeMillis() + SoakTimeoutMillis
        while (last < TargetMoveCount) {
            assertFalse("「${strings.engineStuckDialogTitle}」 팝업이 떴다(수순 $last).\n${recentLogs()}", theStallPopupIsShown())
            check(System.currentTimeMillis() < deadline) {
                "${SoakTimeoutMillis}ms 안에 ${TargetMoveCount}수에 닿지 못했다(수순 $first → $last).\n${recentLogs()}"
            }
            idleFor(PollIntervalMillis)
            val now = currentMoveCount() ?: last
            assertTrue("수순이 줄었다($last → $now).\n${recentLogs()}", now >= last)
            last = now
        }

        assertTrue("수순이 늘었다($first → $last)", last > first)
        assertFalse("「${strings.engineStuckDialogTitle}」 팝업이 떴다(끝).\n${recentLogs()}", theStallPopupIsShown())
        assertEquals("「AI turn failed」로 끝난 차례가 없다.\n${recentLogs()}", 0, runtimeEvents("ai_turn_failure"))
        assertEquals("시간 초과로 끝난 차례가 없다.\n${recentLogs()}", 0, runtimeEvents("ai_turn_timeout"))
    }

    // ── 앱을 움직이는 손잡이 ──────────────────────────────────────────────────────────────────

    /** 홈 → 대국 설정 → (엔진이 준비되면) 대국 시작. 흑백 AI·9줄·탐색 1초는 저장된 설정에서 온다. */
    private fun startTheAiVersusAiGame() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        // 스플래시(약 1초)가 터치를 먹는 동안은 눌러도 넘어가지 않는다 — 대국 설정이 보일 때까지 다시 누른다.
        waitUntilDoing(StartupTimeoutMillis, "홈에서 대국 설정으로") {
            if (exists(strings.matchSetup)) return@waitUntilDoing true
            if (exists(strings.startMatch)) composeRule.onNodeWithText(strings.startMatch).performClick()
            false
        }

        val startButton = hasText(strings.startMatchAction, substring = true) and isEnabled()
        composeRule.waitUntil(EngineTimeoutMillis) {
            composeRule.onAllNodes(startButton).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(startButton).performClick()
    }

    /** 대국 화면의 수순이 보일 때까지 기다린다 — 지연이 없어 첫 수가 이미 놓였을 수 있으므로 0을 기대하지 않는다. */
    private fun waitForAMoveCount(): Int {
        var count: Int? = null
        waitUntilDoing(StartupTimeoutMillis, "대국 화면의 수순") {
            count = currentMoveCount()
            count != null
        }
        return checkNotNull(count)
    }

    // ── 도우미 ────────────────────────────────────────────────────────────────────────────

    /** 화면의 「수순 N수」 중 가장 큰 N — 없으면 null. */
    private fun currentMoveCount(): Int? {
        val pattern = Regex("^${Regex.escape(strings.moveCountPrefix)} (\\d+)${Regex.escape(strings.moveCountSuffix)}$")
        return composeRule.onAllNodes(hasText(strings.moveCountPrefix, substring = true))
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .mapNotNull { text -> pattern.matchEntire(text.text)?.groupValues?.get(1)?.toInt() }
            .maxOrNull()
    }

    private fun theStallPopupIsShown(): Boolean =
        exists(strings.engineStuckDialogTitle) || exists(strings.engineStuckDialogResetAction)

    private fun exists(text: String): Boolean =
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** [step]이 참을 돌려줄 때까지 되풀이한다(한 번에 1초 쉰다). 끝내 안 되면 무엇을 기다렸는지와 최근 로그로 실패한다. */
    private fun waitUntilDoing(timeoutMillis: Long, what: String, step: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            if (step()) return
            check(System.currentTimeMillis() < deadline) { "$what — ${timeoutMillis}ms 안에 안 됐다.\n${recentLogs()}" }
            idleFor(PollIntervalMillis)
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

    private fun recentLogs(): String =
        "최근 런타임 로그:\n" + runtimeLogFile.readTextOrEmpty().lines().takeLast(8).joinToString("\n") +
            "\n최근 진단 로그:\n" + diagnosticLogFile.readTextOrEmpty().lines().takeLast(6).joinToString("\n")

    private fun File.readTextOrEmpty(): String = if (isFile) readText() else ""

    private companion object {
        const val TargetMoveCount = 20
        const val SoakTimeoutMillis = 180_000L
        const val StartupTimeoutMillis = 30_000L
        const val EngineTimeoutMillis = 120_000L
        const val PollIntervalMillis = 1_000L
    }
}
