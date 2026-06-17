package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.*
import com.worksoc.goaicoach.application.humanmove.*

import com.worksoc.goaicoach.application.debugreport.*

import com.worksoc.goaicoach.application.savedgame.*

import com.worksoc.goaicoach.application.engine.*
import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.autoai.*

import com.worksoc.goaicoach.application.topmoves.*
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
    fun builderComposesControllerStateFromCurrentStateHolders() {
        val gameState = GameState.empty(nextPlayer = StoneColor.White)
        val analysis = GameSessionAnalysisState.empty(gameState, candidateText = "candidate text")
        val score = GameSessionScoreState.reset(
            scoreText = "score text",
            scoreSnapshots = listOf(localScoreSnapshot(gameState)),
            endgameLog = "endgame log",
        )
        val runtime = GameSessionRuntimeState(
            playLevel = PlayLevelSetting(level = 2),
            engineProfile = EngineProfile(name = "Builder"),
            analysisPreset = AnalysisPreset.Balanced,
        )
        val review = GameSessionMoveReviewState.reset(
            moveReviewText = "review",
            lastMoveText = "White pass",
        )
        val settings = settingsState().showTopMoves()
        val benchmark = EngineBenchmarkUiState(
            benchmarkText = "benchmark",
            searchTimeBenchmarkAverages = mapOf(16 to 1_000.0),
        )
        val saved = SavedSessionUiState(hasCheckedSavedSession = true)
        val autoAiTurn = AutoAiTurnUiState().markScheduled()
        val cacheOptimization = PositionAnalysisCacheOptimizationUiState().startRunning()

        val state = buildGameSessionControllerState(
            gameState = gameState,
            isGameEnded = true,
            analysisState = analysis,
            scoreState = score,
            runtimeState = runtime,
            moveReviewState = review,
            engineMessage = "engine",
            settings = settings,
            benchmark = benchmark,
            savedSession = saved,
            autoAiTurn = autoAiTurn,
            positionCacheOptimization = cacheOptimization,
        )

        assertEquals(gameState, state.gameState)
        assertEquals(true, state.isGameEnded)
        assertEquals(analysis, state.core.analysisState)
        assertEquals(score, state.core.scoreState)
        assertEquals(runtime, state.core.runtimeState)
        assertEquals(review, state.core.moveReviewState)
        assertEquals("engine", state.engineMessage)
        assertEquals(settings, state.settings)
        assertEquals(benchmark, state.benchmark)
        assertEquals(saved, state.savedSession)
        assertEquals(autoAiTurn, state.autoAiTurn)
        assertEquals(cacheOptimization, state.positionCacheOptimization)
    }

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
        assertEquals(false, state.shouldShowResumePrompt)
        assertEquals(false, state.isAutoAiTurnPending)
    }

    @Test
    fun replacesOneStateHolderWithoutChangingTheOthers() {
        val initial = controllerState()
        val nextCore = initial.core.copy(engineMessage = "Engine ready.")
        val nextSettings = initial.settings.showTopMoves()
        val nextBenchmark = initial.benchmark.startWaitingForEngineSettle()
        val nextSavedSession = initial.savedSession.copy(hasCheckedSavedSession = true)
        val nextAutoAiTurn = initial.autoAiTurn.markScheduled()
        val nextCacheOptimization = initial.positionCacheOptimization.startRunning()

        val withCore = initial.withCore(nextCore)
        assertEquals(nextCore, withCore.core)
        assertSame(initial.settings, withCore.settings)
        assertSame(initial.benchmark, withCore.benchmark)
        assertSame(initial.savedSession, withCore.savedSession)
        assertSame(initial.autoAiTurn, withCore.autoAiTurn)
        assertSame(initial.positionCacheOptimization, withCore.positionCacheOptimization)

        val withSettings = initial.withSettings(nextSettings)
        assertSame(initial.core, withSettings.core)
        assertEquals(nextSettings, withSettings.settings)
        assertSame(initial.benchmark, withSettings.benchmark)
        assertSame(initial.savedSession, withSettings.savedSession)
        assertSame(initial.autoAiTurn, withSettings.autoAiTurn)
        assertSame(initial.positionCacheOptimization, withSettings.positionCacheOptimization)

        val withBenchmark = initial.withBenchmark(nextBenchmark)
        assertSame(initial.core, withBenchmark.core)
        assertSame(initial.settings, withBenchmark.settings)
        assertEquals(nextBenchmark, withBenchmark.benchmark)
        assertSame(initial.savedSession, withBenchmark.savedSession)
        assertSame(initial.autoAiTurn, withBenchmark.autoAiTurn)
        assertSame(initial.positionCacheOptimization, withBenchmark.positionCacheOptimization)

        val withSavedSession = initial.withSavedSession(nextSavedSession)
        assertSame(initial.core, withSavedSession.core)
        assertSame(initial.settings, withSavedSession.settings)
        assertSame(initial.benchmark, withSavedSession.benchmark)
        assertEquals(nextSavedSession, withSavedSession.savedSession)
        assertSame(initial.autoAiTurn, withSavedSession.autoAiTurn)
        assertSame(initial.positionCacheOptimization, withSavedSession.positionCacheOptimization)

        val withAutoAiTurn = initial.withAutoAiTurn(nextAutoAiTurn)
        assertSame(initial.core, withAutoAiTurn.core)
        assertSame(initial.settings, withAutoAiTurn.settings)
        assertSame(initial.benchmark, withAutoAiTurn.benchmark)
        assertSame(initial.savedSession, withAutoAiTurn.savedSession)
        assertEquals(nextAutoAiTurn, withAutoAiTurn.autoAiTurn)
        assertSame(initial.positionCacheOptimization, withAutoAiTurn.positionCacheOptimization)

        val withCacheOptimization = initial.withPositionCacheOptimization(nextCacheOptimization)
        assertSame(initial.core, withCacheOptimization.core)
        assertSame(initial.settings, withCacheOptimization.settings)
        assertSame(initial.benchmark, withCacheOptimization.benchmark)
        assertSame(initial.savedSession, withCacheOptimization.savedSession)
        assertSame(initial.autoAiTurn, withCacheOptimization.autoAiTurn)
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
        val autoAiRunPlan = AutoAiTurnRunPlan(
            delayMillis = 500L,
            context = autoAiContext,
        )
        val autoAiEndgamePlan = AutoAiTurnEndgamePlan.Resolve(
            state = gameState,
            profile = EngineProfile(),
            prePassCandidates = emptyList(),
            engineMessagePrefix = "AI selected pass.",
        )
        val humanSyncPlan = HumanEngineSyncRunPlan(
            afterMove = gameState,
            profile = EngineProfile(),
            move = Move.Pass(StoneColor.Black),
            previousReviewCandidates = emptyList(),
        )
        val startupProfile = EngineProfile(name = "Startup")
        val newGameProfile = EngineProfile(name = "NewGame")

        val topMoveEffect = GameSessionEffect.RunTopMoveAnalysis(
            plan = topMovePlan,
            deep = false,
            automatic = true,
        )
        val autoAiEffect = GameSessionEffect.RunAutoAiTurn(autoAiRunPlan)
        val autoAiEndgameEffect = GameSessionEffect.ResolveAutoAiEndgame(autoAiEndgamePlan)
        val humanSyncEffect = GameSessionEffect.SyncHumanMove(humanSyncPlan)
        val startupEffect = GameSessionEffect.StartEngineSession(gameState, startupProfile)
        val newGameEffect = GameSessionEffect.StartEngineBackedGame(
            currentState = gameState,
            profile = newGameProfile,
            boardSize = BoardSize.Nine,
            ruleset = gameState.ruleset,
        )
        val undoEffect = GameSessionEffect.UndoEngineMoves(gameState, undoCount = 2)
        val restoredEffect = GameSessionEffect.SyncRestoredGame(gameState)
        val debugReportPlan = DebugReportCopyPlan(
            clipboardLabel = "label",
            clipboardReport = "clipboard report",
            fileReport = "file report",
            engineMessage = "message",
            toastMessage = "toast",
        )
        val debugReportEffect = GameSessionEffect.CopyDebugReport(debugReportPlan)

        assertSame(topMovePlan, topMoveEffect.plan)
        assertEquals(true, topMoveEffect.automatic)
        assertSame(autoAiRunPlan, autoAiEffect.plan)
        assertSame(autoAiContext, autoAiEffect.plan.context)
        assertSame(autoAiEndgamePlan, autoAiEndgameEffect.plan)
        assertSame(humanSyncPlan, humanSyncEffect.plan)
        assertSame(gameState, startupEffect.state)
        assertSame(startupProfile, startupEffect.profile)
        assertSame(gameState, newGameEffect.currentState)
        assertSame(newGameProfile, newGameEffect.profile)
        assertEquals(2, undoEffect.undoCount)
        assertSame(gameState, restoredEffect.gameState)
        assertSame(debugReportPlan, debugReportEffect.plan)
    }

    private fun controllerState(
        core: GameSessionCoreState = coreState(),
        settings: GameSessionSettingsState = settingsState(),
        benchmark: EngineBenchmarkUiState = EngineBenchmarkUiState.initial(
            benchmarkText = "No benchmark yet.",
            profile = null,
        ),
        savedSession: SavedSessionUiState = SavedSessionUiState(),
        autoAiTurn: AutoAiTurnUiState = AutoAiTurnUiState(),
        positionCacheOptimization: PositionAnalysisCacheOptimizationUiState =
            PositionAnalysisCacheOptimizationUiState(),
    ): GameSessionControllerState =
        GameSessionControllerState(
            core = core,
            settings = settings,
            benchmark = benchmark,
            savedSession = savedSession,
            autoAiTurn = autoAiTurn,
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
            boardSize = BoardSize.Nine,
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
