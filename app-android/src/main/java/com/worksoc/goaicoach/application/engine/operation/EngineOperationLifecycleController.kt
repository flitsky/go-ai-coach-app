package com.worksoc.goaicoach.application.engine.operation

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.launchUiEffect
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.runtimeEngineOperationCompletedLog
import com.worksoc.goaicoach.application.runtime.runtimeEngineOperationStartedLog
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity
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
    private val onBusyChanged: (Boolean, Boolean, EngineActivityIndicator?) -> Unit,
) {
    private var lifecycleState = EngineOperationLifecycleState()
    private val activeJobs = mutableMapOf<String, Job>()

    val isEngineBusy: Boolean get() = lifecycleState.isEngineBusy
    val isBlockingBusy: Boolean get() = lifecycleState.isBlockingBusy

    fun markStarted(operationId: String) {
        val kind = EngineOperationKind.entries.firstOrNull { operationId.startsWith(it.code) }
            ?: EngineOperationKind.EngineStartup
        val dummyRequest = EngineOperationRequest(
            operationId = operationId,
            kind = kind,
            sessionGeneration = 0L,
            boardFingerprint = "dummy",
            moveCount = 0,
            timeoutPolicy = EngineTimeoutPolicy(),
            fallbackPolicy = EngineFallbackPolicy.None,
            backendId = "local-engine"
        )
        markStarted(dummyRequest)
    }

    fun markStarted(request: EngineOperationRequest) {
        lifecycleState = applyEngineOperationLifecycleTransition(
            state = lifecycleState,
            transition = EngineOperationLifecycleTransition.Started(request),
        )
        onBusyChanged(
            lifecycleState.isEngineBusy,
            lifecycleState.isBlockingBusy,
            lifecycleState.activityIndicator,
        )
        runtimeEventLog.append(
            runtimeEngineOperationStartedLog(
                context = currentRuntimeLogContext(),
                operationId = request.operationId,
                activeOperationCount = lifecycleState.activeOperations.size,
            ),
        )
    }

    fun markCompleted(operationId: String) {
        lifecycleState = applyEngineOperationLifecycleTransition(
            state = lifecycleState,
            transition = EngineOperationLifecycleTransition.Completed(operationId),
        )
        onBusyChanged(
            lifecycleState.isEngineBusy,
            lifecycleState.isBlockingBusy,
            lifecycleState.activityIndicator,
        )
        runtimeEventLog.append(
            runtimeEngineOperationCompletedLog(
                context = currentRuntimeLogContext(),
                operationId = operationId,
                activeOperationCount = lifecycleState.activeOperations.size,
            ),
        )
    }

    fun callbacks(): EngineOperationLifecycleCallbacks =
        EngineOperationLifecycleCallbacks(
            onStarted = { request -> markStarted(request) },
            onCompleted = { request -> markCompleted(request.operationId) },
        )

    fun launchTracked(
        operation: EngineOperationRequest,
        block: suspend () -> Unit,
    ): Job {
        val job = launchUiEffect(scope) {
            runEngineOperationInScope(
                request = operation,
                callbacks = callbacks(),
            ) {
                block()
            }
        }
        synchronized(activeJobs) {
            activeJobs[operation.operationId] = job
        }
        job.invokeOnCompletion {
            synchronized(activeJobs) {
                activeJobs.remove(operation.operationId)
            }
        }
        return job
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

    fun cancelBackgroundOperations() {
        val targets = lifecycleState.activeOperations.values.filter { !it.kind.isBlocking }
        targets.forEach { req ->
            val job = synchronized(activeJobs) { activeJobs[req.operationId] }
            if (job != null && job.isActive) {
                job.cancel()
                diagnosticEventLog.append(
                    DiagnosticEvent(
                        severity = DiagnosticSeverity.Info,
                        code = "engine_operation_cancelled",
                        message = "Cancelled background operation: ${req.operationId}"
                    )
                )
            }
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
