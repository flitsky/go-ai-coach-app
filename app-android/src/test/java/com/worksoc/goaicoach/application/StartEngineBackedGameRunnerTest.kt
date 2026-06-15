package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationResult
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheQuality
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.application.engine.AutoAiTurnResult
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProgress
import com.worksoc.goaicoach.application.engine.EngineSessionBackend
import com.worksoc.goaicoach.application.engine.EngineSessionCapabilities
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.EngineStartupResult
import com.worksoc.goaicoach.application.engine.LocalEngineMoveResult
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.session.GameSessionRuntimeState
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.startgame.StartConfiguredGamePlan
import com.worksoc.goaicoach.application.startgame.StartEngineBackedGameRunRequest
import com.worksoc.goaicoach.application.startgame.runStartEngineBackedGameApplication
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.AnalysisResult
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
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartEngineBackedGameRunnerTest {
    @Test
    fun runnerStartsEngineGameResetsScoreAndRequestsFollowUpAnalysis() {
        val initialState = GameState.empty()
        var currentState = initialState
        var scoreState = GameSessionScoreState.reset(
            scoreText = "old score",
            scoreSnapshots = emptyList(),
            endgameLog = "old endgame",
        )
        val runtime = runtimeSelection()
        val client = RunnerFakeStartGameEngineClient()
        val runtimeLog = RunnerRecordingRuntimeEventLog()
        var appliedRuntime: RuntimePlayLevelSelection? = null
        var launchedOperation: EngineOperationRequest? = null
        var followUpState: GameState? = null

        runStartEngineBackedGameApplication(
            StartEngineBackedGameRunRequest(
                plan = StartConfiguredGamePlan.StartEngineGame(
                    ruleset = Ruleset.Chinese,
                    runtime = runtime,
                ),
                engineClient = client,
                currentState = initialState,
                sessionGeneration = 12L,
                runtimeContextProvider = { runtimeContext(initialState, runtime) },
                runtimeEventLog = runtimeLog,
                diagnosticEventLog = RunnerNoopDiagnosticEventLog,
                applyRuntime = { selection -> appliedRuntime = selection },
                launchEngineOperation = { operation, block ->
                    launchedOperation = operation
                    runBlocking { block() }
                },
                resetLocalGame = { message, ruleset ->
                    assertEquals("new-game", message)
                    currentState = GameState.empty(BoardSize.Nine, ruleset)
                    scoreState = GameSessionScoreState.reset(
                        scoreText = "reset score",
                        scoreSnapshots = listOf(localScoreSnapshot(currentState)),
                        endgameLog = "reset endgame",
                    )
                },
                currentScoreStateProvider = { scoreState },
                replaceScoreState = { state -> scoreState = state },
                currentStateProvider = { currentState },
                requestFollowUpAnalysis = { state -> followUpState = state },
                nowMillis = fixedNowMillis(100L, 130L),
            ),
        )

        assertEquals(runtime, appliedRuntime)
        assertEquals(EngineOperationKind.EngineNewGame, launchedOperation?.kind)
        assertEquals(12L, launchedOperation?.sessionGeneration)
        assertEquals(EngineFallbackPolicy.LocalEngine, launchedOperation?.fallbackPolicy)
        assertEquals(runtime.engineProfile, client.newGameProfile)
        assertEquals(BoardSize.Nine, client.newGameBoardSize)
        assertEquals(Ruleset.Chinese, client.newGameRuleset)
        assertEquals(Ruleset.Chinese, currentState.ruleset)
        assertEquals(currentState, followUpState)
        assertEquals("reset score", scoreState.scoreText)
        assertEquals(1, scoreState.scoreSnapshots.size)
        assertTrue(runtimeLog.events.any { it.contains("event=engine_game_start_request") })
        assertTrue(runtimeLog.events.any { it.contains("event=engine_game_start_success") })
    }

    @Test
    fun runnerResetsWithFailureMessageAndStillRequestsFollowUpAnalysis() {
        val initialState = GameState.empty()
        var currentState = initialState
        var scoreState = GameSessionScoreState.reset(
            scoreText = "old score",
            scoreSnapshots = emptyList(),
            endgameLog = "old endgame",
        )
        val runtime = runtimeSelection()
        val client = RunnerFakeStartGameEngineClient(
            newGameError = IllegalStateException("engine failed"),
        )
        val runtimeLog = RunnerRecordingRuntimeEventLog()
        var resetMessage: String? = null
        var followUpState: GameState? = null

        runStartEngineBackedGameApplication(
            StartEngineBackedGameRunRequest(
                plan = StartConfiguredGamePlan.StartEngineGame(
                    ruleset = Ruleset.Japanese,
                    runtime = runtime,
                ),
                engineClient = client,
                currentState = initialState,
                sessionGeneration = 3L,
                runtimeContextProvider = { runtimeContext(initialState, runtime) },
                runtimeEventLog = runtimeLog,
                diagnosticEventLog = RunnerNoopDiagnosticEventLog,
                applyRuntime = {},
                launchEngineOperation = { _, block -> runBlocking { block() } },
                resetLocalGame = { message, ruleset ->
                    resetMessage = message
                    currentState = GameState.empty(BoardSize.Nine, ruleset)
                    scoreState = GameSessionScoreState.reset(
                        scoreText = "reset failure score",
                        scoreSnapshots = listOf(localScoreSnapshot(currentState)),
                        endgameLog = "reset failure endgame",
                    )
                },
                currentScoreStateProvider = { scoreState },
                replaceScoreState = { state -> scoreState = state },
                currentStateProvider = { currentState },
                requestFollowUpAnalysis = { state -> followUpState = state },
                nowMillis = fixedNowMillis(200L, 260L),
            ),
        )

        assertEquals("engine failed", resetMessage)
        assertEquals(currentState, followUpState)
        assertNotNull(scoreState.scoreSnapshots.singleOrNull())
        assertTrue(runtimeLog.events.any { it.contains("event=engine_game_start_failure") })
    }

    private fun runtimeSelection(): RuntimePlayLevelSelection =
        RuntimePlayLevelSelection(
            playLevel = PlayLevelSetting(),
            engineProfile = EngineProfile(name = "runner-test"),
            analysisPreset = AnalysisPreset.Lite,
            searchTimeSettings = SearchTimeSettings(),
        )

    private fun runtimeContext(
        state: GameState,
        runtime: RuntimePlayLevelSelection,
    ): RuntimeLogContext =
        RuntimeLogContext(
            engineName = "KataGo",
            engineDiagnostic = "diagnostic ok",
            playerSetup = PlayerSetup(),
            gameState = state,
            runtimeState = GameSessionRuntimeState(
                playLevel = runtime.playLevel,
                engineProfile = runtime.engineProfile,
                analysisPreset = runtime.analysisPreset,
            ),
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
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

    private fun fixedNowMillis(vararg values: Long): () -> Long {
        var index = 0
        return {
            val value = values.getOrElse(index) { values.last() }
            index += 1
            value
        }
    }
}

private class RunnerFakeStartGameEngineClient(
    private val newGameError: Throwable? = null,
) : EngineSessionClient {
    var newGameProfile: EngineProfile? = null
        private set
    var newGameBoardSize: BoardSize? = null
        private set
    var newGameRuleset: Ruleset? = null
        private set

    override val capabilities: EngineSessionCapabilities =
        EngineSessionCapabilities(
            supportsDeviceBenchmark = false,
            backend = EngineSessionBackend.LocalEngine,
        )

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
    ): EngineStartupResult =
        error("not used")

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStartupResult {
        newGameProfile = profile
        newGameBoardSize = boardSize
        newGameRuleset = ruleset
        newGameError?.let { throw it }
        return EngineStartupResult(
            message = "new-game",
            scoreSnapshot = null,
        )
    }

    override suspend fun analyzePosition(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
    ): AnalysisResult =
        error("not used")

    override suspend fun optimizePositionAnalysisCache(
        plan: PositionAnalysisCacheOptimizationPlan,
    ): PositionAnalysisCacheOptimizationResult =
        error("not used")

    override suspend fun syncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        error("not used")

    override suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        error("not used")

    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult =
        error("not used")

    override suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult =
        error("not used")

    override suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate =
        error("not used")

    override suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution =
        error("not used")

    override suspend fun undoMove(): EngineStatus =
        error("not used")

    override suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile =
        error("not used")
}

private class RunnerRecordingRuntimeEventLog : RuntimeEventLogPort {
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

private object RunnerNoopDiagnosticEventLog : DiagnosticEventLogPort {
    override fun append(
        event: DiagnosticEvent,
        nowMillis: Long,
    ) = Unit

    override fun readText(): String = ""

    override fun clear() = Unit
}
