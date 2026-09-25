package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot

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

data class ScoringRuleSyncRunRequest(
    val engineClient: EngineSessionClient,
    val state: GameState,
    val profile: EngineProfile,
    val previousSnapshots: List<ScoreSnapshot>,
    val sessionGeneration: Long,
    val timeoutPolicy: EngineTimeoutPolicy,
    val diagnosticEventLog: DiagnosticEventLogPort,
    val engineMessage: String,
    val currentState: () -> GameState,
    val currentSessionGeneration: () -> Long,
    val runEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    val runEngineWork: suspend (suspend () -> ScoreSyncCompletionApplyPlan) -> ScoreSyncCompletionApplyPlan =
        { block -> runEngineIo { block() } },
    val applyCompletion: (ScoreSyncCompletionApplyPlan) -> GameState?,
    val requestFollowUpAnalysis: (GameState) -> Unit,
    val fallbackMessage: String = "Scoring rule changed, but engine rule sync failed.",
)

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

fun runScoringRuleSyncApplication(request: ScoringRuleSyncRunRequest) {
    ScoreSyncFlow(
        kind = EngineOperationKind.ScoringRuleSync,
        state = request.state,
        sessionGeneration = request.sessionGeneration,
        timeoutPolicy = request.timeoutPolicy,
        currentState = request.currentState,
        currentSessionGeneration = request.currentSessionGeneration,
        runEngineWork = request.runEngineWork,
        applyCompletion = request.applyCompletion,
        requestFollowUpAnalysis = request.requestFollowUpAnalysis,
    ) { operation, currentState, currentSessionGeneration ->
        request.engineClient.runScoringRuleSyncApplyPlan(
            request = ScoringRuleSyncEffectLaunchRequest(
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
    }.launchFollowingUpInside(request.runEngineOperation)
}
