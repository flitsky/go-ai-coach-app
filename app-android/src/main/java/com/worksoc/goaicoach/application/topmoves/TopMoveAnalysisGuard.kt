package com.worksoc.goaicoach.application.topmoves

import com.worksoc.goaicoach.application.analysis.AnalysisCacheKey
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.engine.operation.evaluateEngineOperationResultGuard
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.engine.engineOperationRequest
import com.worksoc.goaicoach.shared.GameState

internal fun topMoveAnalysisOperationToken(
    targetState: GameState,
    plan: TopMoveAnalysisPlan,
    sessionGeneration: Long = 0L,
): TopMoveAnalysisOperationToken =
    TopMoveAnalysisOperationToken(
        operation = engineOperationRequest(
            kind = EngineOperationKind.TopMoves,
            state = targetState,
            sessionGeneration = sessionGeneration,
            timeoutPolicy = EngineTimeoutPolicy(
                timeoutMillis = plan.analysisLimit.timeMillis,
                label = "${plan.analysisKey.preset.label}:${plan.analysisLimit.visits}v",
            ),
            fallbackPolicy = EngineFallbackPolicy.CachedAnalysis,
        ),
        analysisKey = plan.analysisKey,
    )

internal fun evaluateTopMoveAnalysisResultGuard(
    token: TopMoveAnalysisOperationToken,
    currentState: GameState,
    currentAnalysisKey: AnalysisCacheKey?,
    currentSessionGeneration: Long = 0L,
): EngineOperationResultGuard {
    val positionGuard = evaluateEngineOperationResultGuard(
        request = token.operation,
        currentState = currentState,
        currentSessionGeneration = currentSessionGeneration,
    )
    if (positionGuard is EngineOperationResultGuard.Discard) {
        return positionGuard
    }
    return if (currentAnalysisKey == token.analysisKey) {
        EngineOperationResultGuard.Apply
    } else {
        EngineOperationResultGuard.Discard(
            reason = "top_moves_analysis result is stale: analysis key changed before result arrived.",
            operation = token.operation.kind.code,
            operationId = token.operation.operationId,
            sessionGeneration = token.operation.sessionGeneration,
        )
    }
}

internal fun buildTopMoveAnalysisSuccessCompletionPlan(
    token: TopMoveAnalysisOperationToken,
    currentState: GameState,
    currentAnalysisKey: AnalysisCacheKey?,
    currentSessionGeneration: Long,
    update: TopMoveAnalysisUpdate,
): TopMoveAnalysisCompletionPlan =
    when (
        val guard = evaluateTopMoveAnalysisResultGuard(
            token = token,
            currentState = currentState,
            currentAnalysisKey = currentAnalysisKey,
            currentSessionGeneration = currentSessionGeneration,
        )
    ) {
        EngineOperationResultGuard.Apply ->
            TopMoveAnalysisCompletionPlan.ApplySuccess(
                update = update,
                analysisKey = token.analysisKey,
            )

        is EngineOperationResultGuard.Discard ->
            TopMoveAnalysisCompletionPlan.Discard(guard)
    }

internal fun buildTopMoveAnalysisFailureDisplayPlan(
    targetState: GameState,
    error: Throwable,
    topMovesEnabled: Boolean,
): TopMoveAnalysisFailureDisplayPlan =
    TopMoveAnalysisFailureDisplayPlan(
        targetState = targetState,
        engineMessage = error.message ?: "Top Moves analysis failed.",
        clearDisplayedTopMoves = topMovesEnabled,
        candidateText = "Top Moves analysis failed.".takeIf { topMovesEnabled },
    )

internal fun buildTopMoveAnalysisFailureCompletionPlan(
    token: TopMoveAnalysisOperationToken,
    currentState: GameState,
    currentAnalysisKey: AnalysisCacheKey?,
    currentSessionGeneration: Long,
    targetState: GameState,
    error: Throwable,
    topMovesEnabled: Boolean,
): TopMoveAnalysisCompletionPlan =
    when (
        val guard = evaluateTopMoveAnalysisResultGuard(
            token = token,
            currentState = currentState,
            currentAnalysisKey = currentAnalysisKey,
            currentSessionGeneration = currentSessionGeneration,
        )
    ) {
        EngineOperationResultGuard.Apply ->
            TopMoveAnalysisCompletionPlan.ApplyFailure(
                buildTopMoveAnalysisFailureDisplayPlan(
                    targetState = targetState,
                    error = error,
                    topMovesEnabled = topMovesEnabled,
                ),
            )

        is EngineOperationResultGuard.Discard ->
            TopMoveAnalysisCompletionPlan.Discard(guard)
    }

internal fun buildTopMoveAnalysisCompletionPlan(
    result: TopMoveAnalysisWorkflowResult,
    token: TopMoveAnalysisOperationToken,
    currentState: GameState,
    currentAnalysisKey: AnalysisCacheKey?,
    currentSessionGeneration: Long,
    targetState: GameState,
    topMovesEnabled: Boolean,
): TopMoveAnalysisCompletionPlan =
    when (result) {
        is TopMoveAnalysisWorkflowResult.Success ->
            buildTopMoveAnalysisSuccessCompletionPlan(
                token = token,
                currentState = currentState,
                currentAnalysisKey = currentAnalysisKey,
                currentSessionGeneration = currentSessionGeneration,
                update = result.update,
            )

        is TopMoveAnalysisWorkflowResult.Failure ->
            buildTopMoveAnalysisFailureCompletionPlan(
                token = token,
                currentState = currentState,
                currentAnalysisKey = currentAnalysisKey,
                currentSessionGeneration = currentSessionGeneration,
                targetState = targetState,
                error = result.error,
                topMovesEnabled = topMovesEnabled,
            )
    }

internal fun TopMoveAnalysisCompletionPlan.toApplyPlan(): TopMoveAnalysisCompletionApplyPlan =
    when (this) {
        is TopMoveAnalysisCompletionPlan.ApplySuccess ->
            TopMoveAnalysisCompletionApplyPlan.ApplySuccess(
                update = update,
                analysisKey = analysisKey,
            )

        is TopMoveAnalysisCompletionPlan.ApplyFailure ->
            TopMoveAnalysisCompletionApplyPlan.ApplyFailure(display)

        is TopMoveAnalysisCompletionPlan.Discard ->
            TopMoveAnalysisCompletionApplyPlan.Discard(discard)
    }
