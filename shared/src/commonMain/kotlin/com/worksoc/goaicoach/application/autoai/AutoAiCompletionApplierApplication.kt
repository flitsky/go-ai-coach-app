package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.contract.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.contract.AutoAiTurnEndgamePlan
import com.worksoc.goaicoach.application.contract.AutoAiTurnExecutionContext
import com.worksoc.goaicoach.application.engine.operation.EngineWaitInterruption
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
    /** 이 국면의 진짜 실패를 센다(refactor backlog #109) — 잇달아 나면 선택 팝업으로 넘긴다. 국면은 러너가 채운다. */
    val markTurnFailed: () -> Unit,
    val appendEngineOperationDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
    /** 엔진을 기다리는 사이 앱이 멈췄는가(backlog #204) — 러너가 엔진 호출 바로 뒤에 굳힌 값. */
    val waitInterruption: EngineWaitInterruption,
    /** 이 국면에서 조용한 재시도(backlog #204)를 이미 썼는가 — 시간 초과일 때만 묻는다. */
    val isInterruptedRetrySpent: () -> Boolean,
    /** 이 국면의 조용한 재시도를 쓴다(backlog #204) — 시간 초과 표시 대신. 국면은 러너가 채운다. */
    val markTurnInterrupted: () -> Unit,
)

/**
 * 시간 초과로 끝난 차례를 **팝업 없이 같은 국면으로 한 번 더** 보낼 것인가(backlog #204).
 *
 * 기다리는 사이 앱이 멈췄으면([EngineWaitInterruption.isInterrupted]) 그 마감은 엔진이 아니라 멈춘 시간을 잰 것이다 —
 * 선택 팝업(「엔진 응답 지연」)을 띄우면 오탐이다. 그래도 **국면마다 한 번뿐**이다: 다시 요청한 차례가 또 시간 초과면
 * (또 멈췄더라도) 지금처럼 팝업이다 — 조용한 반복은 없다. 멈추지 않았으면 언제나 팝업이다(refactor backlog #74 그대로).
 */
internal fun shouldRetryTimedOutTurnSilently(
    interruption: EngineWaitInterruption,
    isInterruptedRetrySpent: Boolean,
): Boolean = interruption.isInterrupted && !isInterruptedRetrySpent

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
            val retriesSilently = shouldRetryTimedOutTurnSilently(
                interruption = request.waitInterruption,
                isInterruptedRetrySpent = request.isInterruptedRetrySpent(),
            )
            if (retriesSilently) {
                // 기다리는 사이 앱이 멈췄다(backlog #204) — 표시를 남기지 않는다. busy가 풀리면 트리거 효과가(백그라운드면
                // 복귀의 `onAppForegrounded`가) 같은 국면을 다시 요청한다. 그 한 번을 이 국면에서 쓴다.
                request.markTurnInterrupted()
            } else {
                // 표시를 남겨 조용한 재시도를 막는다 — busy가 풀려 트리거 효과가 다시 돌아도 같은 국면이면 건너뛴다.
                request.markTurnTimedOut()
            }
            request.runtimeEventLog.append(
                runtimeAiTurnTimeoutLog(
                    context = request.runtimeContextProvider(),
                    turnState = request.turnContext.turnState,
                    aiPlayer = request.turnContext.aiPlayer,
                    turnElapsedMs = request.nowMillis() - request.turnStartMillis,
                    error = completion.error,
                    waitInterruption = request.waitInterruption,
                    retriesSilently = retriesSilently,
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
    request.markTurnFailed()
}
