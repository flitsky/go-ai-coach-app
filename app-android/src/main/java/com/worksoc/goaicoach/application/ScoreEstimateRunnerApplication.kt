package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.EngineProfile
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

internal suspend fun EngineSessionClient.runScoringRuleSyncDisplayPlan(
    state: GameState,
    profile: EngineProfile,
    previousSnapshots: List<ScoreSnapshot>,
    engineMessage: String,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreEstimateDisplayPlan {
    val estimate = runObservedEngineOperation(
        request = operationRequest ?: engineOperationRequest(
            kind = EngineOperationKind.ScoringRuleSync,
            state = state,
            sessionGeneration = 0L,
            timeoutPolicy = EngineTimeoutPolicy(
                timeoutMillis = profile.analysisLimit.timeMillis,
                label = "${profile.difficulty.label}:${profile.analysisLimit.visits}v",
            ),
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        ),
        diagnosticEventLog = diagnosticEventLog,
    ) {
        syncAndEstimateGraphScore(state, profile)
    }
    return buildEngineEstimateDisplayPlan(
        state = state,
        estimate = estimate,
        previousSnapshots = previousSnapshots,
        engineMessage = engineMessage,
        trimAfterMove = true,
    )
}

internal suspend fun EngineSessionClient.runRestoredGameSyncDisplayPlan(
    state: GameState,
    profile: EngineProfile,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreEstimateDisplayPlan {
    val estimate = runObservedEngineOperation(
        request = operationRequest ?: engineOperationRequest(
            kind = EngineOperationKind.RestoredGameSync,
            state = state,
            sessionGeneration = 0L,
            timeoutPolicy = EngineTimeoutPolicy(
                timeoutMillis = profile.analysisLimit.timeMillis,
                label = "${profile.difficulty.label}:${profile.analysisLimit.visits}v",
            ),
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        ),
        diagnosticEventLog = diagnosticEventLog,
    ) {
        configureSyncAndEstimateGraphScore(state, profile)
    }
    return buildEngineEstimateDisplayPlan(
        state = state,
        estimate = estimate,
        previousSnapshots = emptyList(),
        engineMessage = "Previous game restored and engine state synchronized.",
    )
}

internal data class RestoredGameSyncExecutionContext(
    val profile: EngineProfile,
)

internal suspend fun EngineSessionClient.runRestoredGameSyncEffect(
    effect: GameSessionEffect.SyncRestoredGame,
    context: RestoredGameSyncExecutionContext,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreEstimateDisplayPlan =
    runRestoredGameSyncDisplayPlan(
        state = effect.gameState,
        profile = context.profile,
        operationRequest = operationRequest,
        diagnosticEventLog = diagnosticEventLog,
    )
