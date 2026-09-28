package com.worksoc.goaicoach.smoke

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
import com.worksoc.goaicoach.persistence.UiLanguageStore
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStrings
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **액티비티가 몇 번 다시 만들어져도 KataGo는 종류마다 하나를 넘지 않는다** — refactor backlog #110.
 *
 * ## 무엇이 쌓였나
 * 엔진 조립(부트스트랩·어댑터·세션 클라이언트)이 [MainActivity]의 컴포지션 안에 있어, 같은 프로세스에서 액티비티가
 * 새로 만들어질 때마다 새 어댑터가 KataGo를 새로 띄우고 옛것은 아무도 내리지 않았다. 계기 테스트가 한 프로세스에서
 * [MainActivity]를 여러 번 띄우자 2GB 에뮬레이터에서 lowmemorykiller가 앱을 죽였다(2026-09-28). 실사용에서도 같은 길이
 * 있다 — 안드로이드 11 이하에서 홈 화면 뒤로가기로 나갔다 다시 켜기, 「활동 유지 안 함」, `configChanges` 밖의 설정 변경
 * (굵은 글꼴 토글 등). 루프 하나가 그 두 모양(닫고 다시 띄우기 · 그 자리에서 다시 만들기)을 하나씩 잰다.
 *
 * ## 세는 법
 * [kataGoProcessCensus] — 앱 uid의 `/proc`만 본다. 기준은 **종류마다** 1개 이하다(gtp ≤ 1, analysis ≤ 1). 어댑터 하나가
 * 두 종류를 하나씩 띄우므로 합계 1은 정상 동작에서도 틀린 기준이다. 내려간 프로세스가 거둬지기 전의 짧은 창이 있어
 * 한 번 보고 끝내지 않고 [SettleMillis]까지 다시 본다. 엔진 준비 뒤에는 gtp가 **하나 이상** 보여야 한다 — 세는 법이
 * 고장나 늘 0을 답하는 거짓 초록을 막는다.
 *
 * ## 전제
 * 진짜 KataGo 모델이 있어야 한다(`make install-dev-engine TARGET=emu`). 없으면 스텁으로 떨어져 셀 것이 없으므로
 * **건너뛰지 않고 실패한다.** 앞뒤로 남은 KataGo를 내린다([stopLeftoverKataGoProcesses]) — ⚠️ **루프 도중에는 내리지
 * 않는다.** 그것이 이 테스트가 재는 것이다.
 */
