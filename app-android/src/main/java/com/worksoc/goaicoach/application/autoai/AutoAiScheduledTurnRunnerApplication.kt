package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.runtimeAiTurnBeginLog
import com.worksoc.goaicoach.application.runtime.runtimeAiTurnCompleteLog
import com.worksoc.goaicoach.application.runtime.runtimeAiTurnScheduleCancelledLog
import com.worksoc.goaicoach.application.runtime.runtimeAiTurnScheduleLog
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.application.session.GameSessionRuntimeState
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest

internal data class AutoAiScheduledTurnRunRequest(
    val schedule: AutoAiTurnRequestPlan.Schedule,
    val controllerStateProvider: () -> GameSessionControllerState,
    val engineClient: EngineSessionClient,
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
    val launchAutoAiEffect: (suspend () -> Unit) -> Unit,
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
    val appendEngineOperationDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
    val completeAutoAiTurnRun: () -> Unit,
    val requestFollowUpAnalysis: (AutoAiTurnFollowUpRequest) -> Unit,
    val currentStateProvider: () -> GameState,
    val currentSessionGenerationProvider: () -> Long,
    val nowMillis: () -> Long = { System.currentTimeMillis() },
)

internal fun runScheduledAutoAiTurnApplication(
    request: AutoAiScheduledTurnRunRequest,
) {
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
    request.launchAutoAiEffect {
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
        val turnCompletion = runAutoAiTurnEngineCompletion(
            request = request,
            turnRunPlan = turnRunPlan,
            operation = turnOperationToken.operation,
        )
        val followUpPlan = applyAutoAiTurnCompletionApplication(
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
                appendEngineOperationDiscardLog = request.appendEngineOperationDiscardLog,
            ),
        )
        request.markEngineOperationCompleted(turnOperationToken.operation.operationId)
        request.completeAutoAiTurnRun()
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
