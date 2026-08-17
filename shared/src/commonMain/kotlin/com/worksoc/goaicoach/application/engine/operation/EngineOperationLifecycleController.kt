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
class EngineOperationLifecycleController(
    private val scope: CoroutineScope,
    private val runtimeEventLog: RuntimeEventLogPort,
    private val diagnosticEventLog: DiagnosticEventLogPort,
    private val currentRuntimeLogContext: () -> RuntimeLogContext,
    private val currentState: () -> GameState,
    private val currentSessionGeneration: () -> Long,
    private val onBusyChanged: (Boolean, Boolean, EngineActivityIndicator?, Int) -> Unit,
) {
    private var lifecycleState = EngineOperationLifecycleState()
    private val activeJobs = mutableMapOf<String, Job>()

    val isEngineBusy: Boolean get() = lifecycleState.isEngineBusy(currentSessionGeneration())
    val isBlockingBusy: Boolean get() = lifecycleState.isBlockingBusy(currentSessionGeneration())

    fun markStarted(operationId: String) {
        val kind = EngineOperationKind.entries.firstOrNull { operationId.startsWith(it.code) }
            ?: EngineOperationKind.EngineStartup
        // AutoAiTurn/AutoAiEndgame은 launchTracked를 거치지 않고 이 문자열 오버로드로만
        // 시작을 알리므로, 여기서 실제 현재 세대를 채워야 한다 — 예전에는 0L로 고정돼 있어
        // 새 대국을 한 번이라도 시작한 뒤(세대>0)로는 AI 턴이 영원히 "다른 세대" 취급되어
        // isEngineBusy 세대 필터링에서 항상 빠지는 회귀가 생겼을 것이다.
        val dummyRequest = EngineOperationRequest(
            operationId = operationId,
            kind = kind,
            sessionGeneration = currentSessionGeneration(),
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
        notifyBusyChanged()
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
        notifyBusyChanged()
        runtimeEventLog.append(
            runtimeEngineOperationCompletedLog(
                context = currentRuntimeLogContext(),
                operationId = operationId,
                activeOperationCount = lifecycleState.activeOperations.size,
            ),
        )
    }

    private fun notifyBusyChanged() {
        val generation = currentSessionGeneration()
        onBusyChanged(
            lifecycleState.isEngineBusy(generation),
            lifecycleState.isBlockingBusy(generation),
            lifecycleState.activityIndicator(generation),
            lifecycleState.engineTurnWaitCompletionSeq,
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

    /**
     * 새 대국을 시작하기 직전에 호출한다. 이전 세대(예: 방금 기권한 대국)의 엔진 작업이
     * 아직 activeOperations에 남아 있으면, 늦게 끝나는 동안 새 대국의 isEngineBusy를
     * 계속 true로 잡아 AI 턴 예약을 조용히 취소시키는 경쟁 상태가 생긴다([EngineOperationLifecycleState]
     * 주석 참고). [cancelBackgroundOperations]와 달리 kind.isBlocking 여부와 무관하게 추적
     * 중인 작업을 전부 즉시 목록에서 비운다 — launchTracked를 거친 작업은 Job도 취소한다.
     * AutoAiTurn/AutoAiEndgame처럼 launchTracked 밖에서 도는 작업은 Job을 취소할 수 없지만,
     * 목록에서는 제거되므로 busy 플래그는 즉시 정상화된다(세대 스코프 필터링이 최종 방어선).
     */
    fun evictAllOperations() {
        val staleIds = lifecycleState.activeOperations.keys.toList()
        if (staleIds.isEmpty()) return
        staleIds.forEach { operationId ->
            val job = synchronized(activeJobs) { activeJobs[operationId] }
            if (job != null && job.isActive) {
                job.cancel()
            }
            lifecycleState = applyEngineOperationLifecycleTransition(
                state = lifecycleState,
                transition = EngineOperationLifecycleTransition.Completed(operationId),
            )
        }
        notifyBusyChanged()
        diagnosticEventLog.append(
            DiagnosticEvent(
                severity = DiagnosticSeverity.Info,
                code = "engine_operations_evicted",
                message = "Evicted ${staleIds.size} stale operation(s) before starting a new game: ${staleIds.joinToString()}",
            )
        )
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
