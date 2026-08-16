package com.worksoc.goaicoach.application.engine.operation

internal fun com.worksoc.goaicoach.shared.engine.EngineOperationGate.toApplicationGate(): EngineOperationGate =
    when (this) {
        com.worksoc.goaicoach.shared.engine.EngineOperationGate.Allow -> EngineOperationGate.Allow
        com.worksoc.goaicoach.shared.engine.EngineOperationGate.NoOp -> EngineOperationGate.NoOp
        is com.worksoc.goaicoach.shared.engine.EngineOperationGate.Block ->
            EngineOperationGate.Block(message)
    }

internal fun com.worksoc.goaicoach.shared.engine.EngineOperationResultGuard.toApplicationGuard():
    EngineOperationResultGuard =
    when (this) {
        com.worksoc.goaicoach.shared.engine.EngineOperationResultGuard.Apply ->
            EngineOperationResultGuard.Apply

        is com.worksoc.goaicoach.shared.engine.EngineOperationResultGuard.Discard ->
            EngineOperationResultGuard.Discard(
                reason = reason,
                operation = operation,
                operationId = operationId,
                sessionGeneration = sessionGeneration,
            )
    }

internal fun com.worksoc.goaicoach.shared.engine.EngineOperationApplyPlan.toApplicationApplyPlan():
    EngineOperationApplyPlan =
    when (this) {
        com.worksoc.goaicoach.shared.engine.EngineOperationApplyPlan.Apply ->
            EngineOperationApplyPlan.Apply

        is com.worksoc.goaicoach.shared.engine.EngineOperationApplyPlan.Discard ->
            EngineOperationApplyPlan.Discard(discard.toApplicationDiscard())
    }

internal fun com.worksoc.goaicoach.shared.engine.EngineOperationResultGuard.Discard.toApplicationDiscard():
    EngineOperationResultGuard.Discard =
    EngineOperationResultGuard.Discard(
        reason = reason,
        operation = operation,
        operationId = operationId,
        sessionGeneration = sessionGeneration,
    )
