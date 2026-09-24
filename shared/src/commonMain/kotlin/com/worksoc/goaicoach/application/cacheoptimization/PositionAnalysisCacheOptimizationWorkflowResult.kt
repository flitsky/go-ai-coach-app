package com.worksoc.goaicoach.application.cacheoptimization

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationResult
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.shared.policy.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.shared.policy.engineOperationRequest

sealed class PositionAnalysisCacheOptimizationWorkflowResult {
    data class Success(
        val result: PositionAnalysisCacheOptimizationResult,
    ) : PositionAnalysisCacheOptimizationWorkflowResult()

    data class Failure(
        val error: Throwable,
    ) : PositionAnalysisCacheOptimizationWorkflowResult()
}

suspend fun EngineSessionClient.runPositionAnalysisCacheOptimizationEffect(
    effect: GameSessionEffect.RunPositionCacheOptimization,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): PositionAnalysisCacheOptimizationResult =
    runObservedEngineOperation(
        request = operationRequest ?: engineOperationRequest(
            kind = EngineOperationKind.PositionCacheOptimization,
            state = effect.plan.finalState,
            sessionGeneration = 0L,
            timeoutPolicy = EngineTimeoutPolicy(label = "position-cache-optimization"),
            fallbackPolicy = EngineFallbackPolicy.CachedAnalysis,
        ),
        diagnosticEventLog = diagnosticEventLog,
    ) {
        optimizePositionAnalysisCache(effect.plan)
    }

suspend fun EngineSessionClient.runPositionAnalysisCacheOptimizationWorkflowResult(
    effect: GameSessionEffect.RunPositionCacheOptimization,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): PositionAnalysisCacheOptimizationWorkflowResult =
    runCatching {
        runPositionAnalysisCacheOptimizationEffect(
            effect = effect,
            operationRequest = operationRequest,
            diagnosticEventLog = diagnosticEventLog,
        )
    }.fold(
        onSuccess = { result -> PositionAnalysisCacheOptimizationWorkflowResult.Success(result) },
        onFailure = { error -> PositionAnalysisCacheOptimizationWorkflowResult.Failure(error) },
    )
