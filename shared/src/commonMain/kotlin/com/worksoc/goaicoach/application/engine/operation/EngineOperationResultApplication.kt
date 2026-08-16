package com.worksoc.goaicoach.application.engine.operation

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.engineOperationDiscardedDiagnosticEvent
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.runtimeEngineOperationDiscardedLog
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent

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

internal fun recordEngineOperationDiscardLog(
    context: RuntimeLogContext,
    discard: EngineOperationResultGuard.Discard,
    currentState: GameState,
    runtimeEventLog: RuntimeEventLogPort,
    diagnosticEventLog: DiagnosticEventLogPort,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val plan = buildEngineOperationDiscardLogPlan(
        context = context,
        discard = discard,
        currentState = currentState,
    )
    runtimeEventLog.append(plan.runtimeLog, nowMillis)
    diagnosticEventLog.append(plan.diagnosticEvent, nowMillis)
}
