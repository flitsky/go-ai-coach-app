package com.worksoc.goaicoach.application.engine.operation

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.launchUiEffect
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.runtimeEngineOperationCompletedLog
import com.worksoc.goaicoach.application.runtime.runtimeEngineOperationStartedLog
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Owns engine-operation lifecycle tracking: active-operation bookkeeping, the
 * derived "engine busy" flag, runtime start/complete logging, scoped launches,
 * and discard logging.
 *
 * The lifecycle state itself is plain (non-Compose) internal state. The caller
 * keeps only the derived busy flag as observable state and is notified through
 * [onBusyChanged] (called synchronously before each log append, so a runtime log
 * context that reads the busy flag observes the post-transition value).
 */
internal class EngineOperationLifecycleController(
    private val scope: CoroutineScope,
    private val runtimeEventLog: RuntimeEventLogPort,
    private val diagnosticEventLog: DiagnosticEventLogPort,
    private val currentRuntimeLogContext: () -> RuntimeLogContext,
    private val currentState: () -> GameState,
    private val onBusyChanged: (Boolean) -> Unit,
) {
    private var lifecycleState = EngineOperationLifecycleState()

    val isEngineBusy: Boolean get() = lifecycleState.isEngineBusy

    fun markStarted(operationId: String) {
        lifecycleState = applyEngineOperationLifecycleTransition(
            state = lifecycleState,
            transition = EngineOperationLifecycleTransition.Started(operationId),
        )
        onBusyChanged(lifecycleState.isEngineBusy)
        runtimeEventLog.append(
            runtimeEngineOperationStartedLog(
                context = currentRuntimeLogContext(),
                operationId = operationId,
                activeOperationCount = lifecycleState.activeOperationIds.size,
            ),
        )
    }

    fun markCompleted(operationId: String) {
        lifecycleState = applyEngineOperationLifecycleTransition(
            state = lifecycleState,
            transition = EngineOperationLifecycleTransition.Completed(operationId),
        )
        onBusyChanged(lifecycleState.isEngineBusy)
        runtimeEventLog.append(
            runtimeEngineOperationCompletedLog(
                context = currentRuntimeLogContext(),
                operationId = operationId,
                activeOperationCount = lifecycleState.activeOperationIds.size,
            ),
        )
    }

    fun callbacks(): EngineOperationLifecycleCallbacks =
        EngineOperationLifecycleCallbacks(
            onStarted = { request -> markStarted(request.operationId) },
            onCompleted = { request -> markCompleted(request.operationId) },
        )

    fun launchTracked(
        operation: EngineOperationRequest,
        block: suspend () -> Unit,
    ): Job =
        launchUiEffect(scope) {
            runEngineOperationInScope(
                request = operation,
                callbacks = callbacks(),
            ) {
                block()
            }
        }

    suspend fun runTracked(
        operation: EngineOperationRequest,
        block: suspend () -> Unit,
    ) {
        runEngineOperationInScope(
            request = operation,
            callbacks = callbacks(),
        ) {
            block()
        }
    }

    fun appendDiscardLog(discard: EngineOperationResultGuard.Discard) {
        recordEngineOperationDiscardLog(
            context = currentRuntimeLogContext(),
            discard = discard,
            currentState = currentState(),
            runtimeEventLog = runtimeEventLog,
            diagnosticEventLog = diagnosticEventLog,
        )
    }
}
