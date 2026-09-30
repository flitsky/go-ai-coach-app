package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.contract.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.contract.AutoAiTurnEndgamePlan
import com.worksoc.goaicoach.application.contract.GameSessionRuntimeState
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineGamePlayClient
import com.worksoc.goaicoach.application.engine.launchAutoAiEffect
import com.worksoc.goaicoach.application.engine.operation.EngineWaitWatch
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.score.EndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.session.AutoAiTurnTimeout
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.match.MatchReferee
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
    /**
     * 진짜 실패(시간 초과도 취소도 아니다)가 난 국면을 센다(refactor backlog #109). 같은 국면에서 잇달아 나면 위의 시간 초과
     * 표시가 붙어 [requestAiTurn]이 건너뛰고 선택 팝업이 뜬다 — 예전에는 화면에 아무것도 없이 조용히 계속 돌았다.
     */
    private val recordAutoAiTurnFailure: (AutoAiTurnTimeout) -> Unit,
    /**
     * 앱 프로세스가 화면에 있는가(backlog #202). 아니면 [requestAiTurn]이 새 차례를 띄우지 않는다 — 돌아오면
     * [onAppForegrounded]가 다시 요청한다. 이 컨트롤러는 자주 다시 배선되므로 값은 컨트롤러 밖에 두고 부를 때마다
     * 읽는다 — 배선은 `EngineOperationLifecycleController::isAppInForeground`.
     */
    private val isAppInForeground: () -> Boolean = { true },
    /** 그 표시를 바꾼다 — 배선은 `EngineOperationLifecycleController::markAppInForeground`. */
    private val markAppInForeground: (Boolean) -> Unit = {},
    /**
     * AI 차례가 엔진을 기다리는 사이 앱이 멈췄는지(포그라운드 세대·멈춤 박동) 재는 관찰을 연다(backlog #204). 위의
     * 포그라운드 표시와 같은 이유로 컨트롤러 밖(수명 컨트롤러)에 둔다 — 배선은
     * `EngineOperationLifecycleController::startEngineWaitWatch`.
     */
    private val startEngineWaitWatch: () -> EngineWaitWatch,
    /**
     * 기다리는 사이의 멈춤 때문에 끊긴 시간 초과를 팝업 없이 한 번 다시 요청한다 — 그 국면에서 그 한 번을 쓴다(backlog #204).
     * ⚠️ 기본값을 두지 않는다 — 이것이 빠지면 멈춘 차례가 국면마다 **끝없이** 조용히 다시 돈다.
     */
    private val markAutoAiTurnInterruptedRetry: (AutoAiTurnTimeout) -> Unit,
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
     * 프로세스에서 genMove**로 떨어지지 않는다(설계 F2). 취소된 Job은 **곧바로** 끝나고(refactor backlog #15 — 막힌
     * 읽기의 답 받기는 2계층 배수가 넘겨받고, 뒤의 forceReset이 파이프를 닫아 그 배수를 끝낸다), 그 `finally`가
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

    /**
     * 앱 프로세스가 백그라운드로 갔다(backlog #202). 도는 AI 차례를 **취소**한다.
     *
     * 그대로 두면 안드로이드가 곧(Android 14+는 보통 10초쯤 뒤) 앱과 KataGo 자식 프로세스를 함께 동결한다. 탐색
     * 마감은 동결 중에도 흐르는 단조 시계로 재므로, 오래 나가 있다 돌아오는 순간 마감이 터져 멀쩡한 엔진을 SIGKILL로
     * 내리고 「엔진 응답 지연」이 뜬다(2026-09-30 AI 대 AI 리포트 — 36분 동결 뒤 복귀 1초 만의 시간 초과). 취소는
     * 시간 초과가 아니라 프로세스를 내리지 않고 팝업도 없다([cancelInFlightTurn]).
     *
     * ⚠️ 표시를 취소보다 **먼저** 내린다 — 취소된 차례의 `finally`가 busy를 풀면 트리거 효과가 곧바로
     * [requestAiTurn]을 부르고, 표시가 그대로면 백그라운드에서 새 차례가 뜬다.
     * ⚠️ 양패스(또는 꽉 찬 판) 뒤 **계가 중**이면 취소하지 않는다. 계가는 이미 둔 수 뒤의 정리라 다시 요청할 길이
     * 없어 판이 계가 중에 멈추고, 늦어져도 로컬 계가로 떨어질 뿐 팝업을 띄우지 않는다.
     */
    fun onAppBackgrounded() {
        markAppInForeground(false)
        val controllerState = currentControllerState()
        val hadTurnInFlight = controllerState.isAutoAiTurnPending
        val isResolvingEndgame = MatchReferee.shouldResolveEndgame(controllerState.gameState)
        val cancelled = hadTurnInFlight && !isResolvingEndgame
        if (cancelled) {
            cancelInFlightTurn()
        }
        runtimeEventLog.append(
            runtimeAppBackgroundLog(
                context = currentRuntimeLogContext(),
                hadTurnInFlight = hadTurnInFlight,
                cancelled = cancelled,
                isResolvingEndgame = isResolvingEndgame,
            ),
        )
    }

    /** 앱 프로세스가 화면으로 돌아왔다(backlog #202) — 백그라운드 동안 건너뛴 차례를 다시 요청한다. */
    fun onAppForegrounded() {
        markAppInForeground(true)
        runtimeEventLog.append(runtimeAppForegroundLog(currentRuntimeLogContext()))
        requestAiTurn()
    }

    fun requestAiTurn() {
        // 백그라운드에서는 새 차례를 띄우지 않는다(backlog #202) — 돌아오면 [onAppForegrounded]가 다시 요청한다.
        if (!isAppInForeground()) return
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
                        applyTurnFailed = recordAutoAiTurnFailure,
                        startEngineWaitWatch = startEngineWaitWatch,
                        applyTurnInterrupted = markAutoAiTurnInterruptedRetry,
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
