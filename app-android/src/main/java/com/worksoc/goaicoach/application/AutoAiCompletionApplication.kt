package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.GameState

internal data class AutoAiTurnOperationToken(
    val operation: EngineOperationRequest,
)

internal fun autoAiTurnOperationToken(
    runPlan: AutoAiTurnRunPlan,
    sessionGeneration: Long = 0L,
): AutoAiTurnOperationToken =
    AutoAiTurnOperationToken(
        operation = engineOperationRequest(
            kind = EngineOperationKind.AutoAiTurn,
            state = runPlan.context.turnState,
            sessionGeneration = sessionGeneration,
            timeoutPolicy = EngineTimeoutPolicy(
                timeoutMillis = runPlan.context.analysisLimit.timeMillis,
                label = "${runPlan.context.searchMode.name}:${runPlan.context.analysisLimit.visits}v",
            ),
            fallbackPolicy = EngineFallbackPolicy.None,
        ),
    )

internal fun evaluateAutoAiTurnResultGuard(
    token: AutoAiTurnOperationToken,
    currentState: GameState,
    currentSessionGeneration: Long = 0L,
): EngineOperationResultGuard =
    evaluateEngineOperationResultGuard(
        request = token.operation,
        currentState = currentState,
        currentSessionGeneration = currentSessionGeneration,
    )

internal sealed class AutoAiTurnCompletionPlan {
    data class ApplySuccess(
        val display: AutoAiTurnDisplayPlan,
    ) : AutoAiTurnCompletionPlan()

    data class ApplyFailure(
        val error: Throwable,
    ) : AutoAiTurnCompletionPlan()

    data class Discard(
        val discard: EngineOperationResultGuard.Discard,
    ) : AutoAiTurnCompletionPlan()
}

internal sealed class AutoAiTurnWorkflowResult {
    data class Success(
        val display: AutoAiTurnDisplayPlan,
    ) : AutoAiTurnWorkflowResult()

    data class Failure(
        val error: Throwable,
    ) : AutoAiTurnWorkflowResult()
}

internal fun buildAutoAiTurnSuccessCompletionPlan(
    token: AutoAiTurnOperationToken,
    currentState: GameState,
    currentSessionGeneration: Long,
    display: AutoAiTurnDisplayPlan,
): AutoAiTurnCompletionPlan =
    when (
        val guard = evaluateAutoAiTurnResultGuard(
            token = token,
            currentState = currentState,
            currentSessionGeneration = currentSessionGeneration,
        )
    ) {
        EngineOperationResultGuard.Apply ->
            AutoAiTurnCompletionPlan.ApplySuccess(display)

        is EngineOperationResultGuard.Discard ->
            AutoAiTurnCompletionPlan.Discard(guard)
    }

internal fun buildAutoAiTurnFailureCompletionPlan(
    token: AutoAiTurnOperationToken,
    currentState: GameState,
    currentSessionGeneration: Long,
    error: Throwable,
): AutoAiTurnCompletionPlan =
    when (
        val guard = evaluateAutoAiTurnResultGuard(
            token = token,
            currentState = currentState,
            currentSessionGeneration = currentSessionGeneration,
        )
    ) {
        EngineOperationResultGuard.Apply ->
            AutoAiTurnCompletionPlan.ApplyFailure(error)

        is EngineOperationResultGuard.Discard ->
            AutoAiTurnCompletionPlan.Discard(guard)
    }

internal fun buildAutoAiTurnCompletionPlan(
    result: AutoAiTurnWorkflowResult,
    token: AutoAiTurnOperationToken,
    currentState: GameState,
    currentSessionGeneration: Long,
): AutoAiTurnCompletionPlan =
    when (result) {
        is AutoAiTurnWorkflowResult.Success ->
            buildAutoAiTurnSuccessCompletionPlan(
                token = token,
                currentState = currentState,
                currentSessionGeneration = currentSessionGeneration,
                display = result.display,
            )

        is AutoAiTurnWorkflowResult.Failure ->
            buildAutoAiTurnFailureCompletionPlan(
                token = token,
                currentState = currentState,
                currentSessionGeneration = currentSessionGeneration,
                error = result.error,
            )
    }

internal data class AutoAiEndgameOperationToken(
    val operation: EngineOperationRequest,
)

internal fun autoAiEndgameOperationToken(
    plan: AutoAiTurnEndgamePlan.Resolve,
    sessionGeneration: Long = 0L,
): AutoAiEndgameOperationToken =
    AutoAiEndgameOperationToken(
        operation = engineOperationRequest(
            kind = EngineOperationKind.AutoAiEndgame,
            state = plan.state,
            sessionGeneration = sessionGeneration,
            timeoutPolicy = EngineTimeoutPolicy(
                timeoutMillis = plan.profile.analysisLimit.timeMillis,
                label = "endgame:${plan.profile.analysisLimit.visits}v",
            ),
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        ),
    )

internal fun evaluateAutoAiEndgameResultGuard(
    token: AutoAiEndgameOperationToken,
    currentState: GameState,
    currentSessionGeneration: Long = 0L,
): EngineOperationResultGuard =
    evaluateEngineOperationResultGuard(
        request = token.operation,
        currentState = currentState,
        currentSessionGeneration = currentSessionGeneration,
    )

internal sealed class AutoAiEndgameCompletionPlan {
    data class ApplyResolved(
        val display: AutoAiTurnEndgameDisplayPlan.Resolved,
    ) : AutoAiEndgameCompletionPlan()

    data class ApplyFailed(
        val display: AutoAiTurnEndgameDisplayPlan.Failed,
    ) : AutoAiEndgameCompletionPlan()

    data class Discard(
        val discard: EngineOperationResultGuard.Discard,
    ) : AutoAiEndgameCompletionPlan()
}

internal fun buildAutoAiEndgameCompletionPlan(
    token: AutoAiEndgameOperationToken,
    currentState: GameState,
    currentSessionGeneration: Long,
    display: AutoAiTurnEndgameDisplayPlan,
): AutoAiEndgameCompletionPlan =
    when (
        val guard = evaluateAutoAiEndgameResultGuard(
            token = token,
            currentState = currentState,
            currentSessionGeneration = currentSessionGeneration,
        )
    ) {
        EngineOperationResultGuard.Apply ->
            when (display) {
                is AutoAiTurnEndgameDisplayPlan.Resolved ->
                    AutoAiEndgameCompletionPlan.ApplyResolved(display)

                is AutoAiTurnEndgameDisplayPlan.Failed ->
                    AutoAiEndgameCompletionPlan.ApplyFailed(display)
            }

        is EngineOperationResultGuard.Discard ->
            AutoAiEndgameCompletionPlan.Discard(guard)
    }
