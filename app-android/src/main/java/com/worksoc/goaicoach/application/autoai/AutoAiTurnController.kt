package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.launchAutoAiEffect
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.score.EndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionRuntimeState
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

internal class AutoAiTurnController(
    private val scope: CoroutineScope,
    private val engineClient: EngineSessionClient,
    private val diagnosticEventLog: DiagnosticEventLogPort,
    private val runtimeEventLog: RuntimeEventLogPort,
    private val currentControllerState: () -> GameSessionControllerState,
    private val currentRuntimeState: () -> GameSessionRuntimeState,
    private val currentSearchTimeSettings: () -> SearchTimeSettings,
    private val currentScoreSnapshots: () -> List<ScoreSnapshot>,
    private val isEngineReady: () -> Boolean,
    private val isEngineBusy: () -> Boolean,
    private val isGameEnded: () -> Boolean,
    private val shouldShowResumePrompt: () -> Boolean,
    private val currentRuntimeLogContext: () -> RuntimeLogContext,
    private val currentGameState: () -> GameState,
    private val currentSessionGeneration: () -> Long,
    private val markEngineOperationStarted: (String) -> Unit,
    private val markEngineOperationCompleted: (String) -> Unit,
    private val applyAutoAiTurnScheduled: (AutoAiTurnRequestPlan.Schedule) -> Unit,
    private val applyAutoAiTurnCancelled: (AutoAiTurnScheduleValidationPlan) -> Unit,
    private val recordTurnMove: (player: StoneColor, nowMillis: Long, nextPlayer: StoneColor) -> TurnTimeMoveUpdate,
    private val applyTurnTimeUpdate: (TurnTimeMoveUpdate) -> Unit,
    private val applyTurnDisplay: (AutoAiTurnDisplayPlan) -> AutoAiTurnFollowUpPlan,
    private val applyTurnFailureDisplay: (Throwable) -> Unit,
    private val completeAutoAiTurnRun: () -> Unit,
    private val appendEngineOperationDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
    private val requestFollowUpAnalysis: (AutoAiTurnFollowUpRequest) -> Unit,
    private val markGameEnded: () -> Unit,
    private val applyFinalScoreDisplayPlan: (FinalScoreDisplayPlan) -> Unit,
    private val applyEndgameFailureDisplayPlan: (EndgameFailureDisplayPlan) -> Unit,
) {
    suspend fun applyEndgamePlan(endgamePlan: AutoAiTurnEndgamePlan.Resolve) {
        runAutoAiEndgameApplication(
            AutoAiEndgameRunRequest(
                endgamePlan = endgamePlan,
                engineClient = engineClient,
                previousSnapshotsProvider = currentScoreSnapshots,
                currentStateProvider = currentGameState,
                currentSessionGenerationProvider = currentSessionGeneration,
                runtimeContextProvider = currentRuntimeLogContext,
                runtimeEventLog = runtimeEventLog,
                diagnosticEventLog = diagnosticEventLog,
                markGameEnded = markGameEnded,
                applyResolvedDisplay = applyFinalScoreDisplayPlan,
                applyFailureDisplay = applyEndgameFailureDisplayPlan,
                appendEngineOperationDiscardLog = appendEngineOperationDiscardLog,
            ),
        )
    }

    fun requestAiTurn() {
        when (
            val request = currentControllerState().toAutoAiTurnRequestPlan(
                isEngineReady = isEngineReady(),
                isEngineBusy = isEngineBusy(),
            )
        ) {
            AutoAiTurnRequestPlan.Skip -> return
            is AutoAiTurnRequestPlan.Schedule -> {
                runScheduledAutoAiTurnApplication(
                    AutoAiScheduledTurnRunRequest(
                        schedule = request,
                        controllerStateProvider = currentControllerState,
                        engineClient = engineClient,
                        runtimeStateProvider = currentRuntimeState,
                        searchTimeSettingsProvider = currentSearchTimeSettings,
                        scoreSnapshotsProvider = currentScoreSnapshots,
                        isEngineReady = isEngineReady,
                        isEngineBusy = isEngineBusy,
                        isGameEnded = isGameEnded,
                        shouldShowResumePrompt = shouldShowResumePrompt,
                        runtimeContextProvider = currentRuntimeLogContext,
                        runtimeEventLog = runtimeEventLog,
                        diagnosticEventLog = diagnosticEventLog,
                        delayMillis = { millis -> delay(millis) },
                        launchAutoAiEffect = { block -> launchAutoAiEffect(scope) { block() } },
                        applyScheduled = applyAutoAiTurnScheduled,
                        applyCancelled = applyAutoAiTurnCancelled,
                        markEngineOperationStarted = markEngineOperationStarted,
                        markEngineOperationCompleted = markEngineOperationCompleted,
                        recordTurnMove = recordTurnMove,
                        applyTurnTimeUpdate = applyTurnTimeUpdate,
                        applyTurnDisplay = applyTurnDisplay,
                        resolveEndgame = ::applyEndgamePlan,
                        applyTurnFailureDisplay = applyTurnFailureDisplay,
                        appendEngineOperationDiscardLog = appendEngineOperationDiscardLog,
                        completeAutoAiTurnRun = completeAutoAiTurnRun,
                        requestFollowUpAnalysis = requestFollowUpAnalysis,
                        currentStateProvider = currentGameState,
                        currentSessionGenerationProvider = currentSessionGeneration,
                    ),
                )
            }
        }
    }
}
