package com.worksoc.goaicoach.application.cacheoptimization

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationResult
import com.worksoc.goaicoach.application.contract.GameSessionEffect
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.application.engine.EngineAnalysisClient
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest

internal sealed class PositionAnalysisCacheOptimizationWorkflowResult {
    data class Success(
        val result: PositionAnalysisCacheOptimizationResult,
    ) : PositionAnalysisCacheOptimizationWorkflowResult()

    data class Failure(
        val error: Throwable,
    ) : PositionAnalysisCacheOptimizationWorkflowResult()
}

internal suspend fun EngineAnalysisClient.runPositionAnalysisCacheOptimizationEffect(
    effect: GameSessionEffect.RunPositionCacheOptimization,
    operationRequest: EngineOperationRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): PositionAnalysisCacheOptimizationResult =
    runObservedEngineOperation(
        request = operationRequest,
        diagnosticEventLog = diagnosticEventLog,
    ) {
        optimizePositionAnalysisCache(effect.plan)
    }

internal suspend fun EngineAnalysisClient.runPositionAnalysisCacheOptimizationWorkflowResult(
    effect: GameSessionEffect.RunPositionCacheOptimization,
    operationRequest: EngineOperationRequest,
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
