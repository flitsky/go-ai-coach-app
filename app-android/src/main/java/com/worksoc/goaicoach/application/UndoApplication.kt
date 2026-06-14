package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.MatchMode
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

internal sealed class UndoRequestPlan {
    data class ShowMessage(val message: String) : UndoRequestPlan()
    data class LocalTwoPlayerUndo(val syncEngineAfterUndo: Boolean) : UndoRequestPlan()
    data class EngineUndo(val undoCount: Int) : UndoRequestPlan()
}

internal const val UndoEngineInterventionDelayMillis = 1_000L

internal fun undoEngineInterventionQuietUntilMillis(
    nowMillis: Long,
    delayMillis: Long = UndoEngineInterventionDelayMillis,
): Long =
    nowMillis + delayMillis.coerceAtLeast(0L)

internal fun undoEngineInterventionRemainingDelayMillis(
    nowMillis: Long,
    quietUntilMillis: Long,
): Long =
    (quietUntilMillis - nowMillis).coerceAtLeast(0L)

internal fun buildUndoRequestPlan(
    currentState: GameState,
    matchMode: MatchMode,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    humanSeatCount: Int,
): UndoRequestPlan {
    if (currentState.moves.isEmpty()) {
        return UndoRequestPlan.ShowMessage("No move to undo.")
    }

    if (matchMode == MatchMode.LocalTwoPlayer) {
        if (isEngineBusy) {
            return UndoRequestPlan.ShowMessage("Engine is busy. Undo after the current analysis.")
        }
        return UndoRequestPlan.LocalTwoPlayerUndo(syncEngineAfterUndo = isEngineReady)
    }

    if (!isEngineReady) {
        return UndoRequestPlan.ShowMessage("Engine is not ready.")
    }
    if (isEngineBusy) {
        return UndoRequestPlan.ShowMessage("AI is busy. Undo after the current response.")
    }

    return UndoRequestPlan.EngineUndo(
        undoCount = if (humanSeatCount == 1) {
            minOf(2, currentState.moves.size)
        } else {
            1
        },
    )
}

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
