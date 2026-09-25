package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.contract.GameSessionEffect
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleCallbacks
import com.worksoc.goaicoach.application.engine.operation.runEngineOperationInScope
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.policy.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.policy.EngineOperationGate
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.policy.engineOperationRequest
import com.worksoc.goaicoach.shared.policy.evaluateEngineBenchmarkGate
import kotlinx.coroutines.delay

internal data class EngineBenchmarkRunRequest(
    val engineClient: EngineLifecycleClient,
    val store: EngineBenchmarkStorePort,
    /** 디버그 리포트용 저장 원문(refactor backlog #86) — [EngineBenchmarkController]의 같은 이름 참고. */
    val storedBenchmarkText: () -> String,
    val state: GameState,
    val sessionGeneration: Long,
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    val benchmarkUiState: EngineBenchmarkUiState,
    val diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
    val lifecycleCallbacks: EngineOperationLifecycleCallbacks = EngineOperationLifecycleCallbacks(),
    val nowMillis: () -> Long = ::currentEpochMillis,
    val delayMillis: suspend (Long) -> Unit = { millis -> delay(millis) },
    val runEngineWork: suspend (suspend () -> StartupBenchmarkWorkflowResult) -> StartupBenchmarkWorkflowResult =
        { block -> runEngineIo { block() } },
    /**
     * ⚠️ **`Block` 전체를 넘긴다 — 문자열만 넘기지 말 것.** `message`는 진단용 영어이고
     * `reason`은 화면이 번역할 타입인데, 예전에는 문자열만 넘겨 UI가 번역할 근거를 잃었다.
     */
    val onBlocked: (EngineOperationGate.Block) -> Unit = {},
    val onBenchmarkUiState: (EngineBenchmarkUiState) -> Unit = {},
    val onDisplayPlan: (EngineBenchmarkDisplayPlan) -> Unit = {},
    val onProgress: suspend (EngineBenchmarkProgress, EngineBenchmarkDisplayPlan) -> Unit = { _, _ -> },
)

internal suspend fun runEngineBenchmarkApplication(request: EngineBenchmarkRunRequest) {
    when (
        val gate = evaluateEngineBenchmarkGate(
            isEngineReady = request.isEngineReady,
            supportsDeviceBenchmark = request.engineClient.capabilities.supportsDeviceBenchmark,
            isEngineBusy = request.isEngineBusy,
            isBenchmarkRunning = request.benchmarkUiState.isRunning,
        )
    ) {
        EngineOperationGate.Allow -> Unit
        EngineOperationGate.NoOp -> return
        is EngineOperationGate.Block -> {
            request.onBlocked(gate)
            return
        }
    }

    val clearedState = request.benchmarkUiState.clearResult()
    request.onBenchmarkUiState(clearedState)
    val operation = engineOperationRequest(
        kind = EngineOperationKind.StartupBenchmark,
        state = request.state,
        sessionGeneration = request.sessionGeneration,
        timeoutPolicy = EngineTimeoutPolicy(label = "startup-benchmark"),
        fallbackPolicy = EngineFallbackPolicy.None,
    )

    runEngineOperationInScope(
        request = operation,
        callbacks = request.lifecycleCallbacks,
    ) {
        val waitingState = clearedState.startWaitingForEngineSettle()
        request.onBenchmarkUiState(waitingState)
        request.onDisplayPlan(engineBenchmarkWaitingDisplayPlan())
        request.delayMillis(EngineBenchmarkStartupSettleDelayMillis)

        request.onDisplayPlan(engineBenchmarkRunningDisplayPlan())
        val benchmarkResult = request.runEngineWork {
            request.engineClient.runStartupBenchmarkWorkflowResult(
                effect = GameSessionEffect.RunStartupBenchmark,
                context = StartupBenchmarkExecutionContext(
                    restoreState = request.state,
                    nowMillis = request.nowMillis(),
                ),
                operationRequest = operation,
                diagnosticEventLog = request.diagnosticEventLog,
                onProgress = { progress ->
                    request.onProgress(progress, progress.toEngineBenchmarkDisplayPlan())
                },
            )
        }

        when (benchmarkResult) {
            is StartupBenchmarkWorkflowResult.Success -> {
                val profile = benchmarkResult.profile
                request.store.save(profile)
                request.onBenchmarkUiState(
                    waitingState.completeWithProfile(
                        // 저장 **뒤에** 읽는다 — 앞에서 읽으면 리포트가 옛 원문을 보인다.
                        benchmarkText = request.storedBenchmarkText(),
                        profile = profile,
                    ),
                )
                request.onDisplayPlan(engineBenchmarkCompletedDisplayPlan(profile = profile))
            }

            is StartupBenchmarkWorkflowResult.Failure -> {
                request.onDisplayPlan(engineBenchmarkFailureDisplayPlan(benchmarkResult.error))
                request.onBenchmarkUiState(waitingState.failWithoutProfile())
            }
        }
    }
}

internal suspend fun EngineLifecycleClient.runStartupBenchmarkEffect(
    effect: GameSessionEffect.RunStartupBenchmark,
    context: StartupBenchmarkExecutionContext,
    operationRequest: EngineOperationRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
    onProgress: suspend (EngineBenchmarkProgress) -> Unit = {},
): EngineBenchmarkProfile {
    return runObservedEngineOperation(
        request = operationRequest,
        diagnosticEventLog = diagnosticEventLog,
    ) {
        runStartupBenchmark(
            restoreState = context.restoreState,
            nowMillis = context.nowMillis,
            onProgress = onProgress,
        )
    }
}

internal suspend fun EngineLifecycleClient.runStartupBenchmarkWorkflowResult(
    effect: GameSessionEffect.RunStartupBenchmark,
    context: StartupBenchmarkExecutionContext,
    operationRequest: EngineOperationRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
    onProgress: suspend (EngineBenchmarkProgress) -> Unit = {},
): StartupBenchmarkWorkflowResult =
    runCatching {
        runStartupBenchmarkEffect(
            effect = effect,
            context = context,
            operationRequest = operationRequest,
            diagnosticEventLog = diagnosticEventLog,
            onProgress = onProgress,
        )
    }.fold(
        onSuccess = { profile -> StartupBenchmarkWorkflowResult.Success(profile) },
        onFailure = { error -> StartupBenchmarkWorkflowResult.Failure(error) },
    )
