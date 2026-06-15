package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.runtimeAiTurnEndgameDetectedLog
import com.worksoc.goaicoach.application.runtime.runtimeAiTurnEndgameFailureLog
import com.worksoc.goaicoach.application.runtime.runtimeAiTurnEndgameSuccessLog
import com.worksoc.goaicoach.application.score.EndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot

internal data class AutoAiEndgameRunRequest(
    val endgamePlan: AutoAiTurnEndgamePlan.Resolve,
    val engineClient: EngineSessionClient,
    val previousSnapshotsProvider: () -> List<ScoreSnapshot>,
    val currentStateProvider: () -> GameState,
    val currentSessionGenerationProvider: () -> Long,
    val runtimeContextProvider: () -> RuntimeLogContext,
    val runtimeEventLog: RuntimeEventLogPort,
    val diagnosticEventLog: DiagnosticEventLogPort,
    val markGameEnded: () -> Unit,
    val applyResolvedDisplay: (FinalScoreDisplayPlan) -> Unit,
    val applyFailureDisplay: (EndgameFailureDisplayPlan) -> Unit,
    val appendEngineOperationDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
    val runEngineWork: suspend (suspend () -> AutoAiTurnEndgameDisplayPlan) -> AutoAiTurnEndgameDisplayPlan =
        { block -> runEngineIo { block() } },
)

internal suspend fun runAutoAiEndgameApplication(
    request: AutoAiEndgameRunRequest,
) {
    val endgameOperationToken = autoAiEndgameOperationToken(
        request.endgamePlan,
        sessionGeneration = request.currentSessionGenerationProvider(),
    )
    request.markGameEnded()
    request.runtimeEventLog.append(
        runtimeAiTurnEndgameDetectedLog(
            context = request.runtimeContextProvider(),
            state = request.endgamePlan.state,
        ),
    )
    val endgameDisplay = request.runEngineWork {
        request.engineClient.runAutoAiEndgameDisplayPlan(
            plan = request.endgamePlan,
            previousSnapshots = request.previousSnapshotsProvider(),
            operationRequest = endgameOperationToken.operation,
            diagnosticEventLog = request.diagnosticEventLog,
        )
    }
    when (
        val endgameCompletion = buildAutoAiEndgameCompletionPlan(
            token = endgameOperationToken,
            currentState = request.currentStateProvider(),
            currentSessionGeneration = request.currentSessionGenerationProvider(),
            display = endgameDisplay,
        )
    ) {
        is AutoAiEndgameCompletionPlan.ApplyResolved -> {
            request.runtimeEventLog.append(
                runtimeAiTurnEndgameSuccessLog(
                    context = request.runtimeContextProvider(),
                    state = request.endgamePlan.state,
                    endgame = endgameCompletion.display.resolution,
                ),
            )
            request.applyResolvedDisplay(endgameCompletion.display.display)
        }

        is AutoAiEndgameCompletionPlan.ApplyFailed -> {
            request.runtimeEventLog.append(
                runtimeAiTurnEndgameFailureLog(
                    context = request.runtimeContextProvider(),
                    state = request.endgamePlan.state,
                    error = endgameCompletion.display.error,
                ),
            )
            request.applyFailureDisplay(endgameCompletion.display.display)
        }

        is AutoAiEndgameCompletionPlan.Discard ->
            request.appendEngineOperationDiscardLog(endgameCompletion.discard)
    }
}
