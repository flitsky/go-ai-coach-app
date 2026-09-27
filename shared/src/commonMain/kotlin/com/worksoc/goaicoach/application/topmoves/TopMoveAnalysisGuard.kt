package com.worksoc.goaicoach.application.topmoves

import com.worksoc.goaicoach.application.contract.AnalysisCacheKey
import com.worksoc.goaicoach.application.contract.TopMoveAnalysisPlan
import com.worksoc.goaicoach.application.session.TopMoveAnalysisFailureDisplayPlan
import com.worksoc.goaicoach.application.session.TopMoveAnalysisUpdate
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.policy.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.EngineOperationResultGuard
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.policy.engineOperationRequest
import com.worksoc.goaicoach.shared.policy.evaluateEngineOperationResultGuard

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

fun evaluateTopMoveAnalysisResultGuard(
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

fun buildTopMoveAnalysisSuccessCompletionPlan(
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

fun buildTopMoveAnalysisFailureDisplayPlan(
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

fun buildTopMoveAnalysisFailureCompletionPlan(
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
    deep: Boolean,
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

        is TopMoveAnalysisWorkflowResult.Busy ->
            buildTopMoveAnalysisBusyCompletionPlan(
                token = token,
                currentState = currentState,
                currentAnalysisKey = currentAnalysisKey,
                currentSessionGeneration = currentSessionGeneration,
                targetState = targetState,
                deep = deep,
            )
    }

/**
 * 엔진이 바빠 분석이 포기했다(refactor backlog #15). 그사이 국면·세대·분석 키가 그대로면 미룬다 — 실패 문구를 띄우거나
 * 지난 분석을 지우지 않는다(사용자에게는 예전의 "잠시 뒤" 흐름과 같다). 그사이 바뀌었으면 버린다 — 새 국면에는 제 요청이
 * 따로 걸린다.
 */
internal fun buildTopMoveAnalysisBusyCompletionPlan(
    token: TopMoveAnalysisOperationToken,
    currentState: GameState,
    currentAnalysisKey: AnalysisCacheKey?,
    currentSessionGeneration: Long,
    targetState: GameState,
    deep: Boolean,
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
            TopMoveAnalysisCompletionPlan.Defer(
                targetState = targetState,
                deep = deep,
                analysisKey = token.analysisKey,
            )

        is EngineOperationResultGuard.Discard ->
            TopMoveAnalysisCompletionPlan.Discard(guard)
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

        is TopMoveAnalysisCompletionPlan.Defer ->
            TopMoveAnalysisCompletionApplyPlan.Defer(targetState = targetState, deep = deep)
    }
