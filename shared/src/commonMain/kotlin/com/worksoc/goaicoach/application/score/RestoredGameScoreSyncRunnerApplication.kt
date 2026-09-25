package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.contract.GameSessionEffect
import com.worksoc.goaicoach.application.contract.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.application.engine.EngineScoringClient
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot

suspend fun EngineScoringClient.runRestoredGameSyncDisplayPlan(
    state: GameState,
    profile: EngineProfile,
    operationRequest: EngineOperationRequest,
    scoreSnapshots: List<ScoreSnapshot> = emptyList(),
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreEstimateDisplayPlan {
    val estimate = runObservedEngineOperation(
        request = operationRequest,
        diagnosticEventLog = diagnosticEventLog,
    ) {
        configureSyncAndEstimateGraphScore(state, profile)
    }
    return buildEngineEstimateDisplayPlan(
        state = state,
        estimate = estimate,
        previousSnapshots = scoreSnapshots,
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
    val scoreSnapshots: List<ScoreSnapshot>,
    val fallbackMessage: String,
)

data class RestoredGameSyncRunRequest(
    val engineClient: EngineScoringClient,
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
    val scoreSnapshots: List<ScoreSnapshot>,
    val fallbackMessage: String = "Saved game restored locally, but engine sync failed.",
)

internal suspend fun EngineScoringClient.runRestoredGameSyncEffect(
    effect: GameSessionEffect.SyncRestoredGame,
    context: RestoredGameSyncExecutionContext,
    operationRequest: EngineOperationRequest,
    scoreSnapshots: List<ScoreSnapshot> = emptyList(),
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreEstimateDisplayPlan =
    runRestoredGameSyncDisplayPlan(
        state = effect.gameState,
        profile = context.profile,
        operationRequest = operationRequest,
        scoreSnapshots = scoreSnapshots,
        diagnosticEventLog = diagnosticEventLog,
    )

internal suspend fun EngineScoringClient.runRestoredGameSyncCompletionPlan(
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
            scoreSnapshots = request.scoreSnapshots,
            diagnosticEventLog = diagnosticEventLog,
        )
    }

internal suspend fun EngineScoringClient.runRestoredGameSyncApplyPlan(
    request: RestoredGameSyncEffectLaunchRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreSyncCompletionApplyPlan =
    runRestoredGameSyncCompletionPlan(
        request = request,
        diagnosticEventLog = diagnosticEventLog,
    ).toApplyPlan()

fun runRestoredGameSyncApplication(request: RestoredGameSyncRunRequest) {
    ScoreSyncFlow(
        kind = EngineOperationKind.RestoredGameSync,
        state = request.state,
        sessionGeneration = request.sessionGeneration,
        timeoutPolicy = request.timeoutPolicy,
        currentState = request.currentState,
        currentSessionGeneration = request.currentSessionGeneration,
        runEngineWork = request.runEngineWork,
        applyCompletion = request.applyCompletion,
        requestFollowUpAnalysis = request.requestFollowUpAnalysis,
    ) { operation, currentState, currentSessionGeneration ->
        request.engineClient.runRestoredGameSyncApplyPlan(
            request = RestoredGameSyncEffectLaunchRequest(
                effect = GameSessionEffect.SyncRestoredGame(request.state),
                context = RestoredGameSyncExecutionContext(
                    profile = request.profile,
                ),
                operation = operation,
                currentState = currentState,
                currentSessionGeneration = currentSessionGeneration,
                followUpAnalysisState = request.state,
                scoreSnapshots = request.scoreSnapshots,
                fallbackMessage = request.fallbackMessage,
            ),
            diagnosticEventLog = request.diagnosticEventLog,
        )
    }.launchFollowingUpInside(request.runEngineOperation)
}
