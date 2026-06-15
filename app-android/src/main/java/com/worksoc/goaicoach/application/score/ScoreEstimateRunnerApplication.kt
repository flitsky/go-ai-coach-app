package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.ScoreSnapshot

internal data class ScoreEstimateEffectLaunchRequest(
    val effect: GameSessionEffect.RunScoreEstimate,
    val previousSnapshots: List<ScoreSnapshot>,
    val token: ScoreEstimateOperationToken,
    val currentState: GameState,
    val currentSessionGeneration: Long,
)

internal data class ScoreEstimateRunRequest(
    val engineClient: EngineSessionClient,
    val state: GameState,
    val previousSnapshots: List<ScoreSnapshot>,
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    val matchMode: MatchMode,
    val engineProfile: EngineProfile,
    val sessionGeneration: Long,
    val diagnosticEventLog: DiagnosticEventLogPort,
    val currentState: () -> GameState,
    val currentSessionGeneration: () -> Long,
    val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    val runEngineWork: suspend (suspend () -> ScoreEstimateCompletionApplyPlan) -> ScoreEstimateCompletionApplyPlan =
        { block -> runEngineIo { block() } },
    val applyLaunchUpdate: (ScoreEstimateLaunchStateUpdate) -> Unit,
    val applyCompletion: (ScoreEstimateCompletionApplyPlan) -> Unit,
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

internal fun runScoreEstimateApplication(request: ScoreEstimateRunRequest) {
    val requestPlan = buildScoreEstimateRequestPlan(
        state = request.state,
        previousSnapshots = request.previousSnapshots,
        isEngineReady = request.isEngineReady,
        isEngineBusy = request.isEngineBusy,
        matchMode = request.matchMode,
        engineProfile = request.engineProfile,
    )
    val launchUpdate = requestPlan.toScoreEstimateLaunchStateUpdate()
    request.applyLaunchUpdate(launchUpdate)
    val effect = launchUpdate.effect ?: return
    val token = scoreEstimateOperationToken(
        effect.request,
        sessionGeneration = request.sessionGeneration,
    )
    request.launchEngineOperation(token.operation) {
        val completion = request.runEngineWork {
            request.engineClient.runScoreEstimateEffectApplyPlan(
                request = ScoreEstimateEffectLaunchRequest(
                    effect = effect,
                    previousSnapshots = request.previousSnapshots,
                    token = token,
                    currentState = request.currentState(),
                    currentSessionGeneration = request.currentSessionGeneration(),
                ),
                diagnosticEventLog = request.diagnosticEventLog,
            )
        }
        request.applyCompletion(completion)
    }
}
