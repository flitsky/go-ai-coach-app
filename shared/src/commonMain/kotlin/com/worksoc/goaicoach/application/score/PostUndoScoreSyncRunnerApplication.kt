package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy

data class PostUndoScoreSyncEffectLaunchRequest(
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

data class PostUndoScoreSyncRunRequest(
    val engineClient: EngineSessionClient,
    val state: GameState,
    val profile: EngineProfile,
    val previousSnapshots: List<ScoreSnapshot>,
    val sessionGeneration: Long,
    val timeoutPolicy: EngineTimeoutPolicy,
    val diagnosticEventLog: DiagnosticEventLogPort,
    val currentState: () -> GameState,
    val currentSessionGeneration: () -> Long,
    val runEngineOperation: suspend (EngineOperationRequest, suspend () -> Unit) -> Unit,
    val runEngineWork: suspend (suspend () -> ScoreSyncCompletionApplyPlan) -> ScoreSyncCompletionApplyPlan =
        { block -> runEngineIo { block() } },
    val applyCompletion: (ScoreSyncCompletionApplyPlan) -> GameState?,
    val requestFollowUpAnalysis: (GameState) -> Unit,
    val engineMessage: String = "Local undo settled; engine analysis synced.",
    val fallbackMessage: String = "Local undo engine sync failed.",
)

suspend fun EngineSessionClient.runPostUndoScoreSyncCompletionPlan(
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

suspend fun runPostUndoScoreSyncApplication(request: PostUndoScoreSyncRunRequest) {
    ScoreSyncFlow(
        kind = EngineOperationKind.PostUndoSync,
        state = request.state,
        sessionGeneration = request.sessionGeneration,
        timeoutPolicy = request.timeoutPolicy,
        currentState = request.currentState,
        currentSessionGeneration = request.currentSessionGeneration,
        runEngineWork = request.runEngineWork,
        applyCompletion = request.applyCompletion,
        requestFollowUpAnalysis = request.requestFollowUpAnalysis,
    ) { operation, currentState, currentSessionGeneration ->
        request.engineClient.runPostUndoScoreSyncApplyPlan(
            request = PostUndoScoreSyncEffectLaunchRequest(
                state = request.state,
                profile = request.profile,
                previousSnapshots = request.previousSnapshots,
                engineMessage = request.engineMessage,
                operation = operation,
                currentState = currentState,
                currentSessionGeneration = currentSessionGeneration,
                followUpAnalysisState = request.state,
                fallbackMessage = request.fallbackMessage,
            ),
            diagnosticEventLog = request.diagnosticEventLog,
        )
    }.runFollowingUpAfter(request.runEngineOperation)
}
