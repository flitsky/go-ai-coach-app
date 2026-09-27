package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.contract.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.contract.AutoAiTurnEndgamePlan
import com.worksoc.goaicoach.application.contract.AutoAiTurnExecutionContext
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.EngineOperationResultGuard

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
    /** 시간 초과 표시를 남긴다(refactor backlog #74) — 세대·수순 길이는 러너가 채운다. */
    val markTurnTimedOut: () -> Unit,
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

        is AutoAiTurnCompletionPlan.ApplyTimedOut -> {
            // 실패 문구(「AI turn failed…」)를 띄우지 않는다 — 판은 그대로이고, 선택 팝업이 설명한다(설계 C-12).
            // 표시를 남겨 조용한 재시도를 막는다 — busy가 풀려 트리거 효과가 다시 돌아도 같은 국면이면 건너뛴다.
            request.markTurnTimedOut()
            request.runtimeEventLog.append(
                runtimeAiTurnTimeoutLog(
                    context = request.runtimeContextProvider(),
                    turnState = request.turnContext.turnState,
                    aiPlayer = request.turnContext.aiPlayer,
                    turnElapsedMs = request.nowMillis() - request.turnStartMillis,
                    error = completion.error,
                ),
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
