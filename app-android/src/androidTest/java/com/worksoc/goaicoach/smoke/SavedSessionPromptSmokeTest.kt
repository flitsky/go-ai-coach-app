package com.worksoc.goaicoach.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationResult
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheQuality
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.application.engine.AutoAiTurnResult
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProgress
import com.worksoc.goaicoach.application.engine.EngineSessionCapabilities
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.EngineStartupResult
import com.worksoc.goaicoach.application.engine.LocalEngineMoveResult
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.persistence.GameSessionStore
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
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
        val prefsDir = context.filesDir.resolveSibling("shared_prefs")
        prefsDir.listFiles()?.forEach { it.delete() }

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
                engineName = "Fake Engine",
                engineDiagnostic = "smoke-test",
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

private class FakeNeverReadyEngineSessionClient : EngineSessionClient {
    override val capabilities: EngineSessionCapabilities =
        EngineSessionCapabilities(supportsDeviceBenchmark = false)

    override fun positionAnalysisCacheStatsText(nowMillis: Long): String = "disabled"

    override fun positionAnalysisCacheQualityFor(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
        nowMillis: Long,
    ): PositionAnalysisCacheQuality? = null

    override suspend fun startSession(profile: EngineProfile, state: GameState): EngineStartupResult =
        error("fake engine unavailable in smoke test")

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: com.worksoc.goaicoach.shared.Ruleset,
        handicapCount: Int,
        komi: Double,
    ): EngineStartupResult = error("not used")

    override suspend fun analyzePosition(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
    ): AnalysisResult = error("not used")

    override suspend fun optimizePositionAnalysisCache(
        plan: PositionAnalysisCacheOptimizationPlan,
    ): PositionAnalysisCacheOptimizationResult = error("not used")

    override suspend fun syncAndEstimateGraphScore(state: GameState, profile: EngineProfile): ScoreEstimate =
        error("not used")

    override suspend fun configureSyncAndEstimateGraphScore(state: GameState, profile: EngineProfile): ScoreEstimate =
        error("not used")

    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult = error("not used")

    override suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult = error("not used")

    override suspend fun estimateScoreForState(state: GameState, profile: EngineProfile, syncFirst: Boolean): ScoreEstimate =
        error("not used")

    override suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution = error("not used")

    override suspend fun undoMove(): EngineStatus = error("not used")

    override suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile = error("not used")
}
