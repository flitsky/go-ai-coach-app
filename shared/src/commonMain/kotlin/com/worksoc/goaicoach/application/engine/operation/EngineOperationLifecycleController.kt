package com.worksoc.goaicoach.application.engine.operation

import com.worksoc.goaicoach.application.concurrency.launchUiEffect
import com.worksoc.goaicoach.application.concurrency.sharedLock
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.runtimeEngineOperationCompletedLog
import com.worksoc.goaicoach.application.runtime.runtimeEngineOperationStartedLog
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.policy.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineOperationResultGuard
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
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
    private val activeJobsLock = sharedLock()

    /**
     * 지금 도는 AI 차례의 Job(refactor backlog #74). ⚠️ **`AutoAiTurnController`에 두지 않는 이유**: 그 컨트롤러는
     * `GoCoachApp`의 `remember(wiringContext)`로 세션 스냅샷이 바뀔 때마다 **새로 만들어진다** — 예약하는 순간
     * pending이 바뀌어 새 인스턴스가 생기므로, 무르기가 닿는 인스턴스는 Job을 모른다. 이 컨트롤러는 키 없는
     * `remember`라 화면이 사는 동안 하나다.
     */
    private var inFlightAutoAiTurnJob: Job? = null

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
        activeJobsLock.withLock {
            activeJobs[operation.operationId] = job
        }
        job.invokeOnCompletion {
            activeJobsLock.withLock {
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
            val job = activeJobsLock.withLock { activeJobs[req.operationId] }
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
     * **떠난 국면의** 엔진 작업을 취소한다(refactor backlog #15) — 무르기가 판을 되돌린 **직후**(세대가 바뀐 뒤) 부른다.
     * 대상은 지금 세대가 아닌 추천 수·형세·착수 동기화([StaleWorkKindsCancelledOnUndo])의 Job이다.
     *
     * 그 결과는 어차피 버려진다 — 세대가 바뀌어 결과 가드가 버린다. 예전에는 취소하지 않아도 됐다: 무르기 뒤 재동기화가
     * 그 작업과 **나란히** 돌았으니까(그래서 판이 섞였다). 이제 엔진 오퍼레이션은 한 번에 하나라(`LocalEngineSessionClient`의
     * 오퍼레이션 락), 취소하지 않으면 재동기화(blocking — 그동안 착수가 막힌다)가 버려질 작업이 끝나기를 기다린다. 취소된
     * 쪽은 엔진 답을 기다리지 않고 곧바로 락을 놓는다(2계층 배수가 답을 받는다).
     *
     * 목록에서는 지우지 않는다 — 이미 세대 필터로 busy에서 빠져 있고, 끝나면 러너의 `finally`가 스스로 지운다.
     * ⚠️ 그 밖의 종류(이어하기·계가 규칙 재동기화·새 대국·벤치마크·캐시 최적화)는 건드리지 않는다 — 결과 적용 말고도
     * 끝에 하는 일(준비 상태·진행 표시)이 있어, 취소하면 그 일이 빠진다.
     */
    fun cancelStaleGenerationOperations() {
        val generation = currentSessionGeneration()
        val targets = lifecycleState.activeOperations.values.filter { request ->
            request.sessionGeneration != generation && request.kind in StaleWorkKindsCancelledOnUndo
        }
        targets.forEach { request ->
            val job = activeJobsLock.withLock { activeJobs[request.operationId] }
            if (job != null && job.isActive) {
                job.cancel()
                diagnosticEventLog.append(
                    DiagnosticEvent(
                        severity = DiagnosticSeverity.Info,
                        code = "engine_operation_cancelled",
                        message = "Cancelled stale-generation operation after undo: ${request.operationId}",
                    )
                )
            }
        }
    }

    /**
     * AI 차례의 Job을 맡긴다(refactor backlog #74). 끝나면 스스로 빠진다 — 끝난 뒤의 [cancelInFlightAutoAiTurn]은
     * 아무것도 하지 않는다. 새 차례가 오면 앞의 것을 덮는다(pending이 둘을 동시에 띄우지 않는다).
     */
    fun trackAutoAiTurnJob(job: Job) {
        activeJobsLock.withLock { inFlightAutoAiTurnJob = job }
        job.invokeOnCompletion {
            activeJobsLock.withLock {
                if (inFlightAutoAiTurnJob === job) inFlightAutoAiTurnJob = null
            }
        }
    }

    /**
     * 도는 AI 차례를 **취소**한다(refactor backlog #74) — 무르기·나가기·새 대국·이어하기(분기 포함)·「엔진 다시
     * 시작하기」가 부른다. Android 홈(일시정지)은 부르지 않는다 — 돌아오면 그 수가 그대로 둬져 있어야 한다.
     *
     * 취소된 Job은 **곧바로** 끝난다(refactor backlog #15) — 막힌 GTP 읽기에 매달린 호출자도 답을 기다리지 않고
     * 취소로 돌아가고, 그 답 받기는 2계층 배수(`KataGoProcessEngineAdapter`의 `drainThenUnlock`)가 그 핸들의 왕복
     * 락과 함께 맡는다(원래 마감까지 기다리고, 안 오면 그 프로세스를 내린다. forceReset의 EOF도 배수를 끝낸다).
     * 그래서 정리(busy·예약 해제)는 러너의 `finally`가 곧바로 하고 오퍼레이션 락도 곧바로 풀린다 — 같은 프로세스로
     * 가는 다음 명령만 배수가 끝날 때까지 왕복 락에 줄 선다. 취소된 차례는 수를 두지 않고, genMove·형세 추정도 더
     * 부르지 않는다.
     */
    fun cancelInFlightAutoAiTurn() {
        val job = activeJobsLock.withLock { inFlightAutoAiTurnJob } ?: return
        if (!job.isActive) return
        job.cancel()
        diagnosticEventLog.append(
            DiagnosticEvent(
                severity = DiagnosticSeverity.Info,
                code = "engine_operation_cancelled",
                message = "Cancelled the in-flight AI turn.",
            )
        )
    }

    /**
     * 새 대국을 시작하기 직전에 호출한다. 이전 세대(예: 방금 기권한 대국)의 엔진 작업이
     * 아직 activeOperations에 남아 있으면, 늦게 끝나는 동안 새 대국의 isEngineBusy를
     * 계속 true로 잡아 AI 턴 예약을 조용히 취소시키는 경쟁 상태가 생긴다([EngineOperationLifecycleState]
     * 주석 참고). [cancelBackgroundOperations]와 달리 kind.isBlocking 여부와 무관하게 추적
     * 중인 작업을 전부 즉시 목록에서 비운다 — launchTracked를 거친 작업은 Job도 취소한다.
     * AutoAiTurn은 [trackAutoAiTurnJob]으로 맡긴 Job을 취소한다(refactor backlog #74 — 예전에는 취소할 수 없었다).
     * AutoAiEndgame은 그 AI 차례 Job 안에서 돌므로 같이 취소된다. 목록에서도 제거되므로 busy 플래그는 즉시
     * 정상화된다(세대 스코프 필터링이 최종 방어선).
     */
    fun evictAllOperations() {
        // 목록이 비어 있어도 먼저 취소한다 — AI 대 AI의 착수 지연 중인 차례는 아직 목록에 없다.
        cancelInFlightAutoAiTurn()
        val staleIds = lifecycleState.activeOperations.keys.toList()
        if (staleIds.isEmpty()) return
        staleIds.forEach { operationId ->
            val job = activeJobsLock.withLock { activeJobs[operationId] }
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

/**
 * 무르기가 취소하는 떠난 국면의 작업 종류([EngineOperationLifecycleController.cancelStaleGenerationOperations]) — 결과를
 * 세대 가드가 버리는 것 말고는 끝에 하는 일이 없는 것만 둔다.
 */
private val StaleWorkKindsCancelledOnUndo = setOf(
    EngineOperationKind.TopMoves,
    EngineOperationKind.ScoreEstimate,
    EngineOperationKind.HumanMoveSync,
)
