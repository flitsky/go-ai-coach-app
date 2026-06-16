package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest

internal class ScoreEstimateController(
    private val engineClient: EngineSessionClient,
    private val diagnosticEventLog: DiagnosticEventLogPort,
    private val currentGameState: () -> GameState,
    private val currentScoreSnapshots: () -> List<ScoreSnapshot>,
    private val isEngineReady: () -> Boolean,
    private val isEngineBusy: () -> Boolean,
    private val currentMatchMode: () -> MatchMode,
    private val currentEngineProfile: () -> EngineProfile,
    private val currentSessionGeneration: () -> Long,
    private val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    private val onEngineMessage: (String) -> Unit,
    private val onScoreEstimateDisplayPlan: (ScoreEstimateDisplayPlan) -> Unit,
    private val onScoreEstimateFailureDisplayPlan: (ScoreEstimateFailureDisplayPlan) -> Unit,
    private val appendDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
) {
    fun request() {
        runScoreEstimateApplication(
            ScoreEstimateRunRequest(
                engineClient = engineClient,
                state = currentGameState(),
                previousSnapshots = currentScoreSnapshots(),
                isEngineReady = isEngineReady(),
                isEngineBusy = isEngineBusy(),
                matchMode = currentMatchMode(),
                engineProfile = currentEngineProfile(),
                sessionGeneration = currentSessionGeneration(),
                diagnosticEventLog = diagnosticEventLog,
                currentState = currentGameState,
                currentSessionGeneration = currentSessionGeneration,
                launchEngineOperation = launchEngineOperation,
                applyLaunchUpdate = { launch ->
                    launch.engineMessage?.let(onEngineMessage)
                    launch.display?.let(onScoreEstimateDisplayPlan)
                },
                applyCompletion = ::applyCompletion,
            ),
        )
    }

    fun applyCompletion(applyPlan: ScoreEstimateCompletionApplyPlan) {
        when (applyPlan) {
            is ScoreEstimateCompletionApplyPlan.ApplySuccess ->
                onScoreEstimateDisplayPlan(applyPlan.display)

            is ScoreEstimateCompletionApplyPlan.ApplyFailure ->
                onScoreEstimateFailureDisplayPlan(applyPlan.failure)

            is ScoreEstimateCompletionApplyPlan.Discard ->
                appendDiscardLog(applyPlan.discard)
        }
    }
}
