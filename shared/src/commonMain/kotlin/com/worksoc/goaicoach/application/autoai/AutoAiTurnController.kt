package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.contract.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.contract.AutoAiTurnEndgamePlan
import com.worksoc.goaicoach.application.contract.GameSessionRuntimeState
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineGamePlayClient
import com.worksoc.goaicoach.application.engine.launchAutoAiEffect
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.score.EndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.session.AutoAiTurnTimeout
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.EngineOperationResultGuard
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class AutoAiTurnController(
    private val scope: CoroutineScope,
    private val engineClient: EngineGamePlayClient,
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
    /**
     * 띄운 AI 차례의 Job을 **이 컨트롤러보다 오래 사는** 자리에 맡긴다(refactor backlog #74). 이 컨트롤러는
     * `wiringContext`가 바뀔 때마다 새로 만들어지므로 Job을 필드로 들면 취소할 인스턴스가 그것을 모른다.
     * 배선은 `EngineOperationLifecycleController::trackAutoAiTurnJob`을 넘긴다.
     */
    private val trackInFlightTurn: (Job) -> Unit,
    /** 맡긴 Job을 취소한다 — 배선은 `EngineOperationLifecycleController::cancelInFlightAutoAiTurn`. */
    private val cancelTrackedTurn: () -> Unit,
    /** 탐색이 시간 초과로 끝난 국면을 표시한다 — 표시가 맞는 동안 [requestAiTurn]은 건너뛴다(refactor backlog #74). */
    private val applyAutoAiTurnTimedOut: (AutoAiTurnTimeout) -> Unit,
    /** 그 표시를 지운다 — 사용자가 팝업에서 고른 뒤. */
    private val clearAutoAiTurnTimedOut: () -> Unit,
) {
    /**
     * 시간 초과 뒤 「한 번 더 기다리기」(refactor backlog #74, 설계 C-10 상태 B). 표시를 지우고 같은 국면을 같은
     * 예산으로 다시 요청한다. 프로세스가 시간 초과로 내려갔으면 새 차례의 `configure`가 다시 띄운다.
     */
    fun retryTimedOutTurn() {
        clearAutoAiTurnTimedOut()
        requestAiTurn()
    }

    /**
     * 도는 AI 차례를 취소한다(refactor backlog #74). 물음 없이 즉시 멈춘다 — 팝업 없음, AI의 돌 없음,
     * genMove·형세 추정 없음. 정리(busy·예약 해제)는 러너의 `finally`가 한다.
     */
    fun cancelInFlightTurn() {
        cancelTrackedTurn()
    }

    /**
     * 「엔진 다시 시작하기」(refactor backlog #74, 설계 C-11). 도는 차례를 먼저 취소한 뒤 엔진을 강제로 내린다 —
     * 순서가 중요하다. 취소가 먼저여야 파이프가 닫혀 풀린 읽기의 예외가 "진짜 실패"로 읽혀 **맞추지 않은 새
     * 프로세스에서 genMove**로 떨어지지 않는다(설계 F2). 취소된 Job은 파이프가 닫히며 끝나고, 그 `finally`가
     * busy를 풀면 트리거 효과가 차례를 다시 요청한다 — 새 차례는 `configure`로 프로세스를 새로 띄우고 판을 맞춘다.
     * 시간 초과로 이미 끝난 차례(상태 B)라면 도는 Job이 없으므로 표시를 지우고 여기서 바로 다시 요청한다 — 도는
     * Job이 있으면(상태 A) 그 요청은 pending에 걸려 건너뛰어지고, 위의 트리거 효과가 맡는다.
     */
    fun restartEngineForStalledTurn(forceResetEngine: () -> Unit) {
        cancelInFlightTurn()
        forceResetEngine()
        clearAutoAiTurnTimedOut()
        requestAiTurn()
    }

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
                val turnJob = runScheduledAutoAiTurnApplication(
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
                        applyTurnTimedOut = applyAutoAiTurnTimedOut,
                        appendEngineOperationDiscardLog = appendEngineOperationDiscardLog,
                        completeAutoAiTurnRun = completeAutoAiTurnRun,
                        requestFollowUpAnalysis = requestFollowUpAnalysis,
                        currentStateProvider = currentGameState,
                        currentSessionGenerationProvider = currentSessionGeneration,
                    ),
                )
                trackInFlightTurn(turnJob)
            }
        }
    }
}
