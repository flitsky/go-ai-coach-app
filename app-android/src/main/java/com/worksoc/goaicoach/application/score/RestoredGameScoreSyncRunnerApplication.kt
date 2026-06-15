package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.engine.engineOperationRequest

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

internal data class RestoredGameSyncRunRequest(
    val engineClient: EngineSessionClient,
    val state: GameState,
    val profile: EngineProfile,
    val sessionGeneration: Long,
    val timeoutPolicy: EngineTimeoutPolicy,
    val diagnosticEventLog: DiagnosticEventLogPort,
    val currentState: () -> GameState,
    val currentSessionGeneration: () -> Long,
    val runEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    val runEngineWork: suspend (suspend () -> ScoreSyncCompletionApplyPlan) -> ScoreSyncCompletionApplyPlan =
        { block -> runEngineIo { block() } },
    val applyCompletion: (ScoreSyncCompletionApplyPlan) -> GameState?,
    val requestFollowUpAnalysis: (GameState) -> Unit,
    val fallbackMessage: String = "Saved game restored locally, but engine sync failed.",
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

internal fun runRestoredGameSyncApplication(request: RestoredGameSyncRunRequest) {
    val operation = engineOperationRequest(
        kind = EngineOperationKind.RestoredGameSync,
        state = request.state,
        sessionGeneration = request.sessionGeneration,
        timeoutPolicy = request.timeoutPolicy,
        fallbackPolicy = EngineFallbackPolicy.LocalRules,
    )
    request.runEngineOperation(operation) {
        val followUpAnalysisState = request.applyCompletion(
            request.runEngineWork {
                request.engineClient.runRestoredGameSyncApplyPlan(
                    request = RestoredGameSyncEffectLaunchRequest(
                        effect = GameSessionEffect.SyncRestoredGame(request.state),
                        context = RestoredGameSyncExecutionContext(
                            profile = request.profile,
                        ),
                        operation = operation,
                        currentState = request.currentState(),
                        currentSessionGeneration = request.currentSessionGeneration(),
                        followUpAnalysisState = request.state,
                        fallbackMessage = request.fallbackMessage,
                    ),
                    diagnosticEventLog = request.diagnosticEventLog,
                )
            },
        )
        followUpAnalysisState?.let(request.requestFollowUpAnalysis)
    }
}
