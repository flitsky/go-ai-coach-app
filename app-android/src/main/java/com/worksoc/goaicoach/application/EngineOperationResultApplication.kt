package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.GameState

internal data class EngineOperationDiscardLogPlan(
    val runtimeLog: String,
    val diagnosticEvent: DiagnosticEvent,
)

internal fun buildEngineOperationDiscardLogPlan(
    context: RuntimeLogContext,
    discard: EngineOperationResultGuard.Discard,
    currentState: GameState,
): EngineOperationDiscardLogPlan =
    EngineOperationDiscardLogPlan(
        runtimeLog = runtimeEngineOperationDiscardedLog(
            context = context,
            discard = discard,
        ),
        diagnosticEvent = engineOperationDiscardedDiagnosticEvent(
            discard = discard,
            currentState = currentState,
        ),
    )
