package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GameSessionControllerTest {
    @Test
    fun exposesFrequentlyUsedFieldsFromNestedState() {
        val gameState = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val playerSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )
        val state = controllerState(
            core = coreState(
                gameState = gameState,
                isGameEnded = true,
                engineMessage = "Final score complete.",
            ),
            settings = settingsState(playerSetup = playerSetup),
        )

        assertEquals(gameState, state.gameState)
        assertEquals(true, state.isGameEnded)
        assertEquals(playerSetup, state.playerSetup)
        assertEquals(MatchMode.AiVsAi, state.matchMode)
        assertEquals("Final score complete.", state.engineMessage)
    }

    @Test
    fun replacesOneStateHolderWithoutChangingTheOthers() {
        val initial = controllerState()
        val nextCore = initial.core.copy(engineMessage = "Engine ready.")
        val nextSettings = initial.settings.showTopMoves()
        val nextBenchmark = initial.benchmark.startWaitingForEngineSettle()
        val nextSavedSession = initial.savedSession.copy(hasCheckedSavedSession = true)
        val nextCacheOptimization = initial.positionCacheOptimization.startRunning()

        val withCore = initial.withCore(nextCore)
        assertEquals(nextCore, withCore.core)
        assertSame(initial.settings, withCore.settings)
        assertSame(initial.benchmark, withCore.benchmark)
        assertSame(initial.savedSession, withCore.savedSession)
        assertSame(initial.positionCacheOptimization, withCore.positionCacheOptimization)

        val withSettings = initial.withSettings(nextSettings)
        assertSame(initial.core, withSettings.core)
        assertEquals(nextSettings, withSettings.settings)
        assertSame(initial.benchmark, withSettings.benchmark)
        assertSame(initial.savedSession, withSettings.savedSession)
        assertSame(initial.positionCacheOptimization, withSettings.positionCacheOptimization)

        val withBenchmark = initial.withBenchmark(nextBenchmark)
        assertSame(initial.core, withBenchmark.core)
        assertSame(initial.settings, withBenchmark.settings)
        assertEquals(nextBenchmark, withBenchmark.benchmark)
        assertSame(initial.savedSession, withBenchmark.savedSession)
        assertSame(initial.positionCacheOptimization, withBenchmark.positionCacheOptimization)

        val withSavedSession = initial.withSavedSession(nextSavedSession)
        assertSame(initial.core, withSavedSession.core)
        assertSame(initial.settings, withSavedSession.settings)
        assertSame(initial.benchmark, withSavedSession.benchmark)
        assertEquals(nextSavedSession, withSavedSession.savedSession)
        assertSame(initial.positionCacheOptimization, withSavedSession.positionCacheOptimization)

        val withCacheOptimization = initial.withPositionCacheOptimization(nextCacheOptimization)
        assertSame(initial.core, withCacheOptimization.core)
        assertSame(initial.settings, withCacheOptimization.settings)
        assertSame(initial.benchmark, withCacheOptimization.benchmark)
        assertSame(initial.savedSession, withCacheOptimization.savedSession)
        assertEquals(nextCacheOptimization, withCacheOptimization.positionCacheOptimization)
    }

    @Test
    fun effectTypesCarryExistingApplicationPlansWithoutExecutingThem() {
        val gameState = GameState.empty()
        val topMovePlan = buildTopMoveAnalysisPlan(
            targetState = gameState,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val autoAiContext = buildAutoAiTurnExecutionContext(
            gameState = gameState,
            playerSetup = PlayerSetup(black = SidePlayerSetup(controller = SeatController.Ai)),
            searchTimeSettings = SearchTimeSettings(),
            reviewCandidateMoves = emptyList(),
        )

        val topMoveEffect = GameSessionEffect.RunTopMoveAnalysis(
            plan = topMovePlan,
            deep = false,
            automatic = true,
        )
        val autoAiEffect = GameSessionEffect.RunAutoAiTurn(autoAiContext)
        val restoredEffect = GameSessionEffect.SyncRestoredGame(gameState)

        assertSame(topMovePlan, topMoveEffect.plan)
        assertEquals(true, topMoveEffect.automatic)
        assertSame(autoAiContext, autoAiEffect.context)
        assertSame(gameState, restoredEffect.gameState)
    }

    private fun controllerState(
        core: GameSessionCoreState = coreState(),
        settings: GameSessionSettingsState = settingsState(),
        benchmark: EngineBenchmarkUiState = EngineBenchmarkUiState.initial(
            benchmarkText = "No benchmark yet.",
            profile = null,
        ),
        savedSession: SavedSessionUiState = SavedSessionUiState(),
        positionCacheOptimization: PositionAnalysisCacheOptimizationUiState =
            PositionAnalysisCacheOptimizationUiState(),
    ): GameSessionControllerState =
        GameSessionControllerState(
            core = core,
            settings = settings,
            benchmark = benchmark,
            savedSession = savedSession,
            positionCacheOptimization = positionCacheOptimization,
        )

    private fun settingsState(
        playerSetup: PlayerSetup = PlayerSetup(),
    ): GameSessionSettingsState =
        GameSessionSettingsState(
            playerSetup = playerSetup,
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
            searchTimeSettings = SearchTimeSettings(),
            topMovesEnabled = false,
        )

    private fun coreState(
        gameState: GameState = GameState.empty(),
        isGameEnded: Boolean = false,
        engineMessage: String = "Engine not initialized.",
    ): GameSessionCoreState =
        GameSessionCoreState(
            gameState = gameState,
            isGameEnded = isGameEnded,
            analysisState = GameSessionAnalysisState.empty(gameState),
            scoreState = GameSessionScoreState.reset(
                scoreText = "No score estimate yet.",
                scoreSnapshots = listOf(localScoreSnapshot(gameState)),
                endgameLog = "No endgame result recorded.",
            ),
            runtimeState = GameSessionRuntimeState(
                playLevel = PlayLevelSetting(),
                engineProfile = EngineProfile(),
                analysisPreset = AnalysisPreset.Lite,
            ),
            moveReviewState = GameSessionMoveReviewState.reset(
                moveReviewText = "No move review yet.",
                lastMoveText = "None",
            ),
            engineMessage = engineMessage,
        )
}
