package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationResult
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheQuality
import com.worksoc.goaicoach.application.autoai.AutoAiScheduledTurnRunRequest
import com.worksoc.goaicoach.application.autoai.AutoAiTurnFollowUpPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnFollowUpRequest
import com.worksoc.goaicoach.application.autoai.AutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnScheduleValidationPlan
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnScheduleValidationPlan
import com.worksoc.goaicoach.application.autoai.completeAutoAiTurnRun
import com.worksoc.goaicoach.application.autoai.runScheduledAutoAiTurnApplication
import com.worksoc.goaicoach.application.autoai.toAutoAiTurnRequestPlan
import com.worksoc.goaicoach.application.contract.GameSessionRuntimeState
import com.worksoc.goaicoach.application.contract.PositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.application.engine.AutoAiTurnResult
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProgress
import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.engine.EngineSessionCapabilities
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.EngineStartupResult
import com.worksoc.goaicoach.application.engine.LocalEngineMoveResult
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.engine.operation.EngineWaitPauseMeasurement
import com.worksoc.goaicoach.application.engine.operation.EngineWaitProcessPauseThresholdMillis
import com.worksoc.goaicoach.application.engine.operation.EngineWaitWatch
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import com.worksoc.goaicoach.application.session.AutoAiTurnFailureChoiceThreshold
import com.worksoc.goaicoach.application.session.AutoAiTurnFailureStreak
import com.worksoc.goaicoach.application.session.AutoAiTurnTimeout
import com.worksoc.goaicoach.application.session.AutoAiTurnUiState
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionCoreState
import com.worksoc.goaicoach.application.session.GameSessionMoveReviewState
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.testsupport.FakeEngineSessionClient
import com.worksoc.goaicoach.testsupport.RecordingRuntimeEventLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class AutoAiScheduledTurnRunnerTest {
    @Test
    fun runnerExecutesScheduledTurnAndRequestsFollowUpAnalysis() {
        val before = GameState.empty()
        val after = before.play(
            Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
        )
        val playLevel = PlayLevelSetting()
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai, playLevel = playLevel),
            white = SidePlayerSetup(controller = SeatController.Human),
        )
        var currentState = before
        var autoAiState = AutoAiTurnUiState()
        val runtimeState = GameSessionRuntimeState(
            playLevel = playLevel,
            engineProfile = EngineProfile(name = "runner"),
            analysisPreset = AnalysisPreset.Lite,
            sessionGeneration = 4L,
        )
        val client = ScheduledRunnerFakeEngineClient(
            result = AutoAiTurnResult(
                turnOutcome = TurnOutcome(
                    gameState = after,
                    engineMessage = "AI played E5",
                    candidateText = "candidate",
                    lastMoveText = "Black E5",
                ),
                scoreEstimate = null,
                profile = runtimeState.engineProfile,
                playLevel = playLevel,
            ),
        )
        val runtimeLog = RecordingRuntimeEventLog()
        val startedIds = mutableListOf<String>()
        val completedIds = mutableListOf<String>()
        var followUp: AutoAiTurnFollowUpRequest? = null
        var turnTimeUpdate: TurnTimeMoveUpdate? = null

        runScheduledAutoAiTurnApplication(
            baseRequest(
                schedule = AutoAiTurnRequestPlan.Schedule(delayMillis = 0L),
                stateProvider = { currentState },
                controllerStateProvider = {
                    controllerState(
                        state = currentState,
                        setup = setup,
                        runtimeState = runtimeState,
                        autoAiTurnUiState = autoAiState,
                    )
                },
                client = client,
                runtimeState = runtimeState,
                runtimeLog = runtimeLog,
                applyScheduled = { schedule ->
                    autoAiState = autoAiState.applyAutoAiTurnRequestPlan(schedule)
                },
                recordTurnMove = { player, nowMillis, nextPlayer ->
                    assertEquals(StoneColor.Black, player)
                    GameSessionTurnTimeState.reset(before, 900L)
                        .recordMove(player, nowMillis, nextPlayer)
                },
                applyTurnTimeUpdate = { update ->
                    turnTimeUpdate = update
                },
                applyTurnDisplay = { display ->
                    currentState = display.gameState
                    AutoAiTurnFollowUpPlan.RequestTopMoveAnalysis(display.gameState)
                },
                markStarted = { id -> startedIds += id },
                markCompleted = { id -> completedIds += id },
                completeRun = { autoAiState = autoAiState.completeAutoAiTurnRun() },
                requestFollowUp = { request -> followUp = request },
            ),
        )

        assertEquals(before, client.currentState)
        assertEquals(playLevel, client.playLevel)
        assertEquals(runtimeState.engineProfile, client.currentProfile)
        assertEquals(SearchTimeSettings(), client.searchTimeSettings)
        assertEquals(after, currentState)
        assertEquals(StoneColor.Black, turnTimeUpdate?.player)
        assertEquals(false, autoAiState.isPending)
        assertEquals(1, startedIds.size)
        assertEquals(startedIds, completedIds)
        assertEquals(after, followUp?.targetState)
        assertEquals(true, followUp?.automatic)
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_schedule") })
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_begin") })
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_complete") })
    }

    @Test
    fun runnerCancelsScheduledTurnWhenValidationNoLongerAllowsAiMove() {
        val state = GameState.empty()
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Human),
        )
        var autoAiState = AutoAiTurnUiState()
        var cancelled = false
        var launchedEngine = false
        val runtimeLog = RecordingRuntimeEventLog()
        val runtimeState = GameSessionRuntimeState(
            playLevel = PlayLevelSetting(),
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            sessionGeneration = 1L,
        )

        runScheduledAutoAiTurnApplication(
            baseRequest(
                schedule = AutoAiTurnRequestPlan.Schedule(delayMillis = 10L),
                stateProvider = { state },
                controllerStateProvider = {
                    controllerState(
                        state = state,
                        setup = setup,
                        runtimeState = runtimeState,
                        autoAiTurnUiState = autoAiState,
                    )
                },
                client = ScheduledRunnerFakeEngineClient(
                    result = AutoAiTurnResult(
                        turnOutcome = TurnOutcome(state, "unused", "unused", "unused"),
                        scoreEstimate = null,
                        profile = EngineProfile(),
                        playLevel = PlayLevelSetting(),
                    ),
                ),
                runtimeState = runtimeState,
                runtimeLog = runtimeLog,
                isEngineReady = { false },
                delayMillis = { millis -> assertEquals(10L, millis) },
                applyScheduled = { schedule ->
                    autoAiState = autoAiState.applyAutoAiTurnRequestPlan(schedule)
                },
                applyCancelled = { cancel ->
                    assertEquals(AutoAiTurnScheduleValidationPlan.Cancel, cancel)
                    cancelled = true
                    autoAiState = autoAiState.applyAutoAiTurnScheduleValidationPlan(cancel)
                },
                markStarted = { launchedEngine = true },
            ),
        )

        assertEquals(true, cancelled)
        assertEquals(false, launchedEngine)
        assertEquals(false, autoAiState.isPending)
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_schedule_cancelled") })
    }

    /**
     * T5(refactor backlog #74, 설계 F3 (b)) — 엔진 호출 중에 AI 차례의 Job을 취소해도 **정리는 한다.**
     *
     * 예전에는 5계층의 `runCatching`이 취소를 삼킨 뒤 `runEngineIo`(IO 디스패처)에서 돌아오는 순간 즉시 취소가
     * 다시 던져져 `markEngineOperationCompleted`·`completeAutoAiTurnRun`을 건너뛰었다 — busy와 예약 표시가 영원히
     * 남아 **AI가 다시는 두지 않는다.** 그래서 이 테스트는 launch를 `runBlocking`의 루프(IO와 다른 디스패처)에서
     * 돌린다 — 그 복귀 경계를 실제로 건너야 재현된다.
     */
    @Test
    fun cancellingTheLaunchedTurnDuringTheEngineCallStillCompletesTheOperationAndClearsPending() = runBlocking {
        val state = GameState.empty()
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Human),
        )
        var autoAiState = AutoAiTurnUiState()
        val runtimeState = GameSessionRuntimeState(
            playLevel = PlayLevelSetting(),
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            sessionGeneration = 2L,
        )
        val engineEntered = CompletableDeferred<Unit>()
        val startedIds = mutableListOf<String>()
        val completedIds = mutableListOf<String>()
        var completeRunCount = 0
        var failureDisplays = 0
        var followUps = 0
        var launched: Job? = null

        runScheduledAutoAiTurnApplication(
            baseRequest(
                schedule = AutoAiTurnRequestPlan.Schedule(delayMillis = 0L),
                stateProvider = { state },
                controllerStateProvider = {
                    controllerState(state = state, setup = setup, runtimeState = runtimeState, autoAiTurnUiState = autoAiState)
                },
                client = SuspendingRunnerFakeEngineClient { engineEntered.complete(Unit); awaitCancellation() },
                runtimeState = runtimeState,
                runtimeLog = RecordingRuntimeEventLog(),
                applyScheduled = { schedule -> autoAiState = autoAiState.applyAutoAiTurnRequestPlan(schedule) },
                markStarted = { id -> startedIds += id },
                markCompleted = { id -> completedIds += id },
                applyTurnFailureDisplay = { failureDisplays += 1 },
                completeRun = { completeRunCount += 1; autoAiState = autoAiState.completeAutoAiTurnRun() },
                requestFollowUp = { followUps += 1 },
            ).copy(launchAutoAiEffect = { block -> launch { block() }.also { launched = it } }),
        )
        engineEntered.await()
        val job = requireNotNull(launched)
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(1, startedIds.size)
        assertEquals(startedIds, completedIds, "시작한 엔진 작업은 취소돼도 완료로 적어야 busy가 풀린다")
        assertEquals(1, completeRunCount, "예약 표시(pending)를 풀지 않으면 AI가 다시는 두지 않는다")
        assertEquals(false, autoAiState.isPending)
        assertEquals(0, failureDisplays, "사용자가 취소한 차례에 실패 문구를 띄우지 않는다")
        assertEquals(0, followUps, "취소한 차례는 후속 분석을 걸지 않는다")
    }

    /**
     * T6(앞 절반, refactor backlog #74) — 호출자가 살아 있는데 엔진이 **시간 초과**로 끝나면 그것은 실패가 아니다.
     * 「AI turn failed…」를 띄우지 않고 선택 팝업이 설명한다(설계 C-12). 정리는 여느 때처럼 한다.
     */
    @Test
    fun engineTimeoutWhileTheTurnIsActiveIsNotShownAsAFailure() {
        val state = GameState.empty()
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Human),
        )
        var autoAiState = AutoAiTurnUiState()
        val runtimeState = GameSessionRuntimeState(
            playLevel = PlayLevelSetting(),
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            sessionGeneration = 3L,
        )
        val startedIds = mutableListOf<String>()
        val completedIds = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()

        runScheduledAutoAiTurnApplication(
            baseRequest(
                schedule = AutoAiTurnRequestPlan.Schedule(delayMillis = 0L),
                stateProvider = { state },
                controllerStateProvider = {
                    controllerState(state = state, setup = setup, runtimeState = runtimeState, autoAiTurnUiState = autoAiState)
                },
                client = SuspendingRunnerFakeEngineClient { withTimeout(1L) { awaitCancellation() } },
                runtimeState = runtimeState,
                runtimeLog = RecordingRuntimeEventLog(),
                applyScheduled = { schedule -> autoAiState = autoAiState.applyAutoAiTurnRequestPlan(schedule) },
                markStarted = { id -> startedIds += id },
                markCompleted = { id -> completedIds += id },
                applyTurnFailureDisplay = { error -> failures += error },
                completeRun = { autoAiState = autoAiState.completeAutoAiTurnRun() },
            ),
        )

        assertEquals(emptyList<Throwable>(), failures, "시간 초과를 「AI turn failed」로 띄우면 안 된다: $failures")
        assertEquals(startedIds, completedIds)
        assertEquals(false, autoAiState.isPending)
    }

    /**
     * T6(뒤 절반, refactor backlog #74, 설계 C-8) — 시간 초과로 끝난 차례는 **그 국면(세대·수순 길이)에 표시를 남겨**
     * busy가 풀린 뒤 트리거 효과의 조용한 재시도를 막는다(사용자가 팝업에서 고를 때까지). 세대가 오르면(무르기·
     * 새 대국·나가기) 표시는 저절로 효력을 잃고 다시 예약된다. 표시를 지우면(「한 번 더 기다리기」) 같은 국면도 다시 예약된다.
     */
    @Test
    fun engineTimeoutMarksThePositionAndBlocksTheSilentRetryUntilTheGenerationMoves() {
        val state = GameState.empty()
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Human),
        )
        var autoAiState = AutoAiTurnUiState()
        val runtimeState = GameSessionRuntimeState(
            playLevel = PlayLevelSetting(),
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            sessionGeneration = 3L,
        )
        val runtimeLog = RecordingRuntimeEventLog()

        runScheduledAutoAiTurnApplication(
            baseRequest(
                schedule = AutoAiTurnRequestPlan.Schedule(delayMillis = 0L),
                stateProvider = { state },
                controllerStateProvider = {
                    controllerState(state = state, setup = setup, runtimeState = runtimeState, autoAiTurnUiState = autoAiState)
                },
                client = SuspendingRunnerFakeEngineClient { withTimeout(1L) { awaitCancellation() } },
                runtimeState = runtimeState,
                runtimeLog = runtimeLog,
                applyScheduled = { schedule -> autoAiState = autoAiState.applyAutoAiTurnRequestPlan(schedule) },
                applyTurnTimedOut = { timeout -> autoAiState = autoAiState.markTimedOut(timeout) },
                completeRun = { autoAiState = autoAiState.completeAutoAiTurnRun() },
            ),
        )

        assertEquals(AutoAiTurnTimeout(sessionGeneration = 3L, moveCount = 0), autoAiState.timedOut)
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_timeout") })
        fun requestPlanFor(generation: Long, uiState: AutoAiTurnUiState) =
            controllerState(
                state = state,
                setup = setup,
                runtimeState = runtimeState.copy(sessionGeneration = generation),
                autoAiTurnUiState = uiState,
            ).toAutoAiTurnRequestPlan(isEngineReady = true, isEngineBusy = false)

        assertEquals(
            AutoAiTurnRequestPlan.Skip,
            requestPlanFor(3L, autoAiState),
            "사용자가 고르기 전에 같은 예산으로 조용히 다시 탐색하면 안 된다",
        )
        assertEquals(
            AutoAiTurnRequestPlan.Schedule(delayMillis = 0L),
            requestPlanFor(4L, autoAiState),
            "세대가 오르면(무르기·새 대국·나가기) 표시는 저절로 풀린다",
        )
        assertEquals(
            AutoAiTurnRequestPlan.Schedule(delayMillis = 0L),
            requestPlanFor(3L, autoAiState.clearTimedOut()),
            "「한 번 더 기다리기」가 표시를 지우면 같은 국면도 다시 예약된다",
        )
    }

    /**
     * refactor backlog #109 ⓐ — 진짜 실패는 **그 국면(세대·수순 길이)에서 센다**. 첫 실패 뒤에는 조용한 재시도가 그대로
     * 예약되고(스스로 낫는 길), 같은 국면에서 [AutoAiTurnFailureChoiceThreshold]번째 실패면 시간 초과와 같은 표시가 붙어
     * 재시도를 막는다(선택 팝업). 사용자가 고른 뒤 또 실패하면 곧바로 다시 막는다. 국면이 바뀌면 처음부터 센다.
     */
    @Test
    fun aRealFailureRepeatedOnTheSamePositionMarksItLikeATimeoutAndBlocksTheSilentRetry() {
        val state = GameState.empty()
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Human),
        )
        var autoAiState = AutoAiTurnUiState()
        val runtimeState = GameSessionRuntimeState(
            playLevel = PlayLevelSetting(),
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            sessionGeneration = 3L,
        )
        val failures = mutableListOf<Throwable>()
        fun runFailingTurn() {
            runScheduledAutoAiTurnApplication(
                baseRequest(
                    schedule = AutoAiTurnRequestPlan.Schedule(delayMillis = 0L),
                    stateProvider = { state },
                    controllerStateProvider = {
                        controllerState(state = state, setup = setup, runtimeState = runtimeState, autoAiTurnUiState = autoAiState)
                    },
                    client = SuspendingRunnerFakeEngineClient { error("KataGo process died") },
                    runtimeState = runtimeState,
                    runtimeLog = RecordingRuntimeEventLog(),
                    applyScheduled = { schedule -> autoAiState = autoAiState.applyAutoAiTurnRequestPlan(schedule) },
                    applyTurnFailureDisplay = { failures += it },
                    applyTurnTimedOut = { error("진짜 실패는 시간 초과가 아니다") },
                    applyTurnFailed = { position -> autoAiState = autoAiState.recordFailure(position) },
                    completeRun = { autoAiState = autoAiState.completeAutoAiTurnRun() },
                ),
            )
        }
        fun requestPlan(uiState: AutoAiTurnUiState = autoAiState, moves: GameState = state) =
            controllerState(state = moves, setup = setup, runtimeState = runtimeState, autoAiTurnUiState = uiState)
                .toAutoAiTurnRequestPlan(isEngineReady = true, isEngineBusy = false)
        val position = AutoAiTurnTimeout(sessionGeneration = 3L, moveCount = 0)
        assertEquals(2, AutoAiTurnFailureChoiceThreshold, "이 시나리오는 두 번째 실패에서 묻는다고 둔다")

        runFailingTurn()
        assertEquals(1, failures.size, "실패 문구는 그대로 적힌다")
        assertEquals(AutoAiTurnFailureStreak(position, count = 1), autoAiState.failureStreak)
        assertNull(autoAiState.timedOut)
        assertEquals(AutoAiTurnRequestPlan.Schedule(delayMillis = 0L), requestPlan(), "첫 실패 뒤에는 조용히 한 번 더 시도한다")

        runFailingTurn()
        assertEquals(position, autoAiState.timedOut, "같은 국면의 두 번째 실패면 선택 팝업의 표시가 붙는다")
        assertEquals(AutoAiTurnRequestPlan.Skip, requestPlan(), "선택을 기다리는 동안 조용히 다시 시도하면 안 된다")

        autoAiState = autoAiState.clearTimedOut() // 사용자가 「한 번 더 기다리기」·「엔진 다시 시작하기」를 골랐다.
        assertEquals(AutoAiTurnRequestPlan.Schedule(delayMillis = 0L), requestPlan(), "고른 뒤에는 같은 국면을 다시 요청한다")
        runFailingTurn()
        assertEquals(position, autoAiState.timedOut, "사용자가 이미 본 문제는 다시 조용히 돌리지 않는다 — 곧바로 다시 묻는다")

        val afterAMove = autoAiState.clearTimedOut().recordFailure(position.copy(moveCount = 1))
        assertEquals(1, afterAMove.failureStreak?.count, "국면이 바뀌면 처음부터 센다")
        assertNull(afterAMove.timedOut)
    }

    /**
     * backlog #204 (a)·(b)·(e) — 엔진을 기다리는 사이 **포그라운드 세대가 바뀐**(앱이 화면을 떠났다 돌아온) 차례의 시간
     * 초과는 「엔진 응답 지연」이 아니다. 그 마감은 앱이 화면에 없던 시간까지 쟀다. 그래서 표시(선택 팝업)를 남기지 않고
     * 그 국면의 조용한 재시도 한 번을 쓴다 — busy가 풀리면 트리거 효과가 **같은 국면**을 다시 요청한다. 다시 요청한 차례가
     * 또 시간 초과면(또 멈췄더라도) 지금처럼 팝업이다 — 조용한 반복은 없다. 두 줄 다 로그에 멈춤의 신호를 적는다.
     */
    @Test
    fun aTimeoutWhileTheAppLeftTheForegroundRetriesTheSamePositionOnceThenAsks() {
        val scenario = InterruptedWaitScenario()

        // ── 첫 시도: 기다리는 사이 앱이 화면을 떠났다 돌아왔고(세대 +2), 그 기다림이 시간 초과로 끝났다.
        scenario.runTimingOutTurn(onWait = { scenario.foregroundGeneration += 2 })

        assertEquals(listOf(scenario.position), scenario.interruptedMarks, "그 국면의 조용한 재시도 한 번을 쓴다")
        assertEquals(emptyList(), scenario.timedOutMarks, "멈춘 기다림의 시간 초과로 선택 팝업을 띄우면 오탐이다")
        assertNull(scenario.autoAiState.timedOut)
        assertEquals(
            AutoAiTurnRequestPlan.Schedule(delayMillis = 0L),
            scenario.requestPlan(),
            "busy가 풀리면 같은 국면을 다시 요청한다 — 표시가 없으니 건너뛰지 않는다",
        )
        val first = scenario.runtimeLog.events.single { it.contains("event=ai_turn_timeout") }
        assertTrue(first.contains("backgroundedDuringWait=true"), first)
        assertTrue(first.contains("processPauseMs=0"), first)
        assertTrue(first.contains("transition=\"keep_current_board_retry_same_position\""), first)

        // ── 다시 요청한 차례도 멈춘 채 시간 초과 → 이번엔 팝업(조용한 재시도는 국면마다 한 번).
        scenario.runTimingOutTurn(onWait = { scenario.foregroundGeneration += 2 })

        assertEquals(listOf(scenario.position), scenario.interruptedMarks, "두 번째는 조용히 넘기지 않는다")
        assertEquals(listOf(scenario.position), scenario.timedOutMarks)
        assertEquals(scenario.position, scenario.autoAiState.timedOut, "두 번째 시간 초과는 지금처럼 선택 팝업이다")
        assertEquals(AutoAiTurnRequestPlan.Skip, scenario.requestPlan(), "선택을 기다리는 동안 조용히 다시 돌리면 안 된다")
        val second = scenario.runtimeLog.events.filter { it.contains("event=ai_turn_timeout") }.last()
        assertTrue(second.contains("backgroundedDuringWait=true"), second)
        assertTrue(second.contains("transition=\"keep_current_board_await_choice\""), second)
    }

    /**
     * backlog #204 — 수명 콜백 없이 **프로세스가 멈췄던**(동결이 `ON_STOP`보다 먼저·VM 정지) 기다림도 같다. 박동이
     * [EngineWaitProcessPauseThresholdMillis] 이상 늦었으면 세대가 그대로여도 조용히 한 번 다시 요청한다. 그보다 짧은
     * 멈춤은 마감을 넘기게 할 수 없으므로 지금처럼 팝업이다.
     */
    @Test
    fun aTimeoutAfterTheProcessWasPausedWithoutALifecycleCallbackAlsoRetriesOnce() {
        val shortPause = InterruptedWaitScenario(processPauseMillis = EngineWaitProcessPauseThresholdMillis - 1)
        shortPause.runTimingOutTurn()
        assertEquals(listOf(shortPause.position), shortPause.timedOutMarks, "짧은 멈춤은 오탐 팝업을 만들지 못한다 — 진짜 시간 초과다")
        assertEquals(emptyList(), shortPause.interruptedMarks)

        val frozen = InterruptedWaitScenario(processPauseMillis = 69_000L)
        frozen.runTimingOutTurn()
        assertEquals(listOf(frozen.position), frozen.interruptedMarks)
        assertEquals(emptyList(), frozen.timedOutMarks)
        val line = frozen.runtimeLog.events.single { it.contains("event=ai_turn_timeout") }
        assertTrue(line.contains("backgroundedDuringWait=false"), line)
        assertTrue(line.contains("processPauseMs=69000"), line)
        assertTrue(line.contains("transition=\"keep_current_board_retry_same_position\""), line)
    }

    /** backlog #204 (c)·(e) — 앱이 멈추지 않은 기다림의 시간 초과는 지금처럼(refactor backlog #74) 선택 팝업이다. */
    @Test
    fun aTimeoutWithoutAnyInterruptionStillAsksTheUser() {
        val scenario = InterruptedWaitScenario()

        scenario.runTimingOutTurn()

        assertEquals(listOf(scenario.position), scenario.timedOutMarks)
        assertEquals(emptyList(), scenario.interruptedMarks)
        assertEquals(AutoAiTurnRequestPlan.Skip, scenario.requestPlan())
        val line = scenario.runtimeLog.events.single { it.contains("event=ai_turn_timeout") }
        assertTrue(line.contains("backgroundedDuringWait=false"), line)
        assertTrue(line.contains("processPauseMs=0"), line)
        assertTrue(line.contains("transition=\"keep_current_board_await_choice\""), line)
    }

    /**
     * backlog #204 (d) — 조용한 재시도는 **국면마다** 한 번이다. 국면(세대·수순 길이)이 바뀌면 다시 쓸 수 있고, 사용자가
     * 팝업에서 고르면(「한 번 더 기다리기」·「엔진 다시 시작하기」 = `clearTimedOut`) 그 국면에서도 다시 쓸 수 있다.
     * 기다림이 **끝난 뒤**의 세대 변화는 그 기다림의 일이 아니다(판정은 엔진 호출이 돌아온 순간에 굳는다).
     */
    @Test
    fun theSilentRetryIsOncePerPositionAndResetsOnANewPositionOrTheUsersChoice() {
        val scenario = InterruptedWaitScenario()
        scenario.runTimingOutTurn(onWait = { scenario.foregroundGeneration += 2 })
        assertTrue(scenario.autoAiState.hasSpentInterruptedRetry(scenario.position))

        val nextMove = scenario.position.copy(moveCount = scenario.position.moveCount + 2)
        val undone = scenario.position.copy(sessionGeneration = scenario.position.sessionGeneration + 1)
        assertFalse(scenario.autoAiState.hasSpentInterruptedRetry(nextMove), "AI가 두고 다음 차례가 오면 다시 쓸 수 있다")
        assertFalse(scenario.autoAiState.hasSpentInterruptedRetry(undone), "무르기·새 대국(세대가 오른다)도 다시 쓸 수 있다")

        scenario.runTimingOutTurn(onWait = { scenario.foregroundGeneration += 2 })
        assertEquals(scenario.position, scenario.autoAiState.timedOut)
        val afterChoice = scenario.autoAiState.clearTimedOut()
        assertNull(afterChoice.timedOut)
        assertFalse(afterChoice.hasSpentInterruptedRetry(scenario.position), "사용자가 고르면 그 국면에서도 다시 쓸 수 있다")

        var generation = 0L
        val watch = EngineWaitWatch(
            foregroundGenerationAtStart = generation,
            currentForegroundGeneration = { generation },
            pause = EngineWaitPauseMeasurement { 0L },
        )
        assertFalse(watch.finish().backgroundedDuringWait)
        generation += 2
        assertFalse(watch.finish().isInterrupted, "굳힌 판정은 뒤의 전환으로 바뀌지 않는다")
    }

    /**
     * backlog #204 — 기다림이 취소로 끝나도(#202의 백그라운드 취소·무르기) 관찰은 닫힌다(박동을 끈다). 취소는 시간 초과가
     * 아니므로 조용한 재시도도 쓰지 않는다.
     */
    @Test
    fun aCancelledWaitClosesTheWatchAndSpendsNothing() {
        val scenario = InterruptedWaitScenario()
        val entered = CompletableDeferred<Unit>()
        var finished = 0
        val scope = CoroutineScope(Job())
        val job = runScheduledAutoAiTurnApplication(
            scenario.request(
                client = SuspendingRunnerFakeEngineClient {
                    scenario.foregroundGeneration += 1
                    entered.complete(Unit)
                    awaitCancellation()
                },
                startEngineWaitWatch = {
                    EngineWaitWatch(
                        foregroundGenerationAtStart = scenario.foregroundGeneration,
                        currentForegroundGeneration = { scenario.foregroundGeneration },
                        pause = EngineWaitPauseMeasurement {
                            finished += 1
                            0L
                        },
                    )
                },
            ).copy(launchAutoAiEffect = { block -> scope.launch { block() } }),
        )
        runBlocking {
            withTimeout(5_000L) { entered.await() }
            job.cancel()
            job.join()
        }

        assertEquals(1, finished, "취소돼도 멈춤 측정을 닫는다")
        assertEquals(emptyList(), scenario.interruptedMarks)
        assertEquals(emptyList(), scenario.timedOutMarks)
        assertTrue(scenario.runtimeLog.events.none { it.contains("event=ai_turn_timeout") })
    }

    /** backlog #204 시나리오의 뼈대 — AI(흑)가 빈 판에서 두는 차례를 시간 초과로 끝낸다. */
    private inner class InterruptedWaitScenario(private val processPauseMillis: Long = 0L) {
        val state = GameState.empty()
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Human),
        )
        val runtimeState = GameSessionRuntimeState(
            playLevel = PlayLevelSetting(),
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            sessionGeneration = 7L,
        )
        val position = AutoAiTurnTimeout(sessionGeneration = 7L, moveCount = 0)
        var autoAiState = AutoAiTurnUiState()
        var foregroundGeneration = 0L
        val runtimeLog = RecordingRuntimeEventLog()
        val interruptedMarks = mutableListOf<AutoAiTurnTimeout>()
        val timedOutMarks = mutableListOf<AutoAiTurnTimeout>()

        fun request(
            client: EngineSessionClient,
            startEngineWaitWatch: () -> EngineWaitWatch = {
                EngineWaitWatch(
                    foregroundGenerationAtStart = foregroundGeneration,
                    currentForegroundGeneration = { foregroundGeneration },
                    pause = EngineWaitPauseMeasurement { processPauseMillis },
                )
            },
        ): AutoAiScheduledTurnRunRequest =
            baseRequest(
                schedule = AutoAiTurnRequestPlan.Schedule(delayMillis = 0L),
                stateProvider = { state },
                controllerStateProvider = {
                    controllerState(state = state, setup = setup, runtimeState = runtimeState, autoAiTurnUiState = autoAiState)
                },
                client = client,
                runtimeState = runtimeState,
                runtimeLog = runtimeLog,
                applyScheduled = { schedule -> autoAiState = autoAiState.applyAutoAiTurnRequestPlan(schedule) },
                applyTurnTimedOut = { timeout ->
                    timedOutMarks += timeout
                    autoAiState = autoAiState.markTimedOut(timeout)
                },
                applyTurnFailed = { error("시간 초과는 진짜 실패가 아니다") },
                completeRun = { autoAiState = autoAiState.completeAutoAiTurnRun() },
                startEngineWaitWatch = startEngineWaitWatch,
                applyTurnInterrupted = { interrupted ->
                    interruptedMarks += interrupted
                    autoAiState = autoAiState.markInterruptedRetry(interrupted)
                },
            )

        /** 엔진이 [onWait]를 부른 뒤(기다리는 사이 일어난 일) 안쪽 `withTimeout`으로 끊긴다. */
        fun runTimingOutTurn(onWait: () -> Unit = {}) {
            runScheduledAutoAiTurnApplication(
                request(
                    client = SuspendingRunnerFakeEngineClient {
                        onWait()
                        withTimeout(1L) { awaitCancellation() }
                    },
                ),
            )
        }

        fun requestPlan(): AutoAiTurnRequestPlan =
            controllerState(state = state, setup = setup, runtimeState = runtimeState, autoAiTurnUiState = autoAiState)
                .toAutoAiTurnRequestPlan(isEngineReady = true, isEngineBusy = false)
    }

    /**
     * T8(러너 쪽, refactor backlog #74) — 러너는 띄운 Job을 **돌려준다**(예전에는 버렸다, 설계 F3). 그리고 본문이
     * 한 번도 돌기 전에 취소되면(예약 직후 곧바로 무르기) 본문의 `finally`도 없으므로, 예약 표시는 Job의
     * 완료 콜백이 푼다.
     */
    @Test
    fun aTurnCancelledBeforeItsBodyRunsStillClearsPendingAndTheJobIsReturned() {
        val state = GameState.empty()
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Human),
        )
        var autoAiState = AutoAiTurnUiState()
        val runtimeState = GameSessionRuntimeState(
            playLevel = PlayLevelSetting(),
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            sessionGeneration = 5L,
        )
        val scope = CoroutineScope(Job())
        var launched: Job? = null
        var started = false
        var completeRunCount = 0

        val returned = runScheduledAutoAiTurnApplication(
            baseRequest(
                schedule = AutoAiTurnRequestPlan.Schedule(delayMillis = 0L),
                stateProvider = { state },
                controllerStateProvider = {
                    controllerState(state = state, setup = setup, runtimeState = runtimeState, autoAiTurnUiState = autoAiState)
                },
                client = SuspendingRunnerFakeEngineClient { error("본문은 돌지 않는다") },
                runtimeState = runtimeState,
                runtimeLog = RecordingRuntimeEventLog(),
                applyScheduled = { schedule -> autoAiState = autoAiState.applyAutoAiTurnRequestPlan(schedule) },
                markStarted = { started = true },
                completeRun = { completeRunCount += 1; autoAiState = autoAiState.completeAutoAiTurnRun() },
            ).copy(
                launchAutoAiEffect = { block ->
                    scope.launch(start = CoroutineStart.LAZY) { block() }.also { launched = it }
                },
            ),
        )
        assertEquals(true, autoAiState.isPending)
        returned.cancel()

        assertTrue(returned === launched, "러너가 띄운 Job을 그대로 돌려줘야 맡겨 둘 수 있다")
        assertEquals(false, started)
        assertEquals(1, completeRunCount, "본문 없이 취소된 차례도 pending을 한 번 풀어야 한다")
        assertEquals(false, autoAiState.isPending)
    }

    private fun baseRequest(
        schedule: AutoAiTurnRequestPlan.Schedule,
        stateProvider: () -> GameState,
        controllerStateProvider: () -> GameSessionControllerState,
        client: EngineSessionClient,
        runtimeState: GameSessionRuntimeState,
        runtimeLog: RecordingRuntimeEventLog,
        isEngineReady: () -> Boolean = { true },
        delayMillis: suspend (Long) -> Unit = {},
        applyScheduled: (AutoAiTurnRequestPlan.Schedule) -> Unit = {},
        applyCancelled: (AutoAiTurnScheduleValidationPlan) -> Unit = {},
        markStarted: (String) -> Unit = {},
        markCompleted: (String) -> Unit = {},
        recordTurnMove: (
            StoneColor,
            Long,
            StoneColor,
        ) -> TurnTimeMoveUpdate = { player, nowMillis, nextPlayer ->
            GameSessionTurnTimeState.reset(stateProvider(), nowMillis)
                .recordMove(player, nowMillis, nextPlayer)
        },
        applyTurnTimeUpdate: (TurnTimeMoveUpdate) -> Unit = {},
        applyTurnDisplay: (com.worksoc.goaicoach.application.contract.AutoAiTurnDisplayPlan) -> AutoAiTurnFollowUpPlan =
            { AutoAiTurnFollowUpPlan.None },
        resolveEndgame: suspend (com.worksoc.goaicoach.application.contract.AutoAiTurnEndgamePlan.Resolve) -> Unit = {},
        applyTurnFailureDisplay: (Throwable) -> Unit = {},
        applyTurnTimedOut: (AutoAiTurnTimeout) -> Unit = {},
        applyTurnFailed: (AutoAiTurnTimeout) -> Unit = {},
        appendEngineOperationDiscardLog: (
            com.worksoc.goaicoach.shared.policy.EngineOperationResultGuard.Discard,
        ) -> Unit = {},
        completeRun: () -> Unit = {},
        requestFollowUp: (AutoAiTurnFollowUpRequest) -> Unit = {},
        startEngineWaitWatch: () -> EngineWaitWatch = { EngineWaitWatch.unobserved() },
        applyTurnInterrupted: (AutoAiTurnTimeout) -> Unit = { error("이 시나리오의 기다림은 멈추지 않는다") },
    ): AutoAiScheduledTurnRunRequest =
        AutoAiScheduledTurnRunRequest(
            schedule = schedule,
            controllerStateProvider = controllerStateProvider,
            engineClient = client,
            runtimeStateProvider = { runtimeState },
            searchTimeSettingsProvider = { SearchTimeSettings() },
            scoreSnapshotsProvider = { listOf(localScoreSnapshot(stateProvider())) },
            isEngineReady = isEngineReady,
            isEngineBusy = { false },
            isGameEnded = { false },
            shouldShowResumePrompt = { false },
            runtimeContextProvider = {
                runtimeContext(
                    state = stateProvider(),
                    setup = controllerStateProvider().playerSetup,
                    runtimeState = runtimeState,
                )
            },
            runtimeEventLog = runtimeLog,
            diagnosticEventLog = NoopDiagnosticEventLog,
            delayMillis = delayMillis,
            launchAutoAiEffect = { block -> runBlocking { launch { block() } } },
            applyScheduled = applyScheduled,
            applyCancelled = applyCancelled,
            markEngineOperationStarted = markStarted,
            markEngineOperationCompleted = markCompleted,
            recordTurnMove = recordTurnMove,
            applyTurnTimeUpdate = applyTurnTimeUpdate,
            applyTurnDisplay = applyTurnDisplay,
            resolveEndgame = resolveEndgame,
            applyTurnFailureDisplay = applyTurnFailureDisplay,
            applyTurnTimedOut = applyTurnTimedOut,
            applyTurnFailed = applyTurnFailed,
            startEngineWaitWatch = startEngineWaitWatch,
            applyTurnInterrupted = applyTurnInterrupted,
            appendEngineOperationDiscardLog = appendEngineOperationDiscardLog,
            completeAutoAiTurnRun = completeRun,
            requestFollowUpAnalysis = requestFollowUp,
            currentStateProvider = stateProvider,
            currentSessionGenerationProvider = { runtimeState.sessionGeneration },
            nowMillis = { 1_000L },
        )

    private fun controllerState(
        state: GameState,
        setup: PlayerSetup,
        runtimeState: GameSessionRuntimeState,
        autoAiTurnUiState: AutoAiTurnUiState,
    ): GameSessionControllerState =
        GameSessionControllerState(
            core = GameSessionCoreState(
                gameState = state,
                isGameEnded = false,
                analysisState = GameSessionAnalysisState.empty(state, candidateText = "candidate"),
                scoreState = GameSessionScoreState.reset(
                    scoreText = "score",
                    scoreSnapshots = listOf(localScoreSnapshot(state)),
                    endgameLog = "endgame",
                ),
                runtimeState = runtimeState,
                moveReviewState = GameSessionMoveReviewState.reset(
                    moveReviewText = "review",
                    lastMoveText = "None",
                ),
                engineMessage = "engine",
            ),
            settings = GameSessionSettingsState(
                playerSetup = setup,
                autoPlayDelaySetting = AutoPlayDelaySetting.None,
                searchTimeSettings = SearchTimeSettings(),
                topMovesEnabled = true,
                boardSize = BoardSize.Nine,
            ),
            benchmark = EngineBenchmarkUiState.initial(
                benchmarkText = "benchmark",
                profile = null,
            ),
            savedSession = SavedSessionUiState(hasCheckedSavedSession = true),
            autoAiTurn = autoAiTurnUiState,
            positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
        )

    private fun runtimeContext(
        state: GameState,
        setup: PlayerSetup,
        runtimeState: GameSessionRuntimeState,
    ): RuntimeLogContext =
        RuntimeLogContext(
            engineName = "KataGo",
            engineDiagnostic = "diagnostic",
            playerSetup = setup,
            gameState = state,
            runtimeState = runtimeState,
            autoPlayDelaySetting = AutoPlayDelaySetting.None,
            searchTimeSettings = SearchTimeSettings(),
            topMovesEnabled = true,
            isEngineReady = true,
            isEngineBusy = false,
            isGameEnded = false,
            isAutoAiTurnPending = false,
            shouldShowResumePrompt = false,
            analysisCacheStats = "entries=0",
            moveAnalysisCoverage = "coverage",
            scoreText = "score",
        )
}

/** 엔진 호출(`runAutoAiTurn`)이 [onRun]을 그대로 돈다 — 멈추거나 시간 초과를 내는 데 쓴다(refactor backlog #74). */
private class SuspendingRunnerFakeEngineClient(
    private val onRun: suspend () -> AutoAiTurnResult,
) : FakeEngineSessionClient() {
    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult = onRun()
}

private class ScheduledRunnerFakeEngineClient(
    private val result: AutoAiTurnResult,
) : FakeEngineSessionClient() {
    var currentState: GameState? = null
        private set
    var playLevel: PlayLevelSetting? = null
        private set
    var currentProfile: EngineProfile? = null
        private set
    var searchTimeSettings: SearchTimeSettings? = null
        private set

    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult {
        this.currentState = currentState
        this.playLevel = playLevel
        this.currentProfile = currentProfile
        this.searchTimeSettings = searchTimeSettings
        return result
    }
}