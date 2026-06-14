package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.AnalysisLimit
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
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreDisplayApplicationTest {
    @Test
    fun scoreEstimateRequestPlanBlocksWhileEngineIsBusy() {
        val plan = buildScoreEstimateRequestPlan(
            state = GameState.empty(),
            previousSnapshots = emptyList(),
            isEngineReady = true,
            isEngineBusy = true,
            matchMode = MatchMode.HumanVsAi,
            engineProfile = EngineProfile(),
        )

        assertEquals(
            ScoreEstimateRequestPlan.ShowMessage("Engine is busy. Estimate after the current response."),
            plan,
        )
    }

    @Test
    fun scoreEstimateRequestPlanUsesLocalEstimateForOfflineTwoPlayerMode() {
        val plan = buildScoreEstimateRequestPlan(
            state = GameState.empty(),
            previousSnapshots = emptyList(),
            isEngineReady = false,
            isEngineBusy = false,
            matchMode = MatchMode.LocalTwoPlayer,
            engineProfile = EngineProfile(),
        )

        assertTrue(plan is ScoreEstimateRequestPlan.ShowLocalEstimate)
        val local = plan as ScoreEstimateRequestPlan.ShowLocalEstimate
        assertNull(local.display.scoreEstimate)
        assertTrue(local.display.engineMessage.contains("Local"))
    }

    @Test
    fun scoreEstimateRequestPlanReportsEngineNotReadyForAiModes() {
        val plan = buildScoreEstimateRequestPlan(
            state = GameState.empty(),
            previousSnapshots = emptyList(),
            isEngineReady = false,
            isEngineBusy = false,
            matchMode = MatchMode.HumanVsAi,
            engineProfile = EngineProfile(),
        )

        assertEquals(ScoreEstimateRequestPlan.ShowMessage("Engine is not ready."), plan)
    }

    @Test
    fun scoreEstimateRequestPlanRequestsEngineAndSyncsTwoPlayerModeWhenReady() {
        val state = GameState.empty()
        val profile = EngineProfile()
        val plan = buildScoreEstimateRequestPlan(
            state = state,
            previousSnapshots = emptyList(),
            isEngineReady = true,
            isEngineBusy = false,
            matchMode = MatchMode.LocalTwoPlayer,
            engineProfile = profile,
        )

        assertTrue(plan is ScoreEstimateRequestPlan.RequestEngineEstimate)
        val request = plan as ScoreEstimateRequestPlan.RequestEngineEstimate
        assertEquals(state, request.state)
        assertEquals(profile, request.profile)
        assertTrue(request.syncFirst)
    }

    @Test
    fun scoreEstimateResultGuardRejectsChangedPosition() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val changedState = state
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))
        val request = ScoreEstimateRequestPlan.RequestEngineEstimate(
            state = state,
            profile = EngineProfile(),
            syncFirst = false,
        )
        val token = scoreEstimateOperationToken(request)

        assertEquals(
            EngineOperationResultGuard.Apply,
            evaluateScoreEstimateResultGuard(token, state),
        )
        assertTrue(evaluateScoreEstimateResultGuard(token, changedState) is EngineOperationResultGuard.Discard)
    }

    @Test
    fun scoreEstimateFailureDisplayPlanUsesErrorMessageOrDefault() {
        val withMessage = buildScoreEstimateFailureDisplayPlan(IllegalStateException("engine stalled"))
        val withoutMessage = buildScoreEstimateFailureDisplayPlan(Throwable())

        assertEquals("engine stalled", withMessage.engineMessage)
        assertEquals("Score estimate failed.", withoutMessage.engineMessage)
    }

    @Test
    fun engineEstimateDisplayPlanRecordsScoreSnapshotAndMessage() {
        val state = GameState.empty()
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = -3.5,
            whiteWinRate = 0.25,
            summary = "KataGo estimate",
        )

        val plan = buildEngineEstimateDisplayPlan(
            state = state,
            estimate = estimate,
            previousSnapshots = emptyList(),
        )

        assertEquals(estimate, plan.scoreEstimate)
        assertEquals("estimated", plan.engineMessage)
        assertTrue(plan.scoreText.contains("Black win: 75%"))
        assertEquals(0, plan.scoreSnapshots.single().moveNumber)
        assertEquals(ScoreSnapshotSource.EngineEstimate, plan.scoreSnapshots.single().source)
    }

    @Test
    fun scoreEstimateDisplayRunnerRequestsEngineAndBuildsPlan() = runBlocking {
        val state = GameState.empty()
        val profile = EngineProfile()
        val client = FakeScoreEngineSessionClient()
        val request = ScoreEstimateRequestPlan.RequestEngineEstimate(
            state = state,
            profile = profile,
            syncFirst = true,
        )

        val plan = client.runScoreEstimateDisplayPlan(
            request = request,
            previousSnapshots = emptyList(),
        )

        assertEquals(state, client.estimatedState)
        assertEquals(profile, client.estimatedProfile)
        assertEquals(true, client.estimatedSyncFirst)
        assertEquals("estimated", plan.engineMessage)
        assertEquals(ScoreSnapshotSource.EngineEstimate, plan.scoreSnapshots.single().source)
    }

    @Test
    fun scoringRuleSyncRunnerBuildsTrimmedEngineEstimatePlan() = runBlocking {
        val state = GameState.empty()
        val client = FakeScoreEngineSessionClient()
        val previous = listOf(
            ScoreSnapshot(moveNumber = 5, source = ScoreSnapshotSource.EngineEstimate),
        )

        val plan = client.runScoringRuleSyncDisplayPlan(
            state = state,
            profile = EngineProfile(),
            previousSnapshots = previous,
            engineMessage = "rules synced",
        )

        assertEquals(state, client.syncedState)
        assertEquals("rules synced", plan.engineMessage)
        assertEquals(0, plan.scoreSnapshots.single().moveNumber)
    }

    @Test
    fun restoredGameSyncRunnerUsesRestoreMessageAndFreshTimeline() = runBlocking {
        val state = GameState.empty()
        val client = FakeScoreEngineSessionClient()

        val plan = client.runRestoredGameSyncDisplayPlan(
            state = state,
            profile = EngineProfile(),
        )

        assertEquals(state, client.configuredSyncState)
        assertEquals("Previous game restored and engine state synchronized.", plan.engineMessage)
        assertEquals(1, plan.scoreSnapshots.size)
    }

    @Test
    fun localScoreEstimateDisplayPlanClearsEngineEstimateAndRecordsLocalSnapshot() {
        val state = GameState.empty()

        val plan = buildLocalScoreEstimateDisplayPlan(
            state = state,
            previousSnapshots = listOf(ScoreSnapshot(moveNumber = 0, source = ScoreSnapshotSource.EngineEstimate)),
            engineMessage = "local refreshed",
        )

        assertNull(plan.scoreEstimate)
        assertEquals("local refreshed", plan.engineMessage)
        assertTrue(plan.scoreText.contains("Final:"))
        assertEquals(ScoreSnapshotSource.LocalAreaEstimate, plan.scoreSnapshots.single().source)
    }

    @Test
    fun resolvedEndgameDisplayPlanBuildsFinalScoreLogAndCandidateText() {
        val state = GameState.empty()
        val finalScore = FinalScoreResult(
            status = EngineStatus.ready("final complete"),
            rawScore = "B+0.5",
            winner = StoneColor.Black,
            margin = 0.5,
            summary = "Final score",
        )
        val resolution = AiEndgameResolution(
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

        val plan = buildResolvedEndgameDisplayPlan(
            source = "test-endgame",
            originalState = state,
            resolution = resolution,
            previousSnapshots = emptyList(),
            engineMessagePrefix = "prefix",
        )

        assertEquals(state, plan.gameState)
        assertNull(plan.scoreEstimate)
        assertTrue(plan.scoreText.contains("Final: B+0.5"))
        assertTrue(plan.endgameLog.contains("source=test-endgame"))
        assertTrue(plan.engineMessage.startsWith("prefix\n"))
        assertTrue(plan.candidateText.contains("Game ended after pass/pass"))
        assertEquals(ScoreSnapshotSource.FinalScore, plan.scoreSnapshots.single().source)
    }
}

private class FakeScoreEngineSessionClient : EngineSessionClient {
    var estimatedState: GameState? = null
        private set
    var estimatedProfile: EngineProfile? = null
        private set
    var estimatedSyncFirst: Boolean? = null
        private set
    var syncedState: GameState? = null
        private set
    var configuredSyncState: GameState? = null
        private set

    override val capabilities: EngineSessionCapabilities =
        EngineSessionCapabilities(supportsDeviceBenchmark = false)

    override fun positionAnalysisCacheStatsText(nowMillis: Long): String =
        "disabled"

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
    ): ScoreEstimate {
        syncedState = state
        return testEstimate()
    }

    override suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate {
        configuredSyncState = state
        return testEstimate()
    }

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
    ): ScoreEstimate {
        estimatedState = state
        estimatedProfile = profile
        estimatedSyncFirst = syncFirst
        return testEstimate()
    }

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

    private fun testEstimate(): ScoreEstimate =
        ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = 0.5,
            whiteWinRate = 0.5,
            summary = "estimate",
        )
}