@RunWith(AndroidJUnit4::class)
class EngineProcessCountSmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val observations = mutableListOf<String>()

    /** 앱이 지금 쓰는 언어의 문구 — `ProvideUiLanguage`와 같은 규칙(저장된 언어, 없으면 한국어)으로 고른다. */
    private val strings: UiStrings by lazy {
        val saved = UiLanguageStore(context).loadName()
        UiStrings.forLanguage(UiLanguage.entries.firstOrNull { it.name == saved } ?: UiLanguage.Korean)
    }

    @Before
    fun freshAppWithTheRealEngine() {
        stopLeftoverKataGoProcesses()
        resetToFreshInstallState()
        // 흑=사람·백=AI(기본값)라 로비의 시작 버튼이 엔진 준비를 기다린다. 온보딩을 본 것으로 둬 첫 실행 처리를 건너뛴다.
        UserPreferencesStore(context).save(UserPreferencesSnapshot(boardSize = BoardSize.Nine, hasSeenOnboarding = true))
        assertTrue(
            "진짜 KataGo 모델이 없다 — 스텁 엔진으로는 셀 프로세스가 없다. `make install-dev-engine TARGET=emu`로 모델을 넣을 것.",
            File(context.filesDir, "katago/model.bin.gz").isFile,
        )
    }

    @After
    fun closeTheApp() {
        scenario?.close()
        stopLeftoverKataGoProcesses()
    }

    /** 닫고 다시 띄우기 — 계기 테스트가 #110을 낳은 바로 그 모양이고, 안드로이드 11 이하의 뒤로가기 뒤 다시 켜기와 같다. */
    @Test
    fun launchingAndClosingTheActivityAgainAndAgainNeverStacksKataGo() {
        repeat(Rounds) { round ->
            val launched = ActivityScenario.launch(MainActivity::class.java)
            scenario = launched
            waitUntilTheEngineIsReady()
            assertAtMostOneKataGoPerKind("${round + 1}번째 실행 — 엔진 준비 뒤", engineIsUp = true)
            launched.close()
            scenario = null
            assertAtMostOneKataGoPerKind("${round + 1}번째 실행 — 닫은 뒤")
        }
    }

    /** 그 자리에서 다시 만들기 — `configChanges` 밖의 설정 변경·「활동 유지 안 함」과 같은 모양(프로세스는 그대로). */
    @Test
    fun recreatingTheActivityAgainAndAgainNeverStacksKataGo() {
        val launched = ActivityScenario.launch(MainActivity::class.java)
        scenario = launched
        waitUntilTheEngineIsReady()
        assertAtMostOneKataGoPerKind("처음 실행 — 엔진 준비 뒤", engineIsUp = true)
        repeat(Rounds) { round ->
            launched.recreate()
            waitUntilTheEngineIsReady()
            assertAtMostOneKataGoPerKind("${round + 1}번째 재생성 — 엔진 준비 뒤", engineIsUp = true)
        }
    }

    // ── 앱을 움직이는 손잡이 ──────────────────────────────────────────────────────────────────

    /**
     * 홈 → 대국 설정 → AI가 앉은 로비의 시작 버튼이 열릴 때까지. 그 버튼은 **지금 떠 있는 화면의** 엔진 기동이 끝나야
     * 열린다 — 다시 만들어진 화면이면 그 화면의 기동이다.
     */
    private fun waitUntilTheEngineIsReady() {
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
    }

    /**
     * gtp ≤ 1, analysis ≤ 1을 [SettleMillis]까지 다시 보며 단언한다. 실패 메시지에는 이 테스트의 모든 관측이 실린다
     * (RED 수치를 그대로 읽을 수 있게).
     */
    private fun assertAtMostOneKataGoPerKind(moment: String, engineIsUp: Boolean = false) {
        val deadline = System.currentTimeMillis() + SettleMillis
        var census = kataGoProcessCensus()
        while (census.stacked() && System.currentTimeMillis() < deadline) {
            Thread.sleep(PollMillis)
            census = kataGoProcessCensus()
        }
        val gtp = census.count { it.kind == "gtp" }
        val analysis = census.count { it.kind == "analysis" }
        observations += "$moment: gtp=$gtp analysis=$analysis ${census.map { "${it.pid}/${it.kind}/${it.state}" }}"
        val trace = observations.joinToString("\n")
        if (engineIsUp) {
            assertTrue("엔진이 준비됐는데 gtp가 안 보인다 — 세는 법이 고장났거나 스텁으로 떨어졌다.\n$trace", gtp >= 1)
        }
        assertTrue("KataGo가 쌓였다 — 종류마다 1개 이하여야 한다(#110).\n$trace", gtp <= 1 && analysis <= 1)
    }

    private fun List<KataGoProcess>.stacked(): Boolean =
        count { it.kind == "gtp" } > 1 || count { it.kind == "analysis" } > 1

    // ── 도우미 ────────────────────────────────────────────────────────────────────────────

    private fun exists(text: String): Boolean =
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** [step]이 참을 돌려줄 때까지 되풀이한다(한 번에 1초 쉰다). 끝내 안 되면 무엇을 기다렸는지로 실패한다. */
    private fun waitUntilDoing(timeoutMillis: Long, what: String, step: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            if (step()) return
            check(System.currentTimeMillis() < deadline) { "$what — ${timeoutMillis}ms 안에 안 됐다." }
            val until = System.currentTimeMillis() + RetryIntervalMillis
            composeRule.waitUntil(RetryIntervalMillis + 5_000L) { System.currentTimeMillis() >= until }
        }
    }

    private companion object {
        const val Rounds = 4
        const val SettleMillis = 5_000L
        const val PollMillis = 250L
        const val StartupTimeoutMillis = 30_000L
        const val EngineTimeoutMillis = 120_000L
        const val RetryIntervalMillis = 1_000L
    }
}
