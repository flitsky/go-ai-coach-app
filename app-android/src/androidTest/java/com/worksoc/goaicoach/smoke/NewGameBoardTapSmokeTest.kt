package com.worksoc.goaicoach.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.engine.EngineStartupResult
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.engine.EngineIdentity
import com.worksoc.goaicoach.engine.SessionGenerationRelay
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.testsupport.FakeEngineSessionClient
import com.worksoc.goaicoach.ui.GoCoachApp
import com.worksoc.goaicoach.ui.TestTags
import com.worksoc.goaicoach.ui.UiLanguage
import com.worksoc.goaicoach.ui.UiStrings
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M-04 smoke coverage: launch -> new game -> board tap, entirely off the real
 * KataGo engine. [FakeUnavailableEngineSessionClient.startSession] always fails,
 * which routes [GoCoachApp] through its existing local-only fallback path (the
 * same one a real engine-startup failure takes) instead of needing a fake that
 * mimics real engine responses. That keeps this deterministic and fast: no
 * process bootstrap, no coroutine waiting on a real search.
 *
 * Sets both seats to Human (Local Two Player): [MatchPolicy.seatSnapshot][
 * com.worksoc.goaicoach.match.MatchPolicy.seatSnapshot] only allows board input
 * without a ready engine in that mode -- any AI seat requires `isEngineReady`,
 * which this fake deliberately never reaches.
 *
 * 260923: that same seat choice is also what lets the *lobby* start at all. The
 * `대국 시작하기` button is gated on engine readiness (backlog #101 step 0), and
 * until 2026-09-23 the gate ignored the seats -- so this test silently never left
 * the setup screen, tapped the lobby's *preview* board (whose `onCoordinateTap` is
 * empty) and failed on the assertion below. `GameSetupLobby`'s gate now exempts
 * Local Two Player, which is what `EngineUnavailableNoticeDialog` promises the user
 * anyway. ⚠️ If this test starts failing at the board tap again, check that gate
 * first (`EngineReadyGateContractTest` pins both directions).
 *
 * 260814: no onboarding click here anymore -- [FeatureFlags.isLoginEnabled] is
 * `false` (2026-08-09 decision), so [initialDestination] skips onboarding and
 * lands directly on Home. See [AppLaunchSmokeTest] (same package) for the same
 * fix applied to the real-MainActivity launch path.
 */
@RunWith(AndroidJUnit4::class)
class NewGameBoardTapSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val strings = UiStrings.forLanguage(UiLanguage.Korean)

    /**
     * The instrumented test APK shares process/storage with the app under test,
     * so leftover SharedPreferences from a previous run or manual install (saved
     * game, onboarding-seen flag, premium state) would otherwise make navigation
     * from Home non-deterministic (e.g. an unexpected "resume saved game?"
     * dialog). [resetToFreshInstallState] simulates a fresh install so this
     * test's path is the same every time -- and it has to clear the *in-memory*
     * SharedPreferences too, which is exactly what this test used to get wrong;
     * see that function's KDoc.
     */
    @Before
    fun clearPersistedAppState() {
        resetToFreshInstallState()
    }

    @Test
    fun startsLocalGameAndAcceptsBoardTap() {
        composeRule.setContent {
            GoCoachApp(
                engineClient = FakeUnavailableEngineSessionClient(),
                // ⚠️ 이 화면이 쓰는 엔진은 **가짜**다 — 진단 리포트가 로컬 프로세스를 사칭하지
                // 않도록 스텁이라고 명시한다(`EngineModels.kt`의 `EngineMode` 머리말 참고).
                engineIdentity = {
                    EngineIdentity(mode = EngineMode.Stub, name = "Fake Engine", diagnostic = "smoke-test")
                },
                diagnosticEventLog = NoopDiagnosticEventLog,
                sessionGenerationRelay = SessionGenerationRelay(),
            )
        }

        composeRule.onNodeWithText(strings.startMatch).performClick()

        // Both seats to Human, so this is Local Two Player (see the class doc for why that
        // matters with a never-ready fake engine).
        //
        // ⚠️ Black is set explicitly even though it defaults to Human. Relying on that default
        // is what made this test order-dependent: the pill is idempotent (`onSideChange` assigns
        // rather than toggles), so one extra click costs nothing, while a Black seat inherited
        // from a previous test -- or from a future change of default -- silently makes this
        // Ai-vs-Human and re-locks the lobby's start button.
        composeRule.onNodeWithTag(TestTags.seatControllerPill(StoneColor.Black, SeatController.Human))
            .performClick()
        composeRule.onNodeWithTag(TestTags.seatControllerPill(StoneColor.White, SeatController.Human))
            .performClick()

        composeRule.onNodeWithText(strings.startMatchAction).performClick()
        composeRule.onNodeWithTag(TestTags.GoBoard).performClick()

        composeRule.onNodeWithText("${strings.moveCountPrefix} 1${strings.moveCountSuffix}")
            .assertIsDisplayed()
    }
}

/**
 * 엔진이 **끝내 준비되지 않는** 가짜 엔진. 공용 픽스처([FakeEngineSessionClient])는 스텁하지 않은
 * 멤버를 전부 터뜨리므로, 이 테스트가 필요한 것은 사실 **아무것도 스텁하지 않는 것**이다.
 *
 * ⚠️ 그런데도 [startSession]을 **명시적으로** 터뜨린다 — 이 실패가 **이 테스트의 전제**이기 때문이다.
 * 기본 구현에 기대면, 누군가 나중에 픽스처의 [startSession]에 성공값을 넣는 순간 이 테스트는
 * **조용히 다른 것을 재기 시작한다**(함정 24: 초록 ≠ 안전).
 */
private class FakeUnavailableEngineSessionClient : FakeEngineSessionClient() {
    override suspend fun startSession(profile: EngineProfile, state: GameState): EngineStartupResult =
        error("fake engine unavailable in smoke test")
}
