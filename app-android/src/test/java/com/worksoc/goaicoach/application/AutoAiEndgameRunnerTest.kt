package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationResult
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheQuality
import com.worksoc.goaicoach.application.autoai.AutoAiEndgameRunRequest
import com.worksoc.goaicoach.application.autoai.AutoAiTurnEndgamePlan
import com.worksoc.goaicoach.application.autoai.runAutoAiEndgameApplication
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.application.engine.AutoAiTurnResult
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProgress
import com.worksoc.goaicoach.application.engine.EngineSessionCapabilities
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.EngineStartupResult
import com.worksoc.goaicoach.application.engine.LocalEngineMoveResult
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.score.EndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.session.GameSessionRuntimeState
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DeadStoneCleanupResult
import com.worksoc.goaicoach.shared.EndgameScoreSource
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAiEndgameRunnerTest {
    @Test
    fun runnerMarksGameEndedResolvesEndgameAndAppliesFinalScore() = runBlocking {
        val state = passPassState()
        val resolution = aiEndgameResolution(state)
        val client = EndgameRunnerFakeEngineClient(endgameResolution = resolution)
        val runtimeLog = EndgameRunnerRuntimeLog()
        var markedGameEnded = false
        var finalDisplay: FinalScoreDisplayPlan? = null

        runAutoAiEndgameApplication(
            baseRequest(
                state = state,
                client = client,
                runtimeLog = runtimeLog,
                markGameEnded = { markedGameEnded = true },
                applyResolvedDisplay = { finalDisplay = it },
            ),
        )

        assertTrue(markedGameEnded)
        assertEquals(state, client.resolvedEndgameState)
        assertEquals("EndgameProfile", client.resolvedEndgameProfile?.name)
        assertTrue(finalDisplay?.scoreText.orEmpty().contains("Final: B+0.5"))
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_endgame_detected") })
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_endgame_success") })
    }

    @Test
    fun runnerAppliesFailureDisplayWhenEngineEndgameFails() = runBlocking {
        val state = passPassState()
        val client = EndgameRunnerFakeEngineClient(
            endgameError = IllegalStateException("final score timeout"),
        )
        val runtimeLog = EndgameRunnerRuntimeLog()
        var failureDisplay: EndgameFailureDisplayPlan? = null

        runAutoAiEndgameApplication(
            baseRequest(
                state = state,
                client = client,
                runtimeLog = runtimeLog,
                applyFailureDisplay = { failureDisplay = it },
            ),
        )

        assertTrue(failureDisplay?.endgameLog.orEmpty().contains("source=auto-ai-engine-final-score-failed"))
        assertTrue(failureDisplay?.engineMessage.orEmpty().contains("Final score failed: final score timeout"))
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_endgame_failure") })
    }

    @Test
    fun runnerDiscardsLateEndgameResultWhenCurrentStateChanged() = runBlocking {
        val state = passPassState()
        val client = EndgameRunnerFakeEngineClient(endgameResolution = aiEndgameResolution(state))
        var finalDisplay: FinalScoreDisplayPlan? = null
        var discarded: EngineOperationResultGuard.Discard? = null

        runAutoAiEndgameApplication(
            baseRequest(
                state = state,
                client = client,
                currentState = GameState.empty()
                    .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine))),
                applyResolvedDisplay = { finalDisplay = it },
                appendEngineOperationDiscardLog = { discarded = it },
            ),
        )

        assertNull(finalDisplay)
        assertTrue(discarded?.reason.orEmpty().contains("result is stale"))
    }

    private fun baseRequest(
        state: GameState,
        client: EndgameRunnerFakeEngineClient,
        currentState: GameState = state,
        runtimeLog: EndgameRunnerRuntimeLog = EndgameRunnerRuntimeLog(),
        markGameEnded: () -> Unit = {},
        applyResolvedDisplay: (FinalScoreDisplayPlan) -> Unit = {},
        applyFailureDisplay: (EndgameFailureDisplayPlan) -> Unit = {},
        appendEngineOperationDiscardLog: (EngineOperationResultGuard.Discard) -> Unit = {},
    ): AutoAiEndgameRunRequest =
        AutoAiEndgameRunRequest(
            endgamePlan = AutoAiTurnEndgamePlan.Resolve(
                state = state,
                profile = EngineProfile(name = "EndgameProfile"),
                prePassCandidates = emptyList(),
                engineMessagePrefix = "pass/pass",
            ),
            engineClient = client,
            previousSnapshotsProvider = { listOf(localScoreSnapshot(state)) },
            currentStateProvider = { currentState },
            currentSessionGenerationProvider = { 7L },
            runtimeContextProvider = { runtimeContext(state) },
            runtimeEventLog = runtimeLog,
            diagnosticEventLog = NoopDiagnosticEventLog,
            markGameEnded = markGameEnded,
            applyResolvedDisplay = applyResolvedDisplay,
            applyFailureDisplay = applyFailureDisplay,
            appendEngineOperationDiscardLog = appendEngineOperationDiscardLog,
            runEngineWork = { block -> block() },
        )

    private fun passPassState(): GameState =
        GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))

    private fun aiEndgameResolution(state: GameState): AiEndgameResolution {
        val finalScore = FinalScoreResult(
            status = EngineStatus.ready("Final score"),
            rawScore = "B+0.5",
            winner = StoneColor.Black,
            margin = 0.5,
            summary = "Final score",
        )
        return AiEndgameResolution(
            cleanup = DeadStoneCleanupResult(state = state, removedStones = emptyList()),
            finalScore = finalScore,
            scoreSource = EndgameScoreSource.CleanedLocalArea,
            localFinalScore = finalScore,
            deadStonesResult = null,
            deadStonesError = null,
            locallyInferredDeadStones = emptyList(),
            engineScoreEstimate = null,
            engineScoreEstimateError = null,
            engineFinalScore = null,
            engineFinalScoreError = null,
            prePassCandidates = emptyList(),
        )
    }

    private fun runtimeContext(state: GameState): RuntimeLogContext =
        RuntimeLogContext(
            engineName = "KataGo",
            engineDiagnostic = "diagnostic",
            playerSetup = PlayerSetup(),
            gameState = state,
            runtimeState = GameSessionRuntimeState(
                playLevel = PlayLevelSetting(),
                engineProfile = EngineProfile(name = "EndgameProfile"),
                analysisPreset = AnalysisPreset.Lite,
            ),
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
            searchTimeSettings = SearchTimeSettings(),
            topMovesEnabled = true,
            isEngineReady = true,
            isEngineBusy = false,
            isGameEnded = true,
            isAutoAiTurnPending = false,
            shouldShowResumePrompt = false,
            analysisCacheStats = "entries=0",
            moveAnalysisCoverage = "coverage",
            scoreText = "score",
        )
}

private class EndgameRunnerFakeEngineClient(
    private val endgameResolution: AiEndgameResolution? = null,
    private val endgameError: Throwable? = null,
) : EngineSessionClient {
    var resolvedEndgameState: GameState? = null
        private set
    var resolvedEndgameProfile: EngineProfile? = null
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
    ): EngineStartupResult =
        error("not used")

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStartupResult =
        error("not used")

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
    ): AiEndgameResolution {
        resolvedEndgameState = state
        resolvedEndgameProfile = profile
        endgameError?.let { throw it }
        return endgameResolution ?: error("not used")
    }

    override suspend fun undoMove(): EngineStatus =
        error("not used")

    override suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile =
        error("not used")
}

private class EndgameRunnerRuntimeLog : RuntimeEventLogPort {
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
