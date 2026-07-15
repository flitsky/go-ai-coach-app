package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationResult
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheQuality
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.autoai.AutoAiScheduledTurnRunRequest
import com.worksoc.goaicoach.application.autoai.AutoAiTurnFollowUpPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnFollowUpRequest
import com.worksoc.goaicoach.application.autoai.AutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnScheduleValidationPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnUiState
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnScheduleValidationPlan
import com.worksoc.goaicoach.application.autoai.completeAutoAiTurnRun
import com.worksoc.goaicoach.application.autoai.runScheduledAutoAiTurnApplication
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.application.engine.AutoAiTurnResult
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProgress
import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.engine.EngineSessionCapabilities
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.EngineStartupResult
import com.worksoc.goaicoach.application.engine.LocalEngineMoveResult
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionCoreState
import com.worksoc.goaicoach.application.session.GameSessionMoveReviewState
import com.worksoc.goaicoach.application.session.GameSessionRuntimeState
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
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
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAiScheduledTurnRunnerTest {
    @Test
    fun runnerExecutesScheduledTurnAndRequestsFollowUpAnalysis() {
        val before = GameState.empty()
        val after = before.play(
            Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
        )
        val playLevel = PlayLevelSetting()
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai, playLevel = playLevel),
            white = SidePlayerSetup(controller = SeatController.Human),
        )
        var currentState = before
        var autoAiState = AutoAiTurnUiState()
        val runtimeState = GameSessionRuntimeState(
            playLevel = playLevel,
            engineProfile = EngineProfile(name = "runner"),
            analysisPreset = AnalysisPreset.Lite,
            sessionGeneration = 4L,
        )
        val client = ScheduledRunnerFakeEngineClient(
            result = AutoAiTurnResult(
                turnOutcome = TurnOutcome(
                    gameState = after,
                    engineMessage = "AI played E5",
                    candidateText = "candidate",
                    lastMoveText = "Black E5",
                ),
                scoreEstimate = null,
                profile = runtimeState.engineProfile,
                playLevel = playLevel,
            ),
        )
        val runtimeLog = ScheduledRunnerRuntimeEventLog()
        val startedIds = mutableListOf<String>()
        val completedIds = mutableListOf<String>()
        var followUp: AutoAiTurnFollowUpRequest? = null
        var turnTimeUpdate: TurnTimeMoveUpdate? = null

        runScheduledAutoAiTurnApplication(
            baseRequest(
                schedule = AutoAiTurnRequestPlan.Schedule(delayMillis = 0L),
                stateProvider = { currentState },
                controllerStateProvider = {
                    controllerState(
                        state = currentState,
                        setup = setup,
                        runtimeState = runtimeState,
                        autoAiTurnUiState = autoAiState,
                    )
                },
                client = client,
                runtimeState = runtimeState,
                runtimeLog = runtimeLog,
                applyScheduled = { schedule ->
                    autoAiState = autoAiState.applyAutoAiTurnRequestPlan(schedule)
                },
                recordTurnMove = { player, nowMillis, nextPlayer ->
                    assertEquals(StoneColor.Black, player)
                    GameSessionTurnTimeState.reset(before, 900L)
                        .recordMove(player, nowMillis, nextPlayer)
                },
                applyTurnTimeUpdate = { update ->
                    turnTimeUpdate = update
                },
                applyTurnDisplay = { display ->
                    currentState = display.gameState
                    AutoAiTurnFollowUpPlan.RequestTopMoveAnalysis(display.gameState)
                },
                markStarted = { id -> startedIds += id },
                markCompleted = { id -> completedIds += id },
                completeRun = { autoAiState = autoAiState.completeAutoAiTurnRun() },
                requestFollowUp = { request -> followUp = request },
            ),
        )

        assertEquals(before, client.currentState)
        assertEquals(playLevel, client.playLevel)
        assertEquals(runtimeState.engineProfile, client.currentProfile)
        assertEquals(SearchTimeSettings(), client.searchTimeSettings)
        assertEquals(after, currentState)
        assertEquals(StoneColor.Black, turnTimeUpdate?.player)
        assertEquals(false, autoAiState.isPending)
        assertEquals(1, startedIds.size)
        assertEquals(startedIds, completedIds)
        assertEquals(after, followUp?.targetState)
        assertEquals(true, followUp?.automatic)
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_schedule") })
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_begin") })
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_complete") })
    }

    @Test
    fun runnerCancelsScheduledTurnWhenValidationNoLongerAllowsAiMove() {
        val state = GameState.empty()
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Human),
        )
        var autoAiState = AutoAiTurnUiState()
        var cancelled = false
        var launchedEngine = false
        val runtimeLog = ScheduledRunnerRuntimeEventLog()
        val runtimeState = GameSessionRuntimeState(
            playLevel = PlayLevelSetting(),
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            sessionGeneration = 1L,
        )

        runScheduledAutoAiTurnApplication(
            baseRequest(
                schedule = AutoAiTurnRequestPlan.Schedule(delayMillis = 10L),
                stateProvider = { state },
                controllerStateProvider = {
                    controllerState(
                        state = state,
                        setup = setup,
                        runtimeState = runtimeState,
                        autoAiTurnUiState = autoAiState,
                    )
                },
                client = ScheduledRunnerFakeEngineClient(
                    result = AutoAiTurnResult(
                        turnOutcome = TurnOutcome(state, "unused", "unused", "unused"),
                        scoreEstimate = null,
                        profile = EngineProfile(),
                        playLevel = PlayLevelSetting(),
                    ),
                ),
                runtimeState = runtimeState,
                runtimeLog = runtimeLog,
                isEngineReady = { false },
                delayMillis = { millis -> assertEquals(10L, millis) },
                applyScheduled = { schedule ->
                    autoAiState = autoAiState.applyAutoAiTurnRequestPlan(schedule)
                },
                applyCancelled = { cancel ->
                    assertEquals(AutoAiTurnScheduleValidationPlan.Cancel, cancel)
                    cancelled = true
                    autoAiState = autoAiState.applyAutoAiTurnScheduleValidationPlan(cancel)
                },
                markStarted = { launchedEngine = true },
            ),
        )

        assertEquals(true, cancelled)
        assertEquals(false, launchedEngine)
        assertEquals(false, autoAiState.isPending)
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_schedule_cancelled") })
    }

    private fun baseRequest(
        schedule: AutoAiTurnRequestPlan.Schedule,
        stateProvider: () -> GameState,
        controllerStateProvider: () -> GameSessionControllerState,
        client: EngineSessionClient,
        runtimeState: GameSessionRuntimeState,
        runtimeLog: ScheduledRunnerRuntimeEventLog,
        isEngineReady: () -> Boolean = { true },
        delayMillis: suspend (Long) -> Unit = {},
        applyScheduled: (AutoAiTurnRequestPlan.Schedule) -> Unit = {},
        applyCancelled: (AutoAiTurnScheduleValidationPlan) -> Unit = {},
        markStarted: (String) -> Unit = {},
        markCompleted: (String) -> Unit = {},
        recordTurnMove: (
            StoneColor,
            Long,
            StoneColor,
        ) -> TurnTimeMoveUpdate = { player, nowMillis, nextPlayer ->
            GameSessionTurnTimeState.reset(stateProvider(), nowMillis)
                .recordMove(player, nowMillis, nextPlayer)
        },
        applyTurnTimeUpdate: (TurnTimeMoveUpdate) -> Unit = {},
        applyTurnDisplay: (com.worksoc.goaicoach.application.autoai.AutoAiTurnDisplayPlan) -> AutoAiTurnFollowUpPlan =
            { AutoAiTurnFollowUpPlan.None },
        resolveEndgame: suspend (com.worksoc.goaicoach.application.autoai.AutoAiTurnEndgamePlan.Resolve) -> Unit = {},
        applyTurnFailureDisplay: (Throwable) -> Unit = {},
        appendEngineOperationDiscardLog: (
            com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard.Discard,
        ) -> Unit = {},
        completeRun: () -> Unit = {},
        requestFollowUp: (AutoAiTurnFollowUpRequest) -> Unit = {},
    ): AutoAiScheduledTurnRunRequest =
        AutoAiScheduledTurnRunRequest(
            schedule = schedule,
            controllerStateProvider = controllerStateProvider,
            engineClient = client,
            runtimeStateProvider = { runtimeState },
            searchTimeSettingsProvider = { SearchTimeSettings() },
            scoreSnapshotsProvider = { listOf(localScoreSnapshot(stateProvider())) },
            isEngineReady = isEngineReady,
            isEngineBusy = { false },
            isGameEnded = { false },
            shouldShowResumePrompt = { false },
            runtimeContextProvider = {
                runtimeContext(
                    state = stateProvider(),
                    setup = controllerStateProvider().playerSetup,
                    runtimeState = runtimeState,
                )
            },
            runtimeEventLog = runtimeLog,
            diagnosticEventLog = ScheduledRunnerNoopDiagnosticLog,
            delayMillis = delayMillis,
            launchAutoAiEffect = { block -> runBlocking { block() } },
            applyScheduled = applyScheduled,
            applyCancelled = applyCancelled,
            markEngineOperationStarted = markStarted,
            markEngineOperationCompleted = markCompleted,
            recordTurnMove = recordTurnMove,
            applyTurnTimeUpdate = applyTurnTimeUpdate,
            applyTurnDisplay = applyTurnDisplay,
            resolveEndgame = resolveEndgame,
            applyTurnFailureDisplay = applyTurnFailureDisplay,
            appendEngineOperationDiscardLog = appendEngineOperationDiscardLog,
            completeAutoAiTurnRun = completeRun,
            requestFollowUpAnalysis = requestFollowUp,
            currentStateProvider = stateProvider,
            currentSessionGenerationProvider = { runtimeState.sessionGeneration },
            nowMillis = { 1_000L },
        )

    private fun controllerState(
        state: GameState,
        setup: PlayerSetup,
        runtimeState: GameSessionRuntimeState,
        autoAiTurnUiState: AutoAiTurnUiState,
    ): GameSessionControllerState =
        GameSessionControllerState(
            core = GameSessionCoreState(
                gameState = state,
                isGameEnded = false,
                analysisState = GameSessionAnalysisState.empty(state, candidateText = "candidate"),
                scoreState = GameSessionScoreState.reset(
                    scoreText = "score",
                    scoreSnapshots = listOf(localScoreSnapshot(state)),
                    endgameLog = "endgame",
                ),
                runtimeState = runtimeState,
                moveReviewState = GameSessionMoveReviewState.reset(
                    moveReviewText = "review",
                    lastMoveText = "None",
                ),
                engineMessage = "engine",
            ),
            settings = GameSessionSettingsState(
                playerSetup = setup,
                autoPlayDelaySetting = AutoPlayDelaySetting.None,
                searchTimeSettings = SearchTimeSettings(),
                topMovesEnabled = true,
                boardSize = BoardSize.Nine,
            ),
            benchmark = EngineBenchmarkUiState.initial(
                benchmarkText = "benchmark",
                profile = null,
            ),
            savedSession = SavedSessionUiState(hasCheckedSavedSession = true),
            autoAiTurn = autoAiTurnUiState,
            positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
        )

    private fun runtimeContext(
        state: GameState,
        setup: PlayerSetup,
        runtimeState: GameSessionRuntimeState,
    ): RuntimeLogContext =
        RuntimeLogContext(
            engineName = "KataGo",
            engineDiagnostic = "diagnostic",
            playerSetup = setup,
            gameState = state,
            runtimeState = runtimeState,
            autoPlayDelaySetting = AutoPlayDelaySetting.None,
            searchTimeSettings = SearchTimeSettings(),
            topMovesEnabled = true,
            isEngineReady = true,
            isEngineBusy = false,
            isGameEnded = false,
            isAutoAiTurnPending = false,
            shouldShowResumePrompt = false,
            analysisCacheStats = "entries=0",
            moveAnalysisCoverage = "coverage",
            scoreText = "score",
        )
}

