package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisLimit
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EngineSessionLifecycleApplicationTest {
    @Test
    fun startupRunnerDelegatesToEngineSessionClient() = runBlocking {
        val client = RecordingLifecycleEngineSessionClient()
        val state = GameState.empty()
        val profile = EngineProfile(name = "Startup")
        val request = engineOperationRequest(
            kind = EngineOperationKind.EngineStartup,
            state = state,
            sessionGeneration = 4,
        )

        val result = client.runEngineStartupEffect(
            effect = GameSessionEffect.StartEngineSession(
                state = state,
                profile = profile,
            ),
            operationRequest = request,
            diagnosticEventLog = NoopDiagnosticEventLog,
        )

        assertEquals("startup", result.message)
        assertSame(profile, client.startedProfile)
        assertSame(state, client.startedState)
    }

    @Test
    fun newGameRunnerDelegatesToEngineSessionClient() = runBlocking {
        val client = RecordingLifecycleEngineSessionClient()
        val currentState = GameState.empty()
        val profile = EngineProfile(name = "NewGame")

        val result = client.runEngineBackedNewGameEffect(
            effect = GameSessionEffect.StartEngineBackedGame(
                currentState = currentState,
                profile = profile,
                boardSize = BoardSize.Nine,
                ruleset = Ruleset.Chinese,
            ),
        )

        assertEquals("new-game", result.message)
        assertSame(profile, client.newGameProfile)
        assertEquals(BoardSize.Nine, client.newGameBoardSize)
        assertEquals(Ruleset.Chinese, client.newGameRuleset)
    }

    @Test
    fun undoRunnerRepeatsRequestedUndoCountAndReturnsLastStatus() = runBlocking {
        val client = RecordingLifecycleEngineSessionClient()

        val status = client.runEngineUndoEffect(
            effect = GameSessionEffect.UndoEngineMoves(
                state = GameState.empty(),
                undoCount = 3,
            ),
        )

        assertEquals(3, client.undoCalls)
        assertEquals("undo-3", status.message)
    }

    @Test
    fun engineOperationScopeCompletesLifecycleOnFailure() = runBlocking {
        val request = engineOperationRequest(
            kind = EngineOperationKind.EngineUndo,
            state = GameState.empty(),
            sessionGeneration = 1,
        )
        val events = mutableListOf<String>()
        val scope = EngineOperationScope(
            request = request,
            callbacks = EngineOperationLifecycleCallbacks(
                onStarted = { events += "started:${it.operationId}" },
                onCompleted = { events += "completed:${it.operationId}" },
            ),
        )

        runCatching {
            scope.run<Unit> {
                error("boom")
            }
        }

        assertEquals(
            listOf(
                "started:${request.operationId}",
                "completed:${request.operationId}",
            ),
            events,
        )
    }
}

private class RecordingLifecycleEngineSessionClient : EngineSessionClient {
    override val capabilities: EngineSessionCapabilities = EngineSessionCapabilities(
        supportsDeviceBenchmark = true,
    )
    var startedProfile: EngineProfile? = null
    var startedState: GameState? = null
    var newGameProfile: EngineProfile? = null
    var newGameBoardSize: BoardSize? = null
    var newGameRuleset: Ruleset? = null
    var undoCalls: Int = 0

    override fun positionAnalysisCacheStatsText(nowMillis: Long): String =
        "entries=0"

    override fun positionAnalysisCacheQualityFor(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
        nowMillis: Long,
    ): PositionAnalysisCacheQuality? = null

    override suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult {
        startedProfile = profile
        startedState = state
        return EngineStartupResult(
            message = "startup",
            scoreSnapshot = localScoreSnapshot(state),
        )
    }

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStartupResult {
        newGameProfile = profile
        newGameBoardSize = boardSize
        newGameRuleset = ruleset
        return EngineStartupResult(
            message = "new-game",
            scoreSnapshot = localScoreSnapshot(GameState.empty(boardSize = boardSize, ruleset = ruleset)),
        )
    }

    override suspend fun analyzePosition(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
    ): AnalysisResult =
        AnalysisResult(
            status = EngineStatus.ready("unused"),
            candidates = emptyList(),
            summary = "unused",
        )

    override suspend fun optimizePositionAnalysisCache(
        plan: PositionAnalysisCacheOptimizationPlan,
    ): PositionAnalysisCacheOptimizationResult =
        error("unused")

    override suspend fun syncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        error("unused")

    override suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        error("unused")

    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult =
        error("unused")

    override suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult =
        error("unused")

    override suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate =
        error("unused")

    override suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution =
        error("unused")

    override suspend fun undoMove(): EngineStatus {
        undoCalls += 1
        return EngineStatus.ready("undo-$undoCalls")
    }

    override suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile =
        error("unused")
}
