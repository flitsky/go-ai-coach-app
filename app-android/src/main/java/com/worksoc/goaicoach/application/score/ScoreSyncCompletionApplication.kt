package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.engine.operation.EngineOperationApplyPlan
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.engine.operation.buildEngineOperationApplyPlan
import com.worksoc.goaicoach.shared.GameState

internal sealed class ScoreSyncCompletionPlan {
    data class ApplySuccess(
        val display: ScoreEstimateDisplayPlan,
        val followUpAnalysisState: GameState,
    ) : ScoreSyncCompletionPlan()

    data class ApplyFailure(
        val engineMessage: String,
        val followUpAnalysisState: GameState,
    ) : ScoreSyncCompletionPlan()

    data class Discard(
        val discard: EngineOperationResultGuard.Discard,
    ) : ScoreSyncCompletionPlan()
}

internal sealed class ScoreSyncCompletionApplyPlan {
    data class ApplySuccess(
        val display: ScoreEstimateDisplayPlan,
        val followUpAnalysisState: GameState,
    ) : ScoreSyncCompletionApplyPlan()

    data class ApplyFailure(
        val engineMessage: String,
        val followUpAnalysisState: GameState,
    ) : ScoreSyncCompletionApplyPlan()

    data class Discard(
        val discard: EngineOperationResultGuard.Discard,
    ) : ScoreSyncCompletionApplyPlan()
}

internal data class ScoreSyncCompletionRequest(
    val operation: EngineOperationRequest,
    val currentState: GameState,
    val currentSessionGeneration: Long,
    val followUpAnalysisState: GameState,
)

internal fun ScoreSyncCompletionPlan.toApplyPlan(): ScoreSyncCompletionApplyPlan =
    when (this) {
        is ScoreSyncCompletionPlan.ApplySuccess ->
            ScoreSyncCompletionApplyPlan.ApplySuccess(
                display = display,
                followUpAnalysisState = followUpAnalysisState,
            )

        is ScoreSyncCompletionPlan.ApplyFailure ->
            ScoreSyncCompletionApplyPlan.ApplyFailure(
                engineMessage = engineMessage,
                followUpAnalysisState = followUpAnalysisState,
            )

        is ScoreSyncCompletionPlan.Discard ->
            ScoreSyncCompletionApplyPlan.Discard(discard)
    }

internal fun buildScoreSyncSuccessCompletionPlan(
    request: ScoreSyncCompletionRequest,
    display: ScoreEstimateDisplayPlan,
): ScoreSyncCompletionPlan =
    buildScoreSyncSuccessCompletionPlan(
        operation = request.operation,
        currentState = request.currentState,
        currentSessionGeneration = request.currentSessionGeneration,
        display = display,
        followUpAnalysisState = request.followUpAnalysisState,
    )

internal fun buildScoreSyncSuccessCompletionPlan(
    operation: EngineOperationRequest,
    currentState: GameState,
    currentSessionGeneration: Long,
    display: ScoreEstimateDisplayPlan,
    followUpAnalysisState: GameState,
): ScoreSyncCompletionPlan =
    when (
        val applyPlan = buildEngineOperationApplyPlan(
            request = operation,
            currentState = currentState,
            currentSessionGeneration = currentSessionGeneration,
        )
    ) {
        EngineOperationApplyPlan.Apply ->
            ScoreSyncCompletionPlan.ApplySuccess(
                display = display,
                followUpAnalysisState = followUpAnalysisState,
            )

        is EngineOperationApplyPlan.Discard ->
            ScoreSyncCompletionPlan.Discard(applyPlan.discard)
    }

internal fun buildScoreSyncFailureCompletionPlan(
    request: ScoreSyncCompletionRequest,
    error: Throwable,
    fallbackMessage: String,
): ScoreSyncCompletionPlan =
    buildScoreSyncFailureCompletionPlan(
        operation = request.operation,
        currentState = request.currentState,
        currentSessionGeneration = request.currentSessionGeneration,
        error = error,
        fallbackMessage = fallbackMessage,
        followUpAnalysisState = request.followUpAnalysisState,
    )

internal fun buildScoreSyncFailureCompletionPlan(
    operation: EngineOperationRequest,
    currentState: GameState,
    currentSessionGeneration: Long,
    error: Throwable,
    fallbackMessage: String,
    followUpAnalysisState: GameState,
): ScoreSyncCompletionPlan =
    when (
        val applyPlan = buildEngineOperationApplyPlan(
            request = operation,
            currentState = currentState,
            currentSessionGeneration = currentSessionGeneration,
        )
    ) {
        EngineOperationApplyPlan.Apply ->
            ScoreSyncCompletionPlan.ApplyFailure(
                engineMessage = error.message ?: fallbackMessage,
                followUpAnalysisState = followUpAnalysisState,
            )

        is EngineOperationApplyPlan.Discard ->
            ScoreSyncCompletionPlan.Discard(applyPlan.discard)
    }

internal suspend fun runScoreSyncWorkflowCompletionPlan(
    operation: EngineOperationRequest,
    currentState: GameState,
    currentSessionGeneration: Long,
    followUpAnalysisState: GameState,
    fallbackMessage: String,
    runDisplay: suspend () -> ScoreEstimateDisplayPlan,
): ScoreSyncCompletionPlan =
    runCatching {
        runDisplay()
    }.fold(
        onSuccess = { display ->
            buildScoreSyncSuccessCompletionPlan(
                operation = operation,
                currentState = currentState,
                currentSessionGeneration = currentSessionGeneration,
                display = display,
                followUpAnalysisState = followUpAnalysisState,
            )
        },
        onFailure = { error ->
            buildScoreSyncFailureCompletionPlan(
                operation = operation,
                currentState = currentState,
                currentSessionGeneration = currentSessionGeneration,
                error = error,
                fallbackMessage = fallbackMessage,
                followUpAnalysisState = followUpAnalysisState,
            )
        },
    )
