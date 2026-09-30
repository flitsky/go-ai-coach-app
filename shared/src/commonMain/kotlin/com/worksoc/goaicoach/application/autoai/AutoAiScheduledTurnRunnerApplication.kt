package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.contract.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.contract.AutoAiTurnEndgamePlan
import com.worksoc.goaicoach.application.contract.AutoAiTurnRunPlan
import com.worksoc.goaicoach.application.contract.GameSessionEffect
import com.worksoc.goaicoach.application.contract.GameSessionRuntimeState
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineGamePlayClient
import com.worksoc.goaicoach.application.engine.operation.EngineWaitWatch
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.session.AutoAiTurnTimeout
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineOperationResultGuard
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import kotlinx.coroutines.Job

internal data class AutoAiScheduledTurnRunRequest(
    val schedule: AutoAiTurnRequestPlan.Schedule,
    val controllerStateProvider: () -> GameSessionControllerState,
    val engineClient: EngineGamePlayClient,
    val runtimeStateProvider: () -> GameSessionRuntimeState,
    val searchTimeSettingsProvider: () -> SearchTimeSettings,
    val scoreSnapshotsProvider: () -> List<ScoreSnapshot>,
    val isEngineReady: () -> Boolean,
    val isEngineBusy: () -> Boolean,
    val isGameEnded: () -> Boolean,
    val shouldShowResumePrompt: () -> Boolean,
    val runtimeContextProvider: () -> RuntimeLogContext,
    val runtimeEventLog: RuntimeEventLogPort,
    val diagnosticEventLog: DiagnosticEventLogPort,
    val delayMillis: suspend (Long) -> Unit,
    /** 띄운 Job을 돌려준다 — 호출부가 그것을 맡겨 두었다가 무르기·나가기 등에서 취소한다(refactor backlog #74). */
    val launchAutoAiEffect: (suspend () -> Unit) -> Job,
    val applyScheduled: (AutoAiTurnRequestPlan.Schedule) -> Unit,
    val applyCancelled: (AutoAiTurnScheduleValidationPlan) -> Unit,
    val markEngineOperationStarted: (String) -> Unit,
    val markEngineOperationCompleted: (String) -> Unit,
    val recordTurnMove: (
        player: StoneColor,
        nowMillis: Long,
        nextPlayer: StoneColor,
    ) -> TurnTimeMoveUpdate,
    val applyTurnTimeUpdate: (TurnTimeMoveUpdate) -> Unit,
    val applyTurnDisplay: (AutoAiTurnDisplayPlan) -> AutoAiTurnFollowUpPlan,
    val resolveEndgame: suspend (AutoAiTurnEndgamePlan.Resolve) -> Unit,
    val applyTurnFailureDisplay: (Throwable) -> Unit,
    /** 이번 차례의 탐색이 시간 초과로 끝났다 — 선택을 기다리며 조용한 재시도를 막는 표시를 남긴다(refactor backlog #74). */
    val applyTurnTimedOut: (AutoAiTurnTimeout) -> Unit,
    /** 이번 차례가 진짜로 실패했다 — 그 국면에서 센다. 잇달아 나면 위와 같은 표시가 붙는다(refactor backlog #109). */
    val applyTurnFailed: (AutoAiTurnTimeout) -> Unit,
    /**
     * 엔진을 기다리기 시작한다 — 그 사이 앱이 멈췄는지(포그라운드 세대·멈춤 박동) 재는 관찰을 연다(backlog #204).
     * 배선은 `EngineOperationLifecycleController::startEngineWaitWatch`.
     */
    val startEngineWaitWatch: () -> EngineWaitWatch,
    /**
     * 이번 차례의 시간 초과가 기다리는 사이의 멈춤 때문이라 팝업 대신 같은 국면을 조용히 한 번 다시 요청한다 — 그 국면에서
     * 그 한 번을 쓴다(backlog #204). 표시는 [applyTurnTimedOut]과 **둘 중 하나만** 붙는다.
     */
    val applyTurnInterrupted: (AutoAiTurnTimeout) -> Unit,
    val appendEngineOperationDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
    val completeAutoAiTurnRun: () -> Unit,
    val requestFollowUpAnalysis: (AutoAiTurnFollowUpRequest) -> Unit,
    val currentStateProvider: () -> GameState,
    val currentSessionGenerationProvider: () -> Long,
    val nowMillis: () -> Long = { currentEpochMillis() },
)

