package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.runtimeAiTurnFailureLog
import com.worksoc.goaicoach.application.runtime.runtimeAiTurnSuccessLog
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.shared.StoneColor

internal data class AutoAiTurnCompletionApplyRunRequest(
    val completion: AutoAiTurnCompletionPlan,
    val turnContext: AutoAiTurnExecutionContext,
    val turnStartMillis: Long,
    val runtimeContextProvider: () -> RuntimeLogContext,
    val runtimeEventLog: RuntimeEventLogPort,
    val nowMillis: () -> Long,
    val recordTurnMove: (
        player: StoneColor,
        nowMillis: Long,
        nextPlayer: StoneColor,
    ) -> TurnTimeMoveUpdate,
    val applyTurnTimeUpdate: (TurnTimeMoveUpdate) -> Unit,
    val applyTurnDisplay: (AutoAiTurnDisplayPlan) -> AutoAiTurnFollowUpPlan,
    val resolveEndgame: suspend (AutoAiTurnEndgamePlan.Resolve) -> Unit,
    val applyTurnFailureDisplay: (Throwable) -> Unit,
    val appendEngineOperationDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
)

internal suspend fun applyAutoAiTurnCompletionApplication(
    request: AutoAiTurnCompletionApplyRunRequest,
): AutoAiTurnFollowUpPlan =
    when (val completion = request.completion) {
        is AutoAiTurnCompletionPlan.ApplySuccess ->
            applyAutoAiTurnSuccessCompletionApplication(
                request = request,
                completion = completion,
            )

        is AutoAiTurnCompletionPlan.ApplyFailure -> {
            applyAutoAiTurnFailureCompletionApplication(
                request = request,
                completion = completion,
            )
            AutoAiTurnFollowUpPlan.None
        }

        is AutoAiTurnCompletionPlan.Discard -> {
            request.appendEngineOperationDiscardLog(completion.discard)
            AutoAiTurnFollowUpPlan.None
        }
    }

private suspend fun applyAutoAiTurnSuccessCompletionApplication(
    request: AutoAiTurnCompletionApplyRunRequest,
    completion: AutoAiTurnCompletionPlan.ApplySuccess,
): AutoAiTurnFollowUpPlan {
    val appliedDisplay = completion.display
    val nowMillis = request.nowMillis()
    val turnTimeUpdate = request.recordTurnMove(
        request.turnContext.aiPlayer,
        nowMillis,
        appliedDisplay.gameState.nextPlayer,
    )
    request.runtimeEventLog.append(
        runtimeAiTurnSuccessLog(
            context = request.runtimeContextProvider(),
            turnState = request.turnContext.turnState,
            aiPlayer = request.turnContext.aiPlayer,
            display = appliedDisplay,
            turnElapsedMs = nowMillis - request.turnStartMillis,
            turnTimeUpdate = turnTimeUpdate,
        ),
    )
    request.applyTurnTimeUpdate(turnTimeUpdate)
    val followUpPlan = request.applyTurnDisplay(appliedDisplay)
    when (val endgamePlan = buildAutoAiTurnEndgamePlan(appliedDisplay)) {
        AutoAiTurnEndgamePlan.None -> Unit
        is AutoAiTurnEndgamePlan.Resolve -> request.resolveEndgame(endgamePlan)
    }
    return followUpPlan
}

private fun applyAutoAiTurnFailureCompletionApplication(
    request: AutoAiTurnCompletionApplyRunRequest,
    completion: AutoAiTurnCompletionPlan.ApplyFailure,
) {
    val nowMillis = request.nowMillis()
    request.runtimeEventLog.append(
        runtimeAiTurnFailureLog(
            context = request.runtimeContextProvider(),
            turnState = request.turnContext.turnState,
            aiPlayer = request.turnContext.aiPlayer,
            turnElapsedMs = nowMillis - request.turnStartMillis,
            error = completion.error,
        ),
    )
    request.applyTurnFailureDisplay(completion.error)
}
