package com.worksoc.goaicoach.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.engine.EngineStartupResult
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.persistence.GameSessionStore
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.engine.EngineIdentity
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
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
 * M-04 smoke coverage: the saved-session-prompt flow. Seeds a resumable
 * [SavedGameSnapshot] into the real [GameSessionStore] (SharedPreferences)
 * before composing, then verifies the blinking "이어하기" pill appears on
 * Home, tapping it shows the resume dialog (`ResumeSavedSessionDialog`), and
 * confirming resume navigates into the board with the saved move already
 * applied -- not just that some board appears, but that the *restored* state
 * (one pre-existing move, no taps needed) is what's shown.
 *
 * Both seats are Human so no AI turn is ever scheduled after resume --
 * [FakeNeverReadyEngineSessionClient] never reaches engine-ready, and an AI
 * seat would otherwise need that (same constraint [NewGameBoardTapSmokeTest]
 * documents for board-tap input).
 */
@RunWith(AndroidJUnit4::class)
class SavedSessionPromptSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val strings = UiStrings.forLanguage(UiLanguage.Korean)

    /**
     * Same shared_prefs wipe as the other smoke tests, but this one then
     * seeds a resumable snapshot right back in -- the whole point of this
     * test is to start from a state where one exists.
     */
    @Before
    fun seedResumableSavedSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        resetToFreshInstallState()

        val gameState = GameState.empty(boardSize = BoardSize.Nine)
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val snapshot = SavedGameSnapshot(
            gameState = gameState,
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Human),
                white = SidePlayerSetup(controller = SeatController.Human),
            ),
            playLevel = PlayLevelSetting(),
            topMovesEnabled = false,
            savedAtMillis = System.currentTimeMillis(),
        )
        GameSessionStore(context).save(snapshot)
    }

    @Test
    fun resumePromptAppearsAndRestoresSavedGameOnConfirm() {
        composeRule.setContent {
            GoCoachApp(
                engineClient = FakeNeverReadyEngineSessionClient(),
                // ⚠️ 이 화면이 쓰는 엔진은 **가짜**다 — 진단 리포트가 로컬 프로세스를 사칭하지
                // 않도록 스텁이라고 명시한다(`EngineModels.kt`의 `EngineMode` 머리말 참고).
                engineIdentity = {
                    EngineIdentity(mode = EngineMode.Stub, name = "Fake Engine", diagnostic = "smoke-test")
                },
                diagnosticEventLog = NoopDiagnosticEventLog,
            )
        }

        composeRule.onNodeWithText("▶ " + strings.resumeTitle).performClick()
        composeRule.onNodeWithText(strings.resumeTitle).assertIsDisplayed()

        composeRule.onNodeWithText(strings.yes).performClick()

        composeRule.onNodeWithTag(TestTags.GoBoard).assertIsDisplayed()
        composeRule.onNodeWithText("${strings.moveCountPrefix} 1${strings.moveCountSuffix}")
            .assertIsDisplayed()
    }
}

/**
 * 같은 이유로 **끝내 준비되지 않는** 가짜 엔진 — 사유는 [NewGameBoardTapSmokeTest]의 같은 자리에 있다.
 * 이어받기 뒤 AI 차례가 절대 잡히지 않아야 이 테스트가 재는 것이 복원된 국면 하나로 고정된다.
 */
private class FakeNeverReadyEngineSessionClient : FakeEngineSessionClient() {
    override suspend fun startSession(profile: EngineProfile, state: GameState): EngineStartupResult =
        error("fake engine unavailable in smoke test")
}
