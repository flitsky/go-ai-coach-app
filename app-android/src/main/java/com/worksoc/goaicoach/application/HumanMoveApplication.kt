package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe

internal data class HumanMoveLocalResult(
    val afterMove: GameState,
    val moveReview: MoveReviewResult,
    val moveReviews: List<MoveReviewMarker>,
    val lastMoveText: String,
    val capturedText: String,
    val localScoreSnapshot: ScoreSnapshot,
    val localFinalScore: FinalScoreResult?,
)

internal sealed class HumanEngineSyncDisplayPlan {
    data class FinalScore(val display: FinalScoreDisplayPlan) : HumanEngineSyncDisplayPlan()
    data class ScoreEstimate(
        val display: ScoreEstimateDisplayPlan,
        val candidateText: String,
        val nextAnalysisState: GameState,
    ) : HumanEngineSyncDisplayPlan()
    data object NoUpdate : HumanEngineSyncDisplayPlan()
}

internal data class HumanEngineSyncFailurePlan(
    val scoreSnapshots: List<ScoreSnapshot>,
    val candidateText: String,
    val engineMessage: String,
)

internal fun applyHumanMoveLocally(
    beforeMove: GameState,
    move: Move,
    reviewAnalysis: MoveAnalysisSnapshot,
    previousMoveReviews: List<MoveReviewMarker>,
): Result<HumanMoveLocalResult> =
    runCatching {
        val afterMove = beforeMove.play(move)
        val moveReview = buildMoveReview(
            move = move,
            analysis = reviewAnalysis,
            boardSize = beforeMove.boardSize,
            moveNumber = afterMove.moves.size,
        )
        HumanMoveLocalResult(
            afterMove = afterMove,
            moveReview = moveReview,
            moveReviews = previousMoveReviews.withReviewMarker(moveReview.marker),
            lastMoveText = move.describe(beforeMove.boardSize),
            capturedText = "Captured: Black ${afterMove.capturedBy(StoneColor.Black)} / White ${afterMove.capturedBy(StoneColor.White)}",
            localScoreSnapshot = localScoreSnapshot(afterMove),
            localFinalScore = if (afterMove.hasConsecutivePasses()) {
                BoardScorer.score(afterMove)
            } else {
                null
            },
        )
    }

internal fun buildHumanEngineSyncSuccessPlan(
    afterMove: GameState,
    moveDescription: String,
    result: LocalEngineMoveResult,
    localMove: HumanMoveLocalResult,
    previousSnapshots: List<ScoreSnapshot>,
): HumanEngineSyncDisplayPlan {
    result.endgame?.let { endgame ->
        return HumanEngineSyncDisplayPlan.FinalScore(
            buildResolvedEndgameDisplayPlan(
                source = "human-engine-dead-stone-cleanup",
                originalState = afterMove,
                resolution = endgame,
                previousSnapshots = previousSnapshots,
                engineMessagePrefix = "Game ended after two passes.",
            ),
        )
    }

    result.estimate?.let { estimate ->
        return HumanEngineSyncDisplayPlan.ScoreEstimate(
            display = buildEngineEstimateDisplayPlan(
                state = afterMove,
                estimate = estimate,
                previousSnapshots = previousSnapshots,
                engineMessage = "Human move accepted and engine analysis synced: $moveDescription.",
            ),
            candidateText = localMove.capturedText,
            nextAnalysisState = afterMove,
        )
    }

    return HumanEngineSyncDisplayPlan.NoUpdate
}

internal fun buildHumanEngineSyncFailurePlan(
    localMove: HumanMoveLocalResult,
    previousSnapshots: List<ScoreSnapshot>,
    errorMessage: String?,
): HumanEngineSyncFailurePlan =
    HumanEngineSyncFailurePlan(
        scoreSnapshots = ScoreTimeline.record(previousSnapshots, localMove.localScoreSnapshot),
        candidateText = localMove.capturedText,
        engineMessage = errorMessage ?: "Human move accepted, but engine sync failed.",
    )
