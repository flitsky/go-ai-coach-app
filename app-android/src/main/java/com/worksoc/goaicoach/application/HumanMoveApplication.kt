package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshot
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
