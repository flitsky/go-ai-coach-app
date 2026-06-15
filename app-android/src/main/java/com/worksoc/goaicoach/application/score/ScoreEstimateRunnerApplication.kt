package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot

internal data class ScoreEstimateEffectLaunchRequest(
    val effect: GameSessionEffect.RunScoreEstimate,
    val previousSnapshots: List<ScoreSnapshot>,
    val token: ScoreEstimateOperationToken,
    val currentState: GameState,
    val currentSessionGeneration: Long,
)

internal suspend fun EngineSessionClient.runScoreEstimateDisplayPlan(
    request: ScoreEstimateRequestPlan.RequestEngineEstimate,
    previousSnapshots: List<ScoreSnapshot>,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreEstimateDisplayPlan {
    val estimate = runObservedEngineOperation(
        request = operationRequest ?: scoreEstimateOperationToken(request).operation,
        diagnosticEventLog = diagnosticEventLog,
    ) {
        estimateScoreForState(
            state = request.state,
            profile = request.profile,
            syncFirst = request.syncFirst,
        )
    }
    return buildEngineEstimateDisplayPlan(
        state = request.state,
        estimate = estimate,
        previousSnapshots = previousSnapshots,
    )
}

internal suspend fun EngineSessionClient.runScoreEstimateEffect(
    effect: GameSessionEffect.RunScoreEstimate,
    previousSnapshots: List<ScoreSnapshot>,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreEstimateDisplayPlan =
    runScoreEstimateDisplayPlan(
        request = effect.request,
        previousSnapshots = previousSnapshots,
        operationRequest = operationRequest,
        diagnosticEventLog = diagnosticEventLog,
    )

internal suspend fun EngineSessionClient.runScoreEstimateWorkflowResult(
    effect: GameSessionEffect.RunScoreEstimate,
    previousSnapshots: List<ScoreSnapshot>,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreEstimateWorkflowResult =
    runCatching {
        runScoreEstimateEffect(
            effect = effect,
            previousSnapshots = previousSnapshots,
            operationRequest = operationRequest,
            diagnosticEventLog = diagnosticEventLog,
        )
    }.fold(
        onSuccess = { display -> ScoreEstimateWorkflowResult.Success(display) },
        onFailure = { error -> ScoreEstimateWorkflowResult.Failure(error) },
    )

internal suspend fun EngineSessionClient.runScoreEstimateEffectCompletionPlan(
    request: ScoreEstimateEffectLaunchRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreEstimateCompletionPlan =
    buildScoreEstimateCompletionPlan(
        result = runScoreEstimateWorkflowResult(
            effect = request.effect,
            previousSnapshots = request.previousSnapshots,
            operationRequest = request.token.operation,
            diagnosticEventLog = diagnosticEventLog,
        ),
        token = request.token,
        currentState = request.currentState,
        currentSessionGeneration = request.currentSessionGeneration,
    )

internal suspend fun EngineSessionClient.runScoreEstimateEffectApplyPlan(
    request: ScoreEstimateEffectLaunchRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreEstimateCompletionApplyPlan =
    runScoreEstimateEffectCompletionPlan(
        request = request,
        diagnosticEventLog = diagnosticEventLog,
    ).toApplyPlan()
