package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot

internal data class ScoringRuleSyncEffectLaunchRequest(
    val state: GameState,
    val profile: EngineProfile,
    val previousSnapshots: List<ScoreSnapshot>,
    val engineMessage: String,
    val operation: EngineOperationRequest,
    val currentState: GameState,
    val currentSessionGeneration: Long,
    val followUpAnalysisState: GameState,
    val fallbackMessage: String,
)

internal data class PostUndoScoreSyncEffectLaunchRequest(
    val state: GameState,
    val profile: EngineProfile,
    val previousSnapshots: List<ScoreSnapshot>,
    val engineMessage: String,
    val operation: EngineOperationRequest,
    val currentState: GameState,
    val currentSessionGeneration: Long,
    val followUpAnalysisState: GameState,
    val fallbackMessage: String,
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

internal suspend fun EngineSessionClient.runScoringRuleSyncCompletionPlan(
    request: ScoringRuleSyncEffectLaunchRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreSyncCompletionPlan =
    runScoreSyncWorkflowCompletionPlan(
        operation = request.operation,
        currentState = request.currentState,
        currentSessionGeneration = request.currentSessionGeneration,
        followUpAnalysisState = request.followUpAnalysisState,
        fallbackMessage = request.fallbackMessage,
    ) {
        runScoringRuleSyncDisplayPlan(
            state = request.state,
            profile = request.profile,
            previousSnapshots = request.previousSnapshots,
            engineMessage = request.engineMessage,
            operationRequest = request.operation,
            diagnosticEventLog = diagnosticEventLog,
        )
    }

internal suspend fun EngineSessionClient.runScoringRuleSyncApplyPlan(
    request: ScoringRuleSyncEffectLaunchRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreSyncCompletionApplyPlan =
    runScoringRuleSyncCompletionPlan(
        request = request,
        diagnosticEventLog = diagnosticEventLog,
    ).toApplyPlan()

internal suspend fun EngineSessionClient.runPostUndoScoreSyncCompletionPlan(
    request: PostUndoScoreSyncEffectLaunchRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreSyncCompletionPlan =
    runScoreSyncWorkflowCompletionPlan(
        operation = request.operation,
        currentState = request.currentState,
        currentSessionGeneration = request.currentSessionGeneration,
        followUpAnalysisState = request.followUpAnalysisState,
        fallbackMessage = request.fallbackMessage,
    ) {
        runScoringRuleSyncDisplayPlan(
            state = request.state,
            profile = request.profile,
            previousSnapshots = request.previousSnapshots,
            engineMessage = request.engineMessage,
            operationRequest = request.operation,
            diagnosticEventLog = diagnosticEventLog,
        )
    }

internal suspend fun EngineSessionClient.runPostUndoScoreSyncApplyPlan(
    request: PostUndoScoreSyncEffectLaunchRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreSyncCompletionApplyPlan =
    runPostUndoScoreSyncCompletionPlan(
        request = request,
        diagnosticEventLog = diagnosticEventLog,
    ).toApplyPlan()

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

internal data class RestoredGameSyncEffectLaunchRequest(
    val effect: GameSessionEffect.SyncRestoredGame,
    val context: RestoredGameSyncExecutionContext,
    val operation: EngineOperationRequest,
    val currentState: GameState,
    val currentSessionGeneration: Long,
    val followUpAnalysisState: GameState,
    val fallbackMessage: String,
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

internal suspend fun EngineSessionClient.runRestoredGameSyncCompletionPlan(
    request: RestoredGameSyncEffectLaunchRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreSyncCompletionPlan =
    runScoreSyncWorkflowCompletionPlan(
        operation = request.operation,
        currentState = request.currentState,
        currentSessionGeneration = request.currentSessionGeneration,
        followUpAnalysisState = request.followUpAnalysisState,
        fallbackMessage = request.fallbackMessage,
    ) {
        runRestoredGameSyncEffect(
            effect = request.effect,
            context = request.context,
            operationRequest = request.operation,
            diagnosticEventLog = diagnosticEventLog,
        )
    }

internal suspend fun EngineSessionClient.runRestoredGameSyncApplyPlan(
    request: RestoredGameSyncEffectLaunchRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreSyncCompletionApplyPlan =
    runRestoredGameSyncCompletionPlan(
        request = request,
        diagnosticEventLog = diagnosticEventLog,
    ).toApplyPlan()
