package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.contract.PositionAnalysisCacheOptimizationPlan
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
import com.worksoc.goaicoach.application.contract.GameSessionRuntimeState
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.contract.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.startgame.StartConfiguredGamePlan
import com.worksoc.goaicoach.application.startgame.StartEngineBackedGameRunRequest
import com.worksoc.goaicoach.application.startgame.runStartEngineBackedGameApplication
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.policy.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test
import com.worksoc.goaicoach.testsupport.FakeEngineSessionClient
import com.worksoc.goaicoach.testsupport.RecordingRuntimeEventLog
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog

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
        val runtimeLog = RecordingRuntimeEventLog()
        var appliedRuntime: RuntimePlayLevelSelection? = null
        var launchedOperation: EngineOperationRequest? = null
        var followUpState: GameState? = null

        runStartEngineBackedGameApplication(
            StartEngineBackedGameRunRequest(
                plan = StartConfiguredGamePlan.StartEngineGame(
                    ruleset = Ruleset.Chinese,
                    boardSize = BoardSize.Nine,
                    runtime = runtime,
                ),
                engineClient = client,
                currentState = initialState,
                sessionGeneration = 12L,
                runtimeContextProvider = { runtimeContext(initialState, runtime) },
                runtimeEventLog = runtimeLog,
                diagnosticEventLog = NoopDiagnosticEventLog,
                applyRuntime = { selection -> appliedRuntime = selection },
                launchEngineOperation = { operation, block ->
                    launchedOperation = operation
                    runBlocking { block() }
                },
                resetLocalGame = { message, ruleset, size ->
                    assertEquals("new-game", message)
                    assertEquals(BoardSize.Nine, size)
                    currentState = GameState.empty(size, ruleset)
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
        val runtimeLog = RecordingRuntimeEventLog()
        var resetMessage: String? = null
        var followUpState: GameState? = null

        runStartEngineBackedGameApplication(
            StartEngineBackedGameRunRequest(
                plan = StartConfiguredGamePlan.StartEngineGame(
                    ruleset = Ruleset.Japanese,
                    boardSize = BoardSize.Nine,
                    runtime = runtime,
                ),
                engineClient = client,
                currentState = initialState,
                sessionGeneration = 3L,
                runtimeContextProvider = { runtimeContext(initialState, runtime) },
                runtimeEventLog = runtimeLog,
                diagnosticEventLog = NoopDiagnosticEventLog,
                applyRuntime = {},
                launchEngineOperation = { _, block -> runBlocking { block() } },
                resetLocalGame = { message, ruleset, size ->
                    resetMessage = message
                    assertEquals(BoardSize.Nine, size)
                    currentState = GameState.empty(size, ruleset)
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
) : FakeEngineSessionClient() {
    var newGameProfile: EngineProfile? = null
        private set
    var newGameBoardSize: BoardSize? = null
        private set
    var newGameRuleset: Ruleset? = null
        private set

    /**
     * `scoreSnapshot = null`은 축약이 아니다 — 프로덕션
     * `LocalEngineCoreSessionDelegate.startNewGame`이 초기 점수 추정에 실패하면
     * `runCatching { ... }.getOrNull()`로 정확히 이 값을 돌려준다.
     */
    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
        handicapCount: Int,
        komi: Double,
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
}