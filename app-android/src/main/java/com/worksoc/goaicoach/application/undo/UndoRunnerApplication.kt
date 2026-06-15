package com.worksoc.goaicoach.application.undo

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.EngineUndoWorkflowResult
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.engine.runEngineUndoWorkflowResult
import com.worksoc.goaicoach.application.engine.operation.EngineFallbackPolicy
import com.worksoc.goaicoach.application.engine.operation.EngineOperationKind
import com.worksoc.goaicoach.application.engine.operation.EngineOperationRequest
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.engine.operation.EngineTimeoutPolicy
import com.worksoc.goaicoach.application.engine.operation.engineOperationRequest
import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot

internal data class UndoLastTurnRunRequest(
    val currentState: GameState,
    val matchMode: MatchMode,
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    val humanSeatCount: Int,
    val showMessage: (String) -> Unit,
    val runLocalTwoPlayerUndo: (UndoRequestPlan.LocalTwoPlayerUndo) -> Unit,
    val runEngineUndo: (UndoRequestPlan.EngineUndo) -> Unit,
)

internal data class LocalTwoPlayerUndoRunRequest(
    val plan: UndoRequestPlan.LocalTwoPlayerUndo,
    val currentState: GameState,
    val scoreSnapshots: List<ScoreSnapshot>,
    val applyUndo: (UndoLocalStatePlan) -> Unit,
    val markQuiet: () -> Long,
    val setEngineMessage: (String) -> Unit,
    val cancelPendingPostUndoSync: () -> Unit,
    val schedulePostUndoSync: (GameState, Long) -> Unit,
)

internal data class EngineUndoRunRequest(
    val engineClient: EngineSessionClient? = null,
    val plan: UndoRequestPlan.EngineUndo,
    val currentState: GameState,
    val sessionGeneration: Long,
    val previousMoveReviews: List<MoveReviewMarker>,
    val scoreSnapshots: List<ScoreSnapshot>,
    val diagnosticEventLog: DiagnosticEventLogPort,
    val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    val currentStateProvider: () -> GameState,
    val currentSessionGenerationProvider: () -> Long,
    val applyUndo: (UndoLocalStatePlan) -> Unit,
    val setEngineMessage: (String) -> Unit,
    val markQuiet: () -> Long,
    val cancelPendingPostUndoSync: () -> Unit,
    val appendDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
    val runEngineWork: suspend (EngineOperationRequest, UndoRequestPlan.EngineUndo) -> EngineUndoWorkflowResult =
        { operation, undoPlan ->
            val client = requireNotNull(engineClient) {
                "engineClient is required unless runEngineWork is supplied."
            }
            runEngineIo {
                client.runEngineUndoWorkflowResult(
                    effect = GameSessionEffect.UndoEngineMoves(
                        state = currentState,
                        undoCount = undoPlan.undoCount,
                    ),
                    operationRequest = operation,
                    diagnosticEventLog = diagnosticEventLog,
                )
            }
        },
)

internal fun runUndoLastTurnApplication(request: UndoLastTurnRunRequest) {
    when (
        val plan = buildUndoRequestPlan(
            currentState = request.currentState,
            matchMode = request.matchMode,
            isEngineReady = request.isEngineReady,
            isEngineBusy = request.isEngineBusy,
            humanSeatCount = request.humanSeatCount,
        )
    ) {
        is UndoRequestPlan.ShowMessage -> request.showMessage(plan.message)
        is UndoRequestPlan.LocalTwoPlayerUndo -> request.runLocalTwoPlayerUndo(plan)
        is UndoRequestPlan.EngineUndo -> request.runEngineUndo(plan)
    }
}

internal fun runLocalTwoPlayerUndoApplication(request: LocalTwoPlayerUndoRunRequest) {
    val undo = buildLocalTwoPlayerUndoPlan(
        currentState = request.currentState,
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

internal fun runEngineUndoApplication(request: EngineUndoRunRequest) {
    val operation = engineOperationRequest(
        kind = EngineOperationKind.EngineUndo,
        state = request.currentState,
        sessionGeneration = request.sessionGeneration,
        timeoutPolicy = EngineTimeoutPolicy(label = "engine-undo"),
        fallbackPolicy = EngineFallbackPolicy.LocalEngine,
    )
    request.launchEngineOperation(operation) {
        val result = request.runEngineWork(operation, request.plan)
        when (
            val completion = buildEngineUndoCompletionPlan(
                result = result,
                operation = operation,
                currentState = request.currentStateProvider(),
                currentSessionGeneration = request.currentSessionGenerationProvider(),
                undoCount = request.plan.undoCount,
                previousMoveReviews = request.previousMoveReviews,
                scoreSnapshots = request.scoreSnapshots,
            )
        ) {
            is EngineUndoCompletionPlan.ApplySuccess -> {
                request.applyUndo(completion.undo)
                request.setEngineMessage(completion.engineMessage)
                request.markQuiet()
                request.cancelPendingPostUndoSync()
            }

            is EngineUndoCompletionPlan.ApplyFailure ->
                request.setEngineMessage(completion.engineMessage)

            is EngineUndoCompletionPlan.Discard ->
                request.appendDiscardLog(completion.discard)
        }
    }
}
