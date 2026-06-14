package com.worksoc.goaicoach.application

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
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanMoveApplicationTest {
    @Test
    fun applyHumanMoveLocallyReturnsAfterMoveReviewAndCapturedSummary() {
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val beforeMove = GameState.empty()
        val reviewAnalysis = MoveAnalysisSnapshot.from(
            state = beforeMove,
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, coordinate),
                    pointLoss = 0.0,
                ),
            ),
        )

        val result = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = Move.Play(StoneColor.Black, coordinate),
            reviewAnalysis = reviewAnalysis,
            previousMoveReviews = emptyList(),
        ).getOrThrow()

        assertEquals(StoneColor.White, result.afterMove.nextPlayer)
        assertEquals("Black E5", result.lastMoveText)
        assertEquals("Captured: Black 0 / White 0", result.capturedText)
        assertEquals(1, result.localScoreSnapshot.moveNumber)
        assertEquals(coordinate, result.moveReviews.single().coordinate)
        assertTrue(result.moveReview.text.contains("E5 excellent"))
        assertNull(result.localFinalScore)
    }

    @Test
    fun applyHumanMoveLocallyProducesLocalFinalScoreAfterConsecutivePasses() {
        val beforeMove = GameState.empty()
            .play(Move.Pass(StoneColor.Black))

        val result = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = Move.Pass(StoneColor.White),
            reviewAnalysis = MoveAnalysisSnapshot.empty(beforeMove),
            previousMoveReviews = emptyList(),
        ).getOrThrow()

        assertTrue(result.afterMove.hasConsecutivePasses())
        assertNotNull(result.localFinalScore)
        assertEquals("Move review: pass/resign has no board spot evaluation.", result.moveReview.text)
    }

    @Test
    fun applyHumanMoveLocallyFailsForIllegalMove() {
        val occupied = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val beforeMove = GameState.empty()
            .play(Move.Play(StoneColor.Black, occupied))

        val result = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = Move.Play(StoneColor.White, occupied),
            reviewAnalysis = MoveAnalysisSnapshot.empty(beforeMove),
            previousMoveReviews = emptyList(),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun humanEngineSyncSuccessPlanBuildsScoreEstimateUpdate() {
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val beforeMove = GameState.empty()
        val move = Move.Play(StoneColor.Black, coordinate)
        val localMove = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = move,
            reviewAnalysis = MoveAnalysisSnapshot.empty(beforeMove),
            previousMoveReviews = emptyList(),
        ).getOrThrow()
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("synced"),
            whiteScoreLead = -2.0,
            whiteWinRate = 0.2,
            summary = "estimate",
        )

        val plan = buildHumanEngineSyncSuccessPlan(
            afterMove = localMove.afterMove,
            moveDescription = localMove.lastMoveText,
            result = LocalEngineMoveResult(estimate = estimate),
            localMove = localMove,
            previousSnapshots = emptyList(),
        )

        assertTrue(plan is HumanEngineSyncDisplayPlan.ScoreEstimate)
        val score = plan as HumanEngineSyncDisplayPlan.ScoreEstimate
        assertEquals(localMove.capturedText, score.candidateText)
        assertEquals(localMove.afterMove, score.nextAnalysisState)
        assertEquals(estimate, score.display.scoreEstimate)
        assertTrue(score.display.engineMessage.contains("Black E5"))
    }

    @Test
    fun humanEngineSyncSuccessPlanBuildsFinalScoreUpdate() {
        val beforeMove = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val localMove = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = Move.Pass(StoneColor.White),
            reviewAnalysis = MoveAnalysisSnapshot.empty(beforeMove),
            previousMoveReviews = emptyList(),
        ).getOrThrow()
        val finalScore = FinalScoreResult(
            status = EngineStatus.ready("final"),
            rawScore = "W+6.5",
            winner = StoneColor.White,
            margin = 6.5,
            summary = "final",
        )
        val resolution = AiEndgameResolution(
            cleanup = DeadStoneCleanupResult(localMove.afterMove, removedStones = emptyList()),
            finalScore = finalScore,
            scoreSource = EndgameScoreSource.CleanedLocalArea,
            localFinalScore = finalScore,
            deadStonesResult = null,
            deadStonesError = null,
            locallyInferredDeadStones = emptyList(),
            engineScoreEstimate = null,
            engineScoreEstimateError = null,
            engineFinalScore = finalScore,
            engineFinalScoreError = null,
            prePassCandidates = emptyList(),
        )

        val plan = buildHumanEngineSyncSuccessPlan(
            afterMove = localMove.afterMove,
            moveDescription = localMove.lastMoveText,
            result = LocalEngineMoveResult(endgame = resolution),
            localMove = localMove,
            previousSnapshots = emptyList(),
        )

        assertTrue(plan is HumanEngineSyncDisplayPlan.FinalScore)
        val final = plan as HumanEngineSyncDisplayPlan.FinalScore
        assertEquals(localMove.afterMove, final.display.gameState)
        assertTrue(final.display.scoreText.contains("Final: W+6.5"))
        assertTrue(final.display.engineMessage.startsWith("Game ended after two passes."))
    }

    @Test
    fun humanEngineSyncFailurePlanKeepsLocalMoveAndRecordsLocalSnapshot() {
        val beforeMove = GameState.empty()
        val localMove = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = Move.Pass(StoneColor.Black),
            reviewAnalysis = MoveAnalysisSnapshot.empty(beforeMove),
            previousMoveReviews = emptyList(),
        ).getOrThrow()

        val plan = buildHumanEngineSyncFailurePlan(
            localMove = localMove,
            previousSnapshots = emptyList(),
            errorMessage = "sync failed",
        )

        assertEquals(localMove.capturedText, plan.candidateText)
        assertEquals("sync failed", plan.engineMessage)
        assertEquals(ScoreSnapshotSource.LocalAreaEstimate, plan.scoreSnapshots.single().source)
    }

    @Test
    fun humanEngineSyncCompletionPlanAppliesOnlyForCurrentOperationState() {
        val beforeMove = GameState.empty()
        val move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine))
        val localMove = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = move,
            reviewAnalysis = MoveAnalysisSnapshot.empty(beforeMove),
            previousMoveReviews = emptyList(),
        ).getOrThrow()
        val operation = engineOperationRequest(
            kind = EngineOperationKind.HumanMoveSync,
            state = localMove.afterMove,
            sessionGeneration = 2,
        )
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = 0.0,
            whiteWinRate = 0.5,
            summary = "estimate",
        )

        val apply = buildHumanEngineSyncSuccessCompletionPlan(
            operation = operation,
            currentState = localMove.afterMove,
            currentSessionGeneration = 2,
            afterMove = localMove.afterMove,
            moveDescription = localMove.lastMoveText,
            result = LocalEngineMoveResult(estimate = estimate),
            localMove = localMove,
            previousSnapshots = emptyList(),
        )
        val discard = buildHumanEngineSyncFailureCompletionPlan(
            operation = operation,
            currentState = localMove.afterMove.play(Move.Pass(StoneColor.White)),
            currentSessionGeneration = 2,
            localMove = localMove,
            previousSnapshots = emptyList(),
            errorMessage = "late failure",
        )

        assertTrue(apply is HumanEngineSyncCompletionPlan.ApplySuccess)
        assertTrue(discard is HumanEngineSyncCompletionPlan.Discard)
        assertEquals(
            "human_move_sync",
            (discard as HumanEngineSyncCompletionPlan.Discard).discard.operation,
        )
    }

    @Test
    fun humanEngineSyncEffectRunnerDelegatesPlanToEngineClient() = runBlocking {
        val afterMove = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val move = afterMove.moves.single()
        val profile = EngineProfile(name = "human-sync")
        val previousCandidate = CandidateMove(move = move, pointLoss = 0.0)
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = 0.0,
            whiteWinRate = 0.5,
            summary = "estimate",
        )
        val expected = LocalEngineMoveResult(estimate = estimate)
        val client = FakeHumanEngineSessionClient(expected)
        val plan = HumanEngineSyncRunPlan(
            afterMove = afterMove,
            profile = profile,
            move = move,
            previousReviewCandidates = listOf(previousCandidate),
        )

        val actual = client.runHumanEngineSyncEffect(GameSessionEffect.SyncHumanMove(plan))

        assertEquals(expected, actual)
        assertEquals(afterMove, client.afterMove)
        assertEquals(profile, client.profile)
        assertEquals(move, client.move)
        assertEquals(listOf(previousCandidate), client.previousReviewCandidates)
    }
}

private class FakeHumanEngineSessionClient(
    private val result: LocalEngineMoveResult,
) : EngineSessionClient {
    var afterMove: GameState? = null
        private set
    var profile: EngineProfile? = null
        private set
    var move: Move? = null
        private set
    var previousReviewCandidates: List<CandidateMove>? = null
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
    ): LocalEngineMoveResult {
        this.afterMove = afterMove
        this.profile = profile
        this.move = move
        this.previousReviewCandidates = previousReviewCandidates
        return result
    }

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
