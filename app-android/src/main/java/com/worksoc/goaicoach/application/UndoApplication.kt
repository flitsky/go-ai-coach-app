package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe
import com.worksoc.goaicoach.shared.replayWithoutLastMoves

internal data class UndoLocalStatePlan(
    val gameState: GameState,
    val candidateText: String,
    val reviewAnalysis: MoveAnalysisSnapshot,
    val scoreText: String,
    val scoreSnapshots: List<ScoreSnapshot>,
    val moveReviewText: String,
    val moveReviews: List<MoveReviewMarker>,
    val lastMoveText: String,
    val endgameLog: String,
)

internal fun buildLocalTwoPlayerUndoPlan(
    currentState: GameState,
    scoreSnapshots: List<ScoreSnapshot>,
): UndoLocalStatePlan {
    val nextState = currentState.replayWithoutLastMoves(1)
    return UndoLocalStatePlan(
        gameState = nextState,
        candidateText = "Captured: Black ${nextState.capturedBy(StoneColor.Black)} / White ${nextState.capturedBy(StoneColor.White)}",
        reviewAnalysis = MoveAnalysisSnapshot.empty(nextState),
        scoreText = "Score estimate not current.",
        scoreSnapshots = ScoreTimeline.record(
            ScoreTimeline.trimAfter(scoreSnapshots, nextState.moves.size),
            localScoreSnapshot(nextState),
        ),
        moveReviewText = "Move review cleared by undo.",
        moveReviews = emptyList(),
        lastMoveText = nextState.moves.lastOrNull()?.describe(nextState.boardSize) ?: "None",
        endgameLog = "Endgame log cleared by undo.",
    )
}

internal fun buildEngineUndoPlan(
    currentState: GameState,
    undoCount: Int,
    previousMoveReviews: List<MoveReviewMarker>,
    scoreSnapshots: List<ScoreSnapshot>,
): UndoLocalStatePlan {
    val nextState = currentState.replayWithoutLastMoves(undoCount)
    return UndoLocalStatePlan(
        gameState = nextState,
        candidateText = "Undo cleared current Top Moves.",
        reviewAnalysis = MoveAnalysisSnapshot.empty(nextState),
        scoreText = "Score estimate not current.",
        scoreSnapshots = ScoreTimeline.trimAfter(scoreSnapshots, nextState.moves.size),
        moveReviewText = "Move review cleared by undo.",
        moveReviews = previousMoveReviews.filter { marker -> marker.moveNumber <= nextState.moves.size },
        lastMoveText = nextState.moves.lastOrNull()?.describe(nextState.boardSize) ?: "None",
        endgameLog = "Endgame log cleared by undo.",
    )
}
