package com.worksoc.goaicoach.application.undo

import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
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

    /**
     * Applies [undoCount] moves of local rollback immediately (synchronously,
     * regardless of whether the engine is mid-turn) and, if [syncEngineAfterUndo],
     * schedules a deferred full engine resync once the engine settles. Local-first
     * application is what makes undo safe to trigger while an AI turn is in
     * flight: the position-scoped staleness guard used elsewhere would otherwise
     * discard a synchronous engine-undo result the instant the AI's move lands
     * first (see [UndoController.schedulePostUndoSync]).
     */
    data class ApplyLocalUndo(val undoCount: Int, val syncEngineAfterUndo: Boolean) : UndoRequestPlan()
}

internal const val UndoEngineInterventionDelayMillis = 1_000L
internal const val UndoEngineBusyPollIntervalMillis = 100L

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

/**
 * How many moves back land on the state right before the human's own last move,
 * for a game with exactly one human seat. Recomputed from the *current* move
 * count every time this is called (never a value captured earlier), so it stays
 * correct no matter how many extra AI moves landed while a previous undo was
 * waiting on the engine -- see [UndoRequestPlan.ApplyLocalUndo].
 *
 * The move count right before a human turn always has the same parity as the
 * game's move list: even if the human moved first, odd if the human moved
 * second. `moves.first().player` (not "Black") is what moved first, so this is
 * correct for handicap games too (White moves first when handicap stones are
 * placed as setup, not as move 1 -- see [GameState.withHandicap]).
 */
private fun humanUndoMoveCount(currentState: GameState, humanColor: StoneColor): Int? {
    val firstMover = currentState.moves.first().player
    val targetParity = if (humanColor == firstMover) 0 else 1
    val size = currentState.moves.size
    val undoCount = if (size % 2 == targetParity) 2 else 1
    return undoCount.takeIf { it <= size }
}

internal fun buildUndoRequestPlan(
    currentState: GameState,
    matchMode: MatchMode,
    isEngineReady: Boolean,
    playerSetup: PlayerSetup,
): UndoRequestPlan {
    if (currentState.moves.isEmpty()) {
        return UndoRequestPlan.ShowMessage("No move to undo.")
    }

    if (matchMode == MatchMode.AiVsAi) {
        return UndoRequestPlan.ShowMessage("Undo is not available while AI controls both sides.")
    }

    if (matchMode == MatchMode.LocalTwoPlayer) {
        return UndoRequestPlan.ApplyLocalUndo(undoCount = 1, syncEngineAfterUndo = isEngineReady)
    }

    val humanColor = if (playerSetup.sideFor(StoneColor.Black).controller == SeatController.Human) {
        StoneColor.Black
    } else {
        StoneColor.White
    }
    val undoCount = humanUndoMoveCount(currentState, humanColor)
        ?: return UndoRequestPlan.ShowMessage("No human move to undo yet.")

    return UndoRequestPlan.ApplyLocalUndo(undoCount = undoCount, syncEngineAfterUndo = isEngineReady)
}

internal fun buildUndoLocalStatePlan(
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
        scoreSnapshots = ScoreTimeline.record(
            ScoreTimeline.trimAfter(scoreSnapshots, nextState.moves.size),
            localScoreSnapshot(nextState),
        ),
        moveReviewText = "Move review cleared by undo.",
        moveReviews = previousMoveReviews.filter { marker -> marker.moveNumber <= nextState.moves.size },
        lastMoveText = nextState.moves.lastOrNull()?.describe(nextState.boardSize) ?: "None",
        endgameLog = "Endgame log cleared by undo.",
    )
}
