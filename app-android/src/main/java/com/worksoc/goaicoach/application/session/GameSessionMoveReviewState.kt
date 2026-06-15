package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.autoai.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.humanmove.HumanMoveLocalResult
import com.worksoc.goaicoach.application.undo.UndoLocalStatePlan

internal data class GameSessionMoveReviewState(
    val moveReviewText: String,
    val moveReviews: List<MoveReviewMarker>,
    val lastMoveText: String,
) {
    fun applyAutoAiTurnDisplayPlan(display: AutoAiTurnDisplayPlan): GameSessionMoveReviewState =
        copy(lastMoveText = display.lastMoveText)

    fun applyHumanMoveLocalResult(result: HumanMoveLocalResult): GameSessionMoveReviewState =
        copy(
            moveReviewText = result.moveReview.text,
            moveReviews = result.moveReviews,
            lastMoveText = result.lastMoveText,
        )

    fun applyUndoLocalStatePlan(undo: UndoLocalStatePlan): GameSessionMoveReviewState =
        copy(
            moveReviewText = undo.moveReviewText,
            moveReviews = undo.moveReviews,
            lastMoveText = undo.lastMoveText,
        )

    companion object {
        fun reset(
            moveReviewText: String,
            lastMoveText: String,
        ): GameSessionMoveReviewState =
            GameSessionMoveReviewState(
                moveReviewText = moveReviewText,
                moveReviews = emptyList(),
                lastMoveText = lastMoveText,
            )
    }
}