private class ScheduledRunnerFakeEngineClient(
    private val result: AutoAiTurnResult,
) : EngineSessionClient {
    var currentState: GameState? = null
        private set
    var playLevel: PlayLevelSetting? = null
        private set
    var currentProfile: EngineProfile? = null
        private set
    var searchTimeSettings: SearchTimeSettings? = null
        private set

    override val capabilities: EngineSessionCapabilities =
        EngineSessionCapabilities(supportsDeviceBenchmark = false)

    override fun positionAnalysisCacheStatsText(nowMillis: Long): String = "disabled"

    override fun positionAnalysisCacheQualityFor(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
        nowMillis: Long,
    ): PositionAnalysisCacheQuality? = null

    override suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult = error("not used")

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
        handicapCount: Int,
    ): EngineStartupResult = error("not used")

    override suspend fun analyzePosition(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
    ): AnalysisResult = error("not used")

    override suspend fun optimizePositionAnalysisCache(
        plan: PositionAnalysisCacheOptimizationPlan,
    ): PositionAnalysisCacheOptimizationResult = error("not used")

    override suspend fun syncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate = error("not used")

    override suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate = error("not used")

    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult {
        this.currentState = currentState
        this.playLevel = playLevel
        this.currentProfile = currentProfile
        this.searchTimeSettings = searchTimeSettings
        return result
    }

    override suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult = error("not used")

    override suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate = error("not used")

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

private class ScheduledRunnerRuntimeEventLog : RuntimeEventLogPort {
    val events = mutableListOf<String>()

    override fun append(
        event: String,
        nowMillis: Long,
    ) {
        events += event
    }

    override fun readText(): String = events.joinToString("\n")

    override fun clear() {
        events.clear()
    }
}

private object ScheduledRunnerNoopDiagnosticLog : DiagnosticEventLogPort {
    override fun append(
        event: DiagnosticEvent,
        nowMillis: Long,
    ) = Unit

    override fun readText(): String = ""

    override fun clear() = Unit
}
