package com.worksoc.goaicoach.application.undo

import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot

internal data class UndoLastTurnRunRequest(
    val currentState: GameState,
    val matchMode: MatchMode,
    val isEngineReady: Boolean,
    val playerSetup: PlayerSetup,
    val showMessage: (String) -> Unit,
    val runApplyLocalUndo: (UndoRequestPlan.ApplyLocalUndo) -> Unit,
)

internal data class ApplyLocalUndoRunRequest(
    val plan: UndoRequestPlan.ApplyLocalUndo,
    val currentState: GameState,
    val previousMoveReviews: List<MoveReviewMarker>,
    val scoreSnapshots: List<ScoreSnapshot>,
    val applyUndo: (UndoLocalStatePlan) -> Unit,
    val markQuiet: () -> Long,
    val setEngineMessage: (String) -> Unit,
    val cancelPendingPostUndoSync: () -> Unit,
    val schedulePostUndoSync: (GameState, Long) -> Unit,
)

internal fun runUndoLastTurnApplication(request: UndoLastTurnRunRequest) {
    when (
        val plan = buildUndoRequestPlan(
            currentState = request.currentState,
            matchMode = request.matchMode,
            isEngineReady = request.isEngineReady,
            playerSetup = request.playerSetup,
        )
    ) {
        is UndoRequestPlan.ShowMessage -> request.showMessage(plan.message)
        is UndoRequestPlan.ApplyLocalUndo -> request.runApplyLocalUndo(plan)
    }
}

/**
 * Applies the local rollback synchronously, then either drops or schedules the
 * engine resync. Runs the same way whether the engine is idle or mid-turn --
 * see [UndoRequestPlan.ApplyLocalUndo] for why that's what makes undo safe to
 * trigger at any time.
 */
internal fun runApplyLocalUndoApplication(request: ApplyLocalUndoRunRequest) {
    val undo = buildUndoLocalStatePlan(
        currentState = request.currentState,
        undoCount = request.plan.undoCount,
        previousMoveReviews = request.previousMoveReviews,
        scoreSnapshots = request.scoreSnapshots,
    )
    val nextState = undo.gameState
    request.applyUndo(undo)
    val quietUntilMillis = request.markQuiet()
    if (!request.plan.syncEngineAfterUndo) {
        request.setEngineMessage("Local undo completed without engine sync.")
        request.cancelPendingPostUndoSync()
        return
    }

    request.setEngineMessage("Local undo completed. Engine analysis will resume after undo input settles.")
    request.schedulePostUndoSync(nextState, quietUntilMillis)
}