/** 예약하고 띄운 AI 차례의 Job을 돌려준다(refactor backlog #74 — 예전에는 버려져 아무도 취소할 수 없었다, 설계 F3). */
internal fun runScheduledAutoAiTurnApplication(
    request: AutoAiScheduledTurnRunRequest,
): Job {
    request.applyScheduled(request.schedule)
    request.runtimeEventLog.append(
        runtimeAiTurnScheduleLog(
            context = request.runtimeContextProvider(),
            gameState = request.controllerStateProvider().gameState,
            delayMillis = request.schedule.delayMillis,
            autoPlayDelaySetting = request.controllerStateProvider().settings.autoPlayDelaySetting,
            isEngineBusy = request.isEngineBusy(),
        ),
    )
    var isBodyEntered = false
    val job = request.launchAutoAiEffect {
        isBodyEntered = true
        // ⚠️ **정리(busy 해제·예약 해제)는 `finally`에서 한다**(refactor backlog #74, 설계 B-6). 이 블록은 취소될
        // 수 있다 — 무르기·나가기·새 대국·이어하기·「엔진 다시 시작하기」가 Job을 취소한다. 정리를 본문 끝에
        // 두면 취소가 그것을 건너뛰어 busy와 예약 표시(pending)가 영원히 남고, **AI가 다시는 두지 않는다.**
        // 취소되면 실패 문구·후속 분석은 없다(그 둘은 `finally` 뒤·완료 적용 안에 있다).
        var startedOperationId: String? = null
        var isPendingSettled = false
        val followUpPlan = try {
            if (request.schedule.delayMillis > 0L) {
                request.delayMillis(request.schedule.delayMillis)
            }

            val turnRunPlan = when (
                val validation = request.controllerStateProvider().toAutoAiTurnScheduleValidationPlan(
                    isEngineReady = request.isEngineReady(),
                    isEngineBusy = request.isEngineBusy(),
                    scheduledDelayMillis = request.schedule.delayMillis,
                )
            ) {
                AutoAiTurnScheduleValidationPlan.Cancel -> {
                    request.runtimeEventLog.append(
                        runtimeAiTurnScheduleCancelledLog(
                            context = request.runtimeContextProvider(),
                            gameState = request.currentStateProvider(),
                            isEngineReady = request.isEngineReady(),
                            isEngineBusy = request.isEngineBusy(),
                            isGameEnded = request.isGameEnded(),
                            shouldShowResumePrompt = request.shouldShowResumePrompt(),
                        ),
                    )
                    request.applyCancelled(validation)
                    isPendingSettled = true
                    return@launchAutoAiEffect
                }

                is AutoAiTurnScheduleValidationPlan.Continue -> validation.runPlan
            }

            val turnContext = turnRunPlan.context
            val turnOperationToken = autoAiTurnOperationToken(
                turnRunPlan,
                sessionGeneration = request.currentSessionGenerationProvider(),
            )
            val turnStartMillis = request.nowMillis()
            request.runtimeEventLog.append(
                runtimeAiTurnBeginLog(
                    context = request.runtimeContextProvider(),
                    turnState = turnContext.turnState,
                    aiPlayer = turnContext.aiPlayer,
                    playLevel = turnContext.playLevel,
                    analysisLimit = turnContext.analysisLimit,
                    searchMode = turnContext.searchMode,
                    delayMillis = turnRunPlan.delayMillis,
                    isolateSearchCache = turnContext.isolateSearchCache,
                ),
            )
            request.markEngineOperationStarted(turnOperationToken.operation.operationId)
            startedOperationId = turnOperationToken.operation.operationId
            // 시간 초과·진짜 실패의 표시가 묶이는 국면 — 이 차례를 요청한 세대·수순 길이(#74, #109).
            val turnPosition = AutoAiTurnTimeout(
                sessionGeneration = turnOperationToken.operation.sessionGeneration,
                moveCount = turnContext.turnState.moves.size,
            )
            // 기다리는 사이 앱이 멈췄는지 잰다(backlog #204) — 엔진 호출이 어떻게 끝나든(취소 포함) 박동을 끈다. 판정은
            // 엔진 호출이 돌아온 **바로 그때**로 굳힌다 — 그 뒤의 전환은 이 기다림의 일이 아니다.
            val waitWatch = request.startEngineWaitWatch()
            val turnCompletion = try {
                runAutoAiTurnEngineCompletion(
                    request = request,
                    turnRunPlan = turnRunPlan,
                    operation = turnOperationToken.operation,
                )
            } finally {
                waitWatch.finish()
            }
            applyAutoAiTurnCompletionApplication(
                AutoAiTurnCompletionApplyRunRequest(
                    completion = turnCompletion,
                    turnContext = turnContext,
                    turnStartMillis = turnStartMillis,
                    runtimeContextProvider = request.runtimeContextProvider,
                    runtimeEventLog = request.runtimeEventLog,
                    nowMillis = request.nowMillis,
                    recordTurnMove = request.recordTurnMove,
                    applyTurnTimeUpdate = request.applyTurnTimeUpdate,
                    applyTurnDisplay = request.applyTurnDisplay,
                    resolveEndgame = request.resolveEndgame,
                    applyTurnFailureDisplay = request.applyTurnFailureDisplay,
                    markTurnTimedOut = { request.applyTurnTimedOut(turnPosition) },
                    markTurnFailed = { request.applyTurnFailed(turnPosition) },
                    appendEngineOperationDiscardLog = request.appendEngineOperationDiscardLog,
                    waitInterruption = waitWatch.finish(),
                    isInterruptedRetrySpent = {
                        request.controllerStateProvider().autoAiTurn.hasSpentInterruptedRetry(turnPosition)
                    },
                    markTurnInterrupted = { request.applyTurnInterrupted(turnPosition) },
                ),
            )
        } finally {
            startedOperationId?.let(request.markEngineOperationCompleted)
            if (!isPendingSettled) {
                request.completeAutoAiTurnRun()
            }
        }
        request.runtimeEventLog.append(
            runtimeAiTurnCompleteLog(
                context = request.runtimeContextProvider(),
                gameState = request.currentStateProvider(),
                isEngineBusy = request.isEngineBusy(),
                isAutoAiTurnPending = request.controllerStateProvider().isAutoAiTurnPending,
            ),
        )
        followUpPlan.toAutoAiTurnFollowUpRequest()
            ?.let(request.requestFollowUpAnalysis)
    }
    // 본문이 한 번도 돌지 못한 채 취소되면(디스패치 전의 취소 — 예약 직후 곧바로 무르기) 위의 `finally`도 없다.
    // 그때는 예약 표시를 여기서 푼다. 본문에 들어갔다면 정리는 본문의 `finally` 몫이다.
    job.invokeOnCompletion {
        if (!isBodyEntered) request.completeAutoAiTurnRun()
    }
    return job
}

private suspend fun runAutoAiTurnEngineCompletion(
    request: AutoAiScheduledTurnRunRequest,
    turnRunPlan: AutoAiTurnRunPlan,
    operation: EngineOperationRequest,
): AutoAiTurnCompletionPlan {
    val runtimeState = request.runtimeStateProvider()
    val turnResult =
        runEngineIo {
            request.engineClient.runAutoAiTurnWorkflowResult(
                effect = GameSessionEffect.RunAutoAiTurn(turnRunPlan),
                executionContext = AutoAiTurnRunExecutionContext(
                    currentProfile = runtimeState.engineProfile,
                    searchTimeSettings = request.searchTimeSettingsProvider(),
                    previousSnapshots = request.scoreSnapshotsProvider(),
                ),
                operationRequest = operation,
                diagnosticEventLog = request.diagnosticEventLog,
            )
        }
    return buildAutoAiTurnCompletionPlan(
        result = turnResult,
        token = AutoAiTurnOperationToken(operation),
        currentState = request.currentStateProvider(),
        currentSessionGeneration = request.currentSessionGenerationProvider(),
    )
}
