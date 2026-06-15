package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleCallbacks
import com.worksoc.goaicoach.shared.GameState
import kotlinx.coroutines.CoroutineScope

/**
 * Owns the device-benchmark UI flow (startup auto-check, manual show, rerun) so
 * the screen entry point does not wire it inline.
 *
 * State the rest of the screen also reads (the benchmark UI state, engine
 * message) stays in the caller and is reached through the accessors below; only
 * the benchmark-specific orchestration and the "have we auto-checked yet" flag
 * live here.
 */
internal class EngineBenchmarkController(
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
    private val onChecked: () -> Unit,
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
                onBlocked = { message -> onEngineMessage(message) },
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

    suspend fun runStartupCheckIfNeeded(
        hasCompletedStartup: Boolean,
        engineReady: Boolean,
        supportsDeviceBenchmark: Boolean,
        hasCheckedBenchmark: Boolean,
    ) {
        when (
            startupBenchmarkAction(
                hasCompletedStartup = hasCompletedStartup,
                isEngineReady = engineReady,
                supportsDeviceBenchmark = supportsDeviceBenchmark,
                hasCheckedBenchmark = hasCheckedBenchmark,
                hasUsableProfile = {
                    store.hasUsableProfile(
                        samplesPerVisit = EngineBenchmarkDefaultSamplesPerVisit,
                        timeCapMs = EngineBenchmarkDefaultTimeCapMs,
                        measurementVersion = EngineBenchmarkMeasurementVersion,
                        visitsTargets = EngineBenchmarkDefaultVisits,
                    )
                },
            )
        ) {
            StartupBenchmarkAction.Skip -> Unit
            StartupBenchmarkAction.ApplyStoredProfile -> {
                onChecked()
                onBenchmarkUiState(
                    currentBenchmarkUiState().applyStoredProfile(
                        benchmarkText = store.loadText(),
                        profile = store.load(),
                    ),
                )
            }
            StartupBenchmarkAction.RunBenchmark -> {
                onChecked()
                run()
            }
        }
    }
}
