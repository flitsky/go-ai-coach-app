package com.worksoc.goaicoach.application.analysis

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.engine.engineOperationRequest

internal data class PositionAnalysisCacheOptimizationRunRequest(
    val plan: PositionAnalysisCacheOptimizationPlan,
    val uiState: PositionAnalysisCacheOptimizationUiState,
    val isEngineBusy: Boolean,
    val sessionGeneration: Long,
    val engineClient: EngineSessionClient,
    val diagnosticEventLog: DiagnosticEventLogPort,
    val currentUiStateProvider: () -> PositionAnalysisCacheOptimizationUiState,
    val applyUiState: (PositionAnalysisCacheOptimizationUiState) -> Unit,
    val applyEngineMessage: (String) -> Unit,
    val applyCandidateText: (String) -> Unit,
    val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    val runEngineWork: suspend (
        suspend () -> PositionAnalysisCacheOptimizationWorkflowResult,
    ) -> PositionAnalysisCacheOptimizationWorkflowResult = { block -> runEngineIo { block() } },
)

internal fun runPositionAnalysisCacheOptimizationApplication(
    request: PositionAnalysisCacheOptimizationRunRequest,
) {
    val acceptedState = request.uiState.accept(request.plan)
    request.applyUiState(acceptedState)
    if (request.plan.isEmpty || request.isEngineBusy || request.uiState.isRunning) {
        return
    }

    request.applyUiState(acceptedState.startRunning())
    val operation = positionAnalysisCacheOptimizationOperationRequest(
        plan = request.plan,
        sessionGeneration = request.sessionGeneration,
    )
    request.applyEngineMessage(
        "Post-game cache optimization started: ${request.plan.targets.size} JSON position(s).",
    )
    val effect = GameSessionEffect.RunPositionCacheOptimization(request.plan)
    request.launchEngineOperation(operation) {
        when (
            val result = request.runEngineWork {
                request.engineClient.runPositionAnalysisCacheOptimizationWorkflowResult(
                    effect = effect,
                    operationRequest = operation,
                    diagnosticEventLog = request.diagnosticEventLog,
                )
            }
        ) {
            is PositionAnalysisCacheOptimizationWorkflowResult.Success -> {
                val message = result.result.messageText()
                request.applyEngineMessage(message)
                request.applyCandidateText(message)
            }

            is PositionAnalysisCacheOptimizationWorkflowResult.Failure ->
                request.applyEngineMessage(
                    result.error.message ?: "Post-game cache optimization failed.",
                )
        }
        request.applyUiState(request.currentUiStateProvider().finishRunning())
    }
}

internal fun positionAnalysisCacheOptimizationOperationRequest(
    plan: PositionAnalysisCacheOptimizationPlan,
    sessionGeneration: Long,
): EngineOperationRequest =
    engineOperationRequest(
        kind = EngineOperationKind.PositionCacheOptimization,
        state = plan.finalState,
        sessionGeneration = sessionGeneration,
        timeoutPolicy = EngineTimeoutPolicy(label = "position-cache-optimization"),
        fallbackPolicy = EngineFallbackPolicy.CachedAnalysis,
    )
