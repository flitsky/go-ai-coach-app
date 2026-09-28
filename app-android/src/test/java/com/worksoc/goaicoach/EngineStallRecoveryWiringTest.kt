package com.worksoc.goaicoach

import com.worksoc.goaicoach.application.engine.LocalEngineSessionClient
import com.worksoc.goaicoach.application.safety.EngineStuckWaitAction
import com.worksoc.goaicoach.application.safety.engineStuckWaitActionFor
import com.worksoc.goaicoach.application.safety.engineTurnWatchdogTimeoutMillisFor
import com.worksoc.goaicoach.application.safety.isEngineStuckDialogVisible
import com.worksoc.goaicoach.application.safety.isEngineTurnWatchdogTriggered
import com.worksoc.goaicoach.application.session.AutoAiTurnTimeout
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.runTurnAutomationTriggerEffect
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.presentation.GoCoachScreenStateAssembler
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.presentation.buildGameUiEventHandlers
import com.worksoc.goaicoach.presentation.dispatchGameUiEvent
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.SearchTimeLimit
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **엔진이 멎었을 때의 복구를 끝에서 끝까지** 돌린다 — refactor backlog #74의 자체 검수(2026-09-28 사용자 결정).
 *
 * #74의 멎음은 실기에서 손으로 재현하기 어려울 만큼 드물다. 그래서 수동 실기 확인 대신, 엔진 자리(2계층)에
 * **일부러 멎는 스텁**([StallScriptedEngine])을 끼우고 그 위는 전부 진짜로 둔다:
 * `LocalEngineSessionClient`(3계층) → AI 차례 러너·컨트롤러(5계층) → 앱 배선 [wireGoCoachControllers] →
 * 화면 상태 조립([GoCoachScreenStateAssembler]) → 팝업 규칙(`GamePlaySection`이 부르는 순수 함수들) →
 * 사용자 선택(`GoCoachApp.dispatch`와 같은 [dispatchGameUiEvent] 손잡이).
 *
 * 시나리오마다 **사용자가 보는 것**을 단언한다 — 팝업이 뜨는가, 판에 AI의 돌이 놓이는가, 몇 수가 놓이는가,
 * 늦게 온 답의 돌이 판에 보이는가, `genMove`로 몰래 두지 않는가.
 *
 * ## 시간
 * 실제 시간은 기다리지 않는다. [FakeGoCoachAppWiringContext]의 디스패처는 시킬 때만 돌고(`delay`는 멈춘다),
 * 어댑터의 마감은 테스트가 [StallScriptedEngine.Hang.passDeadline]으로 **직접** 넘긴다. 와치독의 경과 시간은
 * 팝업 규칙에 숫자로 넣는다. 기다리는 것은 엔진 스레드(IO)에서 돌아오는 복귀가 줄에 도착하는 순간뿐이고,
 * 그 기다림에는 상한만 있다([pumpUntil]).
 *
 * ## 앱의 두 자동 동작을 손으로 한 번씩 부른다
 * - **차례 자동화 효과** — `GoCoachApp`의 `LaunchedEffect(isEngineBusy, …)`가 busy·수순 길이가 바뀔 때마다
 *   `runTurnAutomationTriggerEffect`를 다시 돌린다. 여기서는 그 순간마다 [runTurnAutomationEffect]를 부른다.
 * - **리컴포지션** — 앱은 세션이 바뀔 때마다 컨트롤러를 새로 배선한다. 그래서 사용자 동작마다
 *   [wireGoCoachControllers]를 새로 부른다(도는 차례의 Job은 수명 컨트롤러가 들고 있어야 닿는다 — #74 B).
 */
class EngineStallRecoveryWiringTest {
    private val engine = StallScriptedEngine()
    private val watchdogThresholdMillis = engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinOneSecond)

    /**
     * A1 — 「한 번 더 기다리기」: 탐색이 멎어 **마감이 지나면** 차례는 시간 초과로 끝나고(`genMove`로 덮지 않는다),
     * 팝업이 선택을 기다리며, 같은 국면을 조용히 다시 태우지 않는다. 사용자가 기다리기를 고르면 **정상 분석**이
     * 다시 불려 AI의 돌이 놓인다.
     */
    @Test
    fun waitAgainAfterTheSearchTimedOutAsksTheEngineAgainAndTheAiStoneLands() {
        val context = newContext(inGameSession(playerSetup = AiBlackHumanWhite, boardSize = BoardSize.Nine))
        val hang = engine.hangNextAnalysis()

        startAiTurnUntilTheEngineIsStuck(context, hang)

        // ── 상태 A: 엔진이 생각하는 중(멎음). 와치독 한도를 넘기는 순간 팝업이 뜬다.
        assertTrue("AI가 생각하는 중이면 엔진이 바쁘다", context.engineIsBusy)
        assertTrue(context.holder.current.autoAiTurn.isPending)
        assertFalse("아직 끝나지 않은 차례는 선택을 기다리지 않는다", screenOf(context).isAwaitingEngineTimeoutChoice)
        assertFalse("한도 전에는 팝업이 없다", popupVisible(context, watchdogThresholdMillis - 1, turnWaitEnded = false))
        assertTrue("한도를 넘기면 「엔진 응답 지연」 팝업이 뜬다", popupVisible(context, watchdogThresholdMillis, turnWaitEnded = false))
        // 상태 A의 「한 번 더 기다리기」는 팝업만 닫고 도는 요청을 그대로 둔다 — 엔진에 아무것도 새로 보내지 않는다.
        assertEquals(EngineStuckWaitAction.KeepWaitingAndRearm, engineStuckWaitActionFor(screenOf(context).isAwaitingEngineTimeoutChoice))
        assertEquals(1, engine.count("analyze"))

        // ── 어댑터의 마감이 지난다 → 탐색이 진짜 TimeoutCancellationException으로 끝난다.
        hang.passDeadline()
        pumpUntil(context, "시간 초과로 끝난 차례의 정리") { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)

        // ── 상태 B: 시간 초과로 끝났다. 판은 그대로, genMove 없음, 팝업이 선택을 기다린다.
        assertEquals("시간 초과는 genMove로 덮지 않는다", 0, engine.count("genMove"))
        assertTrue(engine.calls.contains("analyze:deadline"))
        assertEquals(1, events(context, "ai_turn_timeout"))
        assertEquals(0, events(context, "ai_turn_failure"))
        assertTrue("시간 초과에 「AI turn failed」 문구를 띄우지 않는다", context.engineMessages.none { it.contains("AI turn failed") })
        assertTrue("판은 그대로다", context.gameState().moves.isEmpty())
        assertEquals(
            "시간 초과 표시가 이 국면(세대·수순 길이)에 남는다",
            AutoAiTurnTimeout(sessionGeneration = generationOf(context), moveCount = 0),
            context.holder.current.autoAiTurn.timedOut,
        )
        assertTrue(context.holder.current.isAwaitingAutoAiTurnTimeoutChoice)
        assertTrue("화면 상태가 선택을 기다린다", screenOf(context).isAwaitingEngineTimeoutChoice)
        // 차례 대기가 끝나 와치독의 지역 표시는 닫혔어도 팝업은 남는다 — 사용자가 고를 때까지.
        assertTrue(popupVisible(context, elapsedSinceWatchdogBaseMillis = 0L, turnWaitEnded = true))

        // busy가 풀려 차례 자동화 효과가 다시 돌아도 같은 국면을 조용히 다시 태우지 않는다.
        runTurnAutomationEffect(context)
        assertEquals("조용한 재시도가 예약되면 안 된다", 0, context.dispatcher.queuedCount)
        assertEquals(1, engine.count("analyze"))
        assertEquals(1, events(context, "ai_turn_schedule"))

        // ── 사용자가 「한 번 더 기다리기」를 누른다(상태 B → RetryTimedOutAiTurn).
        assertEquals(EngineStuckWaitAction.RetryTimedOutTurnAndRearm, engineStuckWaitActionFor(screenOf(context).isAwaitingEngineTimeoutChoice))
        dispatch(context, GameUiEvent.RetryTimedOutAiTurn)
        assertFalse("고르자마자 선택 대기가 풀린다", screenOf(context).isAwaitingEngineTimeoutChoice)
        pumpUntil(context, "다시 요청한 AI 차례") { context.gameState().moves.size == 1 && !context.holder.current.autoAiTurn.isPending }
        drainQueue(context)

        // ── 복구: 정상 분석이 답했고 AI의 돌이 판에 놓였다.
        assertAiStoneLandedOnce(context, aiColor = StoneColor.Black)
        assertEquals("같은 국면을 한 번 더 분석했다", 2, engine.count("analyze"))
        assertNull(context.holder.current.autoAiTurn.timedOut)
        assertFalse(screenOf(context).isAwaitingEngineTimeoutChoice)
        assertFalse("사람 차례에는 팝업이 없다", popupVisible(context, watchdogThresholdMillis * 10, turnWaitEnded = false))
        assertEquals(1, events(context, "ai_turn_success"))
    }

    /**
     * A2 — 「엔진 다시 시작하기」(상태 A): 엔진이 **답하지 않는다**(forceReset 전에는 절대 돌아오지 않는다). 와치독이
     * 팝업을 띄우고, 사용자가 다시 시작을 고르면 도는 차례가 취소되고 엔진이 내려간다. 파이프가 닫혀 풀린 읽기는
     * 실패가 아니라 취소로 끝나고(genMove 없음), busy가 풀리면 새 차례가 **새 프로세스를 다시 맞춘 뒤** 정상 분석으로
     * 둔다 — 돌은 정확히 하나.
     */
    @Test
    fun restartWhileTheEngineIsWedgedRecoversOnAFreshProcessWithExactlyOneAiMove() {
        val context = newContext(inGameSession(playerSetup = AiBlackHumanWhite, boardSize = BoardSize.Nine))
        val wedge = engine.wedgeNextAnalysis(onReset = StallScriptedEngine.AfterReset.ClosedPipe)

        startAiTurnUntilTheEngineIsStuck(context, wedge)
        assertTrue("와치독 한도를 넘기면 팝업이 뜬다", popupVisible(context, watchdogThresholdMillis, turnWaitEnded = false))

        restartFromThePopupAndRecover(context)

        assertTrue(engine.calls.contains("analyze:closed-pipe"))
        assertEquals("닫힌 파이프의 예외는 실패가 아니다 — 「AI turn failed」 없음", 0, events(context, "ai_turn_failure"))
        assertTrue(context.engineMessages.none { it.contains("AI turn failed") })
    }

    /**
     * A2′ — 같은 「엔진 다시 시작하기」인데, forceReset 뒤 멎었던 읽기가 **늦은 답**(1선)을 들고 돌아온다. 취소된 차례의
     * 답이므로 판에는 절대 보이면 안 되고, 새 차례의 수 하나만 놓인다.
     */
    @Test
    fun restartWhoseWedgedCallLaterAnswersNeverAppliesThatStaleMove() {
        val context = newContext(inGameSession(playerSetup = AiBlackHumanWhite, boardSize = BoardSize.Nine))
        val wedge = engine.wedgeNextAnalysis(onReset = StallScriptedEngine.AfterReset.StaleAnswer)

        startAiTurnUntilTheEngineIsStuck(context, wedge)
        restartFromThePopupAndRecover(context)

        assertTrue("늦은 답은 실제로 돌아왔다", engine.calls.contains("analyze:late-answer"))
        assertNoStaleStone(context)
    }

    /**
     * A2″ — 「엔진 다시 시작하기」(상태 B): 탐색이 이미 시간 초과로 끝나 선택을 기다리는 중에 다시 시작을 고른다. 도는
     * 차례가 없으므로 엔진을 내리고 곧바로 같은 국면을 다시 요청한다 — 새 프로세스를 맞춘 뒤 정상 분석이 답한다.
     */
    @Test
    fun restartAfterTheSearchTimedOutRecoversOnAFreshProcess() {
        val context = newContext(inGameSession(playerSetup = AiBlackHumanWhite, boardSize = BoardSize.Nine))
        val hang = engine.hangNextAnalysis()
        startAiTurnUntilTheEngineIsStuck(context, hang)
        hang.passDeadline()
        pumpUntil(context, "시간 초과로 끝난 차례의 정리") { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)
        assertTrue(screenOf(context).isAwaitingEngineTimeoutChoice)

        dispatch(context, GameUiEvent.ForceResetEngine)
        pumpUntil(context, "다시 시작한 뒤의 AI 차례") { context.gameState().moves.size == 1 && !context.holder.current.autoAiTurn.isPending }
        drainQueue(context)

        assertAiStoneLandedOnce(context, aiColor = StoneColor.Black)
        assertFreshProcessWasSyncedBeforeAnalysis(engine.calls)
        assertFalse(screenOf(context).isAwaitingEngineTimeoutChoice)
    }

    /**
     * A3 — 취소(무르기), 취소에 곧바로 반응하는 전송(디버그 스위치의 `slow:N`·원격 엔진): 엔진이 멎어 있는 동안 사람이
     * 무르면 **묻지 않고 곧바로** 멈춘다 — 엔진을 풀어 주지 않아도 예약·busy가 풀리고 팝업은 없다. 나중에 엔진을 풀어도
     * 무른 판에 AI의 돌은 놓이지 않고, 사람 차례에 AI를 다시 부르지 않는다.
     */
    @Test
    fun undoWhileTheEngineIsStuckCancelsTheTurnAtOnceAndNoStoneLandsWhenTheEngineIsReleasedLater() {
        val context = newContext(inGameSession(playerSetup = HumanBlackAiWhite, boardSize = BoardSize.Nine))
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtC3)) }
        val hang = engine.hangNextAnalysis(cancellable = true)
        startAiTurnUntilTheEngineIsStuck(context, hang)

        dispatch(context, GameUiEvent.UndoLastTurn)

        assertTrue("판이 사람의 수 앞으로 돌아간다", context.gameState().moves.isEmpty())
        assertTrue("도는 AI 차례를 취소했다", context.diagnosticLog.events.any { it.code == "engine_operation_cancelled" })
        // 엔진은 아직 풀지 않았다 — 취소만으로 차례가 끝나고 정리된다.
        pumpUntil(context, "취소된 차례의 정리(엔진은 멎은 채)") { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)
        assertFalse(context.holder.current.autoAiTurn.isPending)
        assertFalse(context.engineIsBusy)
        assertFalse("취소는 묻지 않는다 — 팝업 없음", popupVisible(context, watchdogThresholdMillis * 10, turnWaitEnded = true))

        // 나중에 엔진이 풀린다 — 이미 떠난 차례라 아무것도 받지 않는다.
        hang.answerLate()
        runTurnAutomationEffect(context)
        drainQueue(context)

        assertFalse("취소된 분석의 늦은 답은 아무도 받지 않는다", engine.calls.contains("analyze:late-answer"))
        assertCancelledTurnLeftNoTrace(context)
    }

    /**
     * A3′ — 취소(무르기), **막힌 GTP 읽기**(취소를 무시한다, 설계 F4): 무르면 판은 곧바로 돌아가고 차례는 취소되지만,
     * 그 차례의 정리(예약·busy 해제)는 막힌 읽기가 돌아올 때 끝난다. 읽기가 늦은 답(1선)을 들고 돌아와도 그 돌은
     * 무른 판에 놓이지 않는다.
     *
     * ⚠️ 읽기가 돌아오기 **전**의 예약·busy는 단언하지 않는다 — 그 구간이 설계 F4의 알려진 한계다(취소는 표시만 하고,
     * 막힌 읽기는 인터럽트를 받지 않는다).
     */
    @Test
    fun undoWhileAGtpReadIsBlockedCancelsTheTurnAndItsLateAnswerNeverLands() {
        val context = newContext(inGameSession(playerSetup = HumanBlackAiWhite, boardSize = BoardSize.Nine))
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtC3)) }
        val hang = engine.hangNextAnalysis(cancellable = false)
        startAiTurnUntilTheEngineIsStuck(context, hang)

        dispatch(context, GameUiEvent.UndoLastTurn)

        assertTrue("판이 사람의 수 앞으로 돌아간다", context.gameState().moves.isEmpty())
        assertTrue("도는 AI 차례를 취소했다", context.diagnosticLog.events.any { it.code == "engine_operation_cancelled" })
        assertFalse("취소는 묻지 않는다 — 선택 대기 없음", screenOf(context).isAwaitingEngineTimeoutChoice)

        // 막힌 읽기가 늦은 답을 들고 돌아온다 — 그때 취소된 차례가 끝나고 정리된다.
        hang.answerLate()
        pumpUntil(context, "취소된 차례의 정리") { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)
        runTurnAutomationEffect(context)
        drainQueue(context)

        assertTrue("늦은 답은 실제로 돌아왔다", engine.calls.contains("analyze:late-answer"))
        assertCancelledTurnLeftNoTrace(context)
    }

    // ── 시나리오 뼈대 ──────────────────────────────────────────────────────────────────────────

    /**
     * 팝업의 「엔진 다시 시작하기」(상태 A)부터 복구까지 — A2·A2′가 같은 길을 간다.
     * 앱과 같은 순서다: 누름 → `restartEngineForStalledTurn(engineClient::forceResetEngine)` → 취소된 차례의 정리 →
     * busy가 풀려 차례 자동화 효과가 다시 돈다 → 새 차례.
     */
    private fun restartFromThePopupAndRecover(context: FakeGoCoachAppWiringContext) {
        dispatch(context, GameUiEvent.ForceResetEngine)
        assertTrue(engine.calls.contains("forceReset"))
        assertTrue("다시 시작은 도는 차례를 먼저 취소한다", context.diagnosticLog.events.any { it.code == "engine_operation_cancelled" })
        assertEquals("도는 차례가 있으니 두 번째 차례를 예약하지 않는다", 1, events(context, "ai_turn_schedule"))

        pumpUntil(context, "취소된 차례의 정리") { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)
        assertTrue("취소된 차례는 판을 바꾸지 않는다", context.gameState().moves.isEmpty())
        assertEquals("취소된 차례는 genMove로 떨어지지 않는다", 0, engine.count("genMove"))
        assertEquals(0, events(context, "ai_turn_success"))

        // busy가 풀렸다 → 앱의 차례 자동화 효과가 다시 돈다 → 새 차례.
        runTurnAutomationEffect(context)
        assertEquals(2, events(context, "ai_turn_schedule"))
        pumpUntil(context, "다시 시작한 뒤의 AI 차례") { context.gameState().moves.size == 1 && !context.holder.current.autoAiTurn.isPending }
        drainQueue(context)

        assertAiStoneLandedOnce(context, aiColor = StoneColor.Black)
        assertFreshProcessWasSyncedBeforeAnalysis(engine.calls)
        assertNull(context.holder.current.autoAiTurn.timedOut)
        assertFalse("사람 차례에는 팝업이 없다", popupVisible(context, watchdogThresholdMillis * 10, turnWaitEnded = false))
    }

    /** 차례 자동화 효과가 AI 차례를 예약하고, 그 차례가 엔진 안에서 [hang]에 걸릴 때까지 돌린다. */
    private fun startAiTurnUntilTheEngineIsStuck(context: FakeGoCoachAppWiringContext, hang: StallScriptedEngine.Hang) {
        runTurnAutomationEffect(context)
        assertEquals("AI 차례가 예약된다", 1, events(context, "ai_turn_schedule"))
        assertTrue("예약된 차례의 본문이 돈다", context.dispatcher.runNext())
        assertTrue("엔진 스레드가 분석에서 멎어야 한다 — 호출: ${engine.calls}", hang.awaitEntered())
    }

    // ── 앱의 손잡이 ────────────────────────────────────────────────────────────────────────────

    /** 멎는 스텁 엔진을 **진짜 3계층**(`LocalEngineSessionClient`)으로 감싸 배선 컨텍스트에 넣는다. */
    private fun newContext(session: GameSessionControllerState): FakeGoCoachAppWiringContext {
        var generation: () -> Long = { 0L }
        val client = LocalEngineSessionClient(coreApi = engine, currentSessionGeneration = { generation() })
        return FakeGoCoachAppWiringContext(session, engineClient = client).also { context ->
            generation = { context.holder.current.core.runtimeState.sessionGeneration }
            // 가장 짧은 탐색 제한 — 와치독 한도가 가장 작다(1초 × 1.2 + 3초 + 여유 5초).
            context.changeSettings { it.copy(searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinOneSecond)) }
            context.engineIsReady = true
        }
    }

    /**
     * `GoCoachApp`의 차례 자동화 `LaunchedEffect`를 한 번 돌린다(busy·수순 길이 등이 바뀔 때마다 앱이 다시 돌리는 것).
     * 무르기의 조용한 구간 대기는 가상 시간으로 건너뛴다.
     */
    private fun runTurnAutomationEffect(context: FakeGoCoachAppWiringContext) = runBlocking {
        val controllers = wireGoCoachControllers(context)
        controllers.topMovesController.resumeDeferredAnalysisIfIdle()
        runTurnAutomationTriggerEffect(
            quietUntilMillis = context.undoEngineInterventionQuietUntil(),
            topMoveTargetState = context.gameState(),
            delayMillis = {},
            requestAiTurn = controllers.autoAiTurnController::requestAiTurn,
            requestTopMoveAnalysis = { target -> controllers.topMovesController.requestAnalysis(target, automatic = true) },
        )
    }

    /**
     * 사용자 동작 — `GoCoachApp.dispatch`가 이 시나리오의 세 이벤트에 묶는 것과 **같은 손잡이**다(무르기, 「엔진 다시
     * 시작하기」 = `restartEngineForStalledTurn(engineClient::forceResetEngine)`, 「한 번 더 기다리기」(상태 B) =
     * `retryTimedOutTurn`). 앱처럼 그 순간의 컨트롤러를 새로 배선해 쓴다. 나머지 손잡이는 닿으면 터진다.
     */
    private fun dispatch(context: FakeGoCoachAppWiringContext, event: GameUiEvent) {
        val controllers = wireGoCoachControllers(context)
        fun unexpected(name: String): Nothing = error("이 시나리오에 없는 손잡이가 불렸다: $name")
        dispatchGameUiEvent(
            event = event,
            handlers = buildGameUiEventHandlers(
                currentPlayer = { unexpected("currentPlayer") },
                isTopMovesEnabled = { unexpected("isTopMovesEnabled") },
                startConfiguredGame = { unexpected("startConfiguredGame") },
                copyDebugReport = { unexpected("copyDebugReport") },
                showEngineBenchmark = { unexpected("showEngineBenchmark") },
                requestScoreEstimate = { unexpected("requestScoreEstimate") },
                toggleEvalWithGradient = { unexpected("toggleEvalWithGradient") },
                showTopMoves = { unexpected("showTopMoves") },
                hideTopMoves = { unexpected("hideTopMoves") },
                undoLastTurn = controllers.undoController::undoLastTurn,
                submitMove = { unexpected("submitMove") },
                resignCurrentGame = { unexpected("resignCurrentGame") },
                dismissResumePrompt = { unexpected("dismissResumePrompt") },
                acceptCacheOptimizationPrompt = { unexpected("acceptCacheOptimizationPrompt") },
                dismissCacheOptimizationPrompt = { unexpected("dismissCacheOptimizationPrompt") },
                restoreSavedSession = { unexpected("restoreSavedSession") },
                changePlayerSetup = { unexpected("changePlayerSetup") },
                changeAutoPlayDelay = { unexpected("changeAutoPlayDelay") },
                changeSearchTimeSettings = { unexpected("changeSearchTimeSettings") },
                changeBoardSize = { unexpected("changeBoardSize") },
                changeScoringRule = { unexpected("changeScoringRule") },
                changeKomi = { unexpected("changeKomi") },
                changeUxOptions = { unexpected("changeUxOptions") },
                changeHandicapCount = { unexpected("changeHandicapCount") },
                reportEngineTurnWatchdogTriggered = { _, _ -> },
                forceResetEngine = {
                    controllers.autoAiTurnController.restartEngineForStalledTurn(context.engineClient::forceResetEngine)
                },
                retryTimedOutAiTurn = controllers.autoAiTurnController::retryTimedOutTurn,
            ),
        )
    }

    /** 앱과 같은 조립기로 만든 지금의 화면 상태. */
    private fun screenOf(context: FakeGoCoachAppWiringContext): GameScreenState =
        GoCoachScreenStateAssembler.assemble(
            GoCoachScreenStateAssembler.Input(
                controller = context.holder.current,
                uxOptions = KaTrainUxOptions(),
                engineRuntime = GoCoachScreenStateAssembler.EngineRuntime(
                    name = context.currentEngineName,
                    diagnostic = context.currentEngineDiagnostic,
                    isReady = context.engineIsReady,
                    isBusy = context.engineIsBusy,
                    isBlockingBusy = context.engineIsBlockingBusy,
                    hasCompletedStartup = true,
                ),
                displayRuntime = GoCoachScreenStateAssembler.DisplayRuntime(
                    analysisCacheStats = "",
                    isScoreGraphExpanded = false,
                    turnTimeText = "",
                ),
            ),
        )

    /**
     * `GamePlaySection`의 「엔진 응답 지연」 팝업이 지금 보이는가 — 그 화면이 부르는 순수 규칙을 **같은 입력**으로 부른다.
     * - 지역 표시(상태 A): AI 차례에서 와치독 한도를 넘겼고, 그 뒤로 이번 차례 대기가 아직 끝나지 않았다
     *   (화면은 완료 순번이 바뀌면 지역 표시를 닫는다 — [turnWaitEnded]).
     * - 대기 표시(상태 B): 화면 상태의 `isAwaitingEngineTimeoutChoice`.
     *
     * @param elapsedSinceWatchdogBaseMillis 차례 시작(또는 다시 건 시각)부터 흐른 시간 — 화면은 1초마다 잰다.
     */
    private fun popupVisible(
        context: FakeGoCoachAppWiringContext,
        elapsedSinceWatchdogBaseMillis: Long,
        turnWaitEnded: Boolean,
    ): Boolean {
        val screen = screenOf(context)
        val isAiTurn = when (context.turnTimeState().currentTurnPlayer) {
            StoneColor.Black -> screen.playerSetup.black.controller == SeatController.Ai
            StoneColor.White -> screen.playerSetup.white.controller == SeatController.Ai
        }
        val watchdogTriggered = isEngineTurnWatchdogTriggered(
            isAiTurn = isAiTurn,
            elapsedSinceTurnStartMillis = elapsedSinceWatchdogBaseMillis,
            searchTimeLimit = screen.searchTimeSettings.limit,
            isResolvingEndgame = screen.gameState.hasConsecutivePasses() || screen.gameState.isBoardFull(),
        )
        return isEngineStuckDialogVisible(
            isWatchdogTriggered = watchdogTriggered && !turnWaitEnded,
            isAwaitingTimeoutChoice = screen.isAwaitingEngineTimeoutChoice,
        )
    }

    // ── 돌리기와 단언 ──────────────────────────────────────────────────────────────────────────

    /**
     * 줄에 선 블록을 [done]이 참이 될 때까지 돌린다. 엔진 작업은 IO 스레드를 다녀오므로 그 복귀가 줄에 **도착할
     * 때까지** 기다린다 — 시간을 흘려보내는 것이 아니라 도착을 기다리는 것이고, 10초는 오지 않을 때의 상한이다.
     */
    private fun pumpUntil(context: FakeGoCoachAppWiringContext, what: String, done: () -> Boolean) {
        val deadline = System.nanoTime() + PumpCapMillis * 1_000_000L
        while (!done()) {
            val remainingMillis = (deadline - System.nanoTime()) / 1_000_000L
            check(remainingMillis > 0L) {
                "$what — ${PumpCapMillis}ms 안에 오지 않았다. 엔진 호출: ${engine.calls}\n" +
                    context.runtimeLog.lines.takeLast(6).joinToString("\n")
            }
            context.dispatcher.runNextArrivingWithin(remainingMillis)
        }
    }

    /** 지금 줄에 선 블록을 전부 돌린다(기다리지 않는다) — 완료 로그·후속 요청 같은 꼬리까지. */
    private fun drainQueue(context: FakeGoCoachAppWiringContext): Int {
        var ran = 0
        while (context.dispatcher.runNext()) ran += 1
        return ran
    }

    /** 수가 **정확히 하나** 늘었고, 그것이 정상 분석의 후보 자리에 놓인 AI의 돌이다. */
    private fun assertAiStoneLandedOnce(context: FakeGoCoachAppWiringContext, aiColor: StoneColor) {
        val state = context.gameState()
        assertEquals("AI의 돌은 정확히 하나 — 수순: ${state.moves}", 1, state.moves.size)
        val move = state.moves.single()
        assertTrue("AI($aiColor)의 착수여야 한다: $move", move is Move.Play && move.player == aiColor)
        val point = (move as Move.Play).coordinate
        assertTrue("정상 분석의 후보 자리여야 한다: $point", point in StallScriptedEngine.freshCandidates)
        assertEquals("판에 그 돌이 보인다", aiColor, state.stoneAt(point))
        assertEquals("genMove로 몰래 두지 않았다", 0, engine.count("genMove"))
        assertNoStaleStone(context)
    }

    private fun assertNoStaleStone(context: FakeGoCoachAppWiringContext) {
        val state = context.gameState()
        val staleStones = StallScriptedEngine.staleCandidates.filter { state.stoneAt(it) != null }
        assertTrue("버렸어야 할 늦은 답의 돌이 판에 있다: $staleStones", staleStones.isEmpty())
    }

    /** 취소된 차례(무르기)가 판·기록·예약에 아무것도 남기지 않았고, 사람 차례에 AI를 다시 부르지 않았다. */
    private fun assertCancelledTurnLeftNoTrace(context: FakeGoCoachAppWiringContext) {
        assertTrue("무른 판에 AI의 돌이 놓이면 안 된다: ${context.gameState().moves}", context.gameState().moves.isEmpty())
        assertNoStaleStone(context)
        assertEquals("취소된 차례는 genMove로 떨어지지 않는다", 0, engine.count("genMove"))
        assertEquals(0, events(context, "ai_turn_success"))
        assertEquals("취소는 시간 초과가 아니다", 0, events(context, "ai_turn_timeout"))
        assertEquals(0, events(context, "ai_turn_failure"))
        assertNull(context.holder.current.autoAiTurn.timedOut)
        assertFalse(context.holder.current.autoAiTurn.isPending)
        assertFalse(context.engineIsBusy)
        assertEquals("사람 차례에는 AI를 다시 예약하지 않는다", 1, events(context, "ai_turn_schedule"))
    }

    /** forceReset 뒤 첫 분석 전에 새 프로세스를 다시 맞췄다(configure → newGame) — 빈 판의 수를 내지 않는다(설계 F2). */
    private fun assertFreshProcessWasSyncedBeforeAnalysis(calls: List<String>) {
        val afterReset = calls.drop(calls.lastIndexOf("forceReset") + 1).filterNot { it.startsWith("analyze:") }
        val firstAnalysis = afterReset.indexOf("analyze")
        assertTrue("forceReset 뒤 분석이 있어야 한다: $afterReset", firstAnalysis >= 0)
        val beforeAnalysis = afterReset.take(firstAnalysis)
        assertTrue("새 프로세스를 configure로 띄운 뒤 분석한다: $afterReset", "configure" in beforeAnalysis)
        assertTrue("판을 다시 맞춘 뒤 분석한다: $afterReset", "newGame" in beforeAnalysis)
    }

    private fun events(context: FakeGoCoachAppWiringContext, name: String): Int {
        val pattern = Regex("""event=$name\b""")
        return context.runtimeLog.lines.count { pattern.containsMatchIn(it) }
    }

    private fun generationOf(context: FakeGoCoachAppWiringContext): Long = context.holder.current.core.runtimeState.sessionGeneration

    private companion object {
        const val PumpCapMillis = 10_000L
        val HumanBlackAiWhite = PlayerSetup()
        val AiBlackHumanWhite = PlayerSetup(black = SidePlayerSetup(SeatController.Ai), white = SidePlayerSetup(SeatController.Human))
        val BlackAtC3 = Move.Play(StoneColor.Black, BoardCoordinate(row = 2, column = 2))
    }
}
