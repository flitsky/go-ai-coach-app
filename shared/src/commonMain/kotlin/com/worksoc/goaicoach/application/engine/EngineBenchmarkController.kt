package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleCallbacks
import com.worksoc.goaicoach.shared.GameState
import kotlinx.coroutines.CoroutineScope

/**
 * Owns the user-initiated device-benchmark UI flow (show, rerun) so the screen
 * entry point does not wire it inline.
 *
 * State the rest of the screen also reads (the benchmark UI state, engine
 * message) stays in the caller and is reached through the accessors below; only
 * the benchmark-specific orchestration lives here.
 */
class EngineBenchmarkController(
    private val scope: CoroutineScope,
    private val engineClient: EngineSessionClient,
    private val store: EngineBenchmarkStorePort,
    private val diagnosticEventLog: DiagnosticEventLogPort,
    private val lifecycleCallbacks: () -> EngineOperationLifecycleCallbacks,
    private val currentState: () -> GameState,
    private val sessionGeneration: () -> Long,
    private val isEngineReady: () -> Boolean,
    private val isEngineBusy: () -> Boolean,
    private val currentBenchmarkUiState: () -> EngineBenchmarkUiState,
    private val onBenchmarkUiState: (EngineBenchmarkUiState) -> Unit,
    private val onEngineMessage: (String) -> Unit,
    private val onDisplayPlan: (EngineBenchmarkDisplayPlan) -> Unit,
) {
    suspend fun run() {
        runEngineBenchmarkApplication(
            EngineBenchmarkRunRequest(
                engineClient = engineClient,
                store = store,
                state = currentState(),
                sessionGeneration = sessionGeneration(),
                isEngineReady = isEngineReady(),
                isEngineBusy = isEngineBusy(),
                benchmarkUiState = currentBenchmarkUiState(),
                diagnosticEventLog = diagnosticEventLog,
                lifecycleCallbacks = lifecycleCallbacks(),
                onBlocked = { block ->
                    // 진단 문자열은 그대로 남긴다 — 디버그 리포트가 읽는 자리다.
                    onEngineMessage(block.message)
                    // ⚠️ **그리고 화면에도 알린다.** 위 한 줄만 있던 시절, 막힌 사유는 앱 어디에도
                    // 렌더되지 않아 사용자에게는 버튼이 그냥 죽은 것으로 보였다.
                    onBenchmarkUiState(currentBenchmarkUiState().blockedBy(block.reason))
                },
                onBenchmarkUiState = { state -> onBenchmarkUiState(state) },
                onDisplayPlan = { plan -> onDisplayPlan(plan) },
                onProgress = { progress, displayPlan ->
                    launchUiEffect(scope) {
                        onBenchmarkUiState(currentBenchmarkUiState().updateProgress(progress))
                        onDisplayPlan(displayPlan)
                    }
                    Unit
                },
            ),
        )
    }

    fun showResult() {
        store.load()?.let { profile ->
            onBenchmarkUiState(currentBenchmarkUiState().showResult(profile))
            return
        }
        launchUiEffect(scope) { run() }
    }

    fun rerun() {
        onBenchmarkUiState(currentBenchmarkUiState().clearResult())
        launchUiEffect(scope) { run() }
    }
}
