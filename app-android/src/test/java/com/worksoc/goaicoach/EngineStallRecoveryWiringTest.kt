package com.worksoc.goaicoach

import com.worksoc.goaicoach.application.engine.LocalEngineSessionClient
import com.worksoc.goaicoach.application.safety.EngineStuckWaitAction
import com.worksoc.goaicoach.application.safety.EngineTurnWatchdogAttempt
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
        // 앱이 멈추지 않은 기다림의 시간 초과는 지금처럼 선택을 기다린다 — 멈춤의 두 신호도 적는다(backlog #204 (c)·(e)).
        val timeoutLine = context.runtimeLog.lines.single { it.contains("event=ai_turn_timeout") }
        assertTrue(timeoutLine, timeoutLine.contains("transition=\"keep_current_board_await_choice\""))
        assertTrue(timeoutLine, timeoutLine.contains("backgroundedDuringWait=false processPauseMs=0 "))
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

    /**
     * B1 — **진짜 실패가 같은 국면에서 되풀이된다**(refactor backlog #109 ⓐ). 시간 초과도 취소도 아닌 실패(분석도,
     * 그 차례의 `genMove` 폴백도 실패 — 프로세스가 죽었다 등)는 예전에는 완료 순번이 팝업을 닫고 **조용히 다시**
     * 시도했다. 같은 실패가 되풀이되면 화면에 아무것도 안 보인 채 계속 돌았다(실패 사유는 대국 화면이 그리지 않는
     * `engineMessage`에만 남는다).
     *
     * 첫 실패는 지금처럼 조용히 한 번 더 시도한다 — 죽은 프로세스는 다음 차례의 기동이 거두고 새로 띄운다(#14).
     * **같은 국면에서 두 번째 실패**면 시간 초과와 같은 선택 팝업(상태 B)으로 넘어가 조용한 재시도를 멈추고,
     * 「엔진 다시 시작하기」를 고르면 새 프로세스에서 정상 분석이 둔다.
     */
    @Test
    fun aRealFailureRepeatedOnTheSamePositionStopsTheSilentRetryAndAsksTheUser() {
        val context = newContext(inGameSession(playerSetup = AiBlackHumanWhite, boardSize = BoardSize.Nine))
        engine.failNextTurns(2)

        // ── 첫 시도: 진짜 실패 → 조용히 한 번 더(스스로 낫는 길).
        runAiTurnToItsEnd(context, "첫 시도의 실패")
        assertEquals(1, events(context, "ai_turn_failure"))
        assertEquals("분석이 실패하면 genMove로 떨어진다 — 그것도 실패했다", 1, engine.count("genMove:failed"))
        assertTrue("판은 그대로다", context.gameState().moves.isEmpty())
        assertFalse("첫 실패는 아직 묻지 않는다", screenOf(context).isAwaitingEngineTimeoutChoice)

        runTurnAutomationEffect(context)
        assertEquals("첫 실패 뒤에는 조용히 한 번 더 시도한다", 2, events(context, "ai_turn_schedule"))

        // ── 두 번째 시도: 같은 국면에서 또 실패 → 선택 팝업(상태 B).
        runAiTurnToItsEnd(context, "두 번째 시도의 실패")
        assertEquals(2, events(context, "ai_turn_failure"))
        assertTrue("판은 그대로다", context.gameState().moves.isEmpty())
        assertTrue(
            "같은 국면에서 두 번째 진짜 실패면 선택 팝업이 떠야 한다 — 안 뜨면 화면에 아무것도 없이 계속 돈다",
            screenOf(context).isAwaitingEngineTimeoutChoice,
        )
        assertTrue("차례 대기가 끝났어도 팝업은 남는다", popupVisible(context, elapsedSinceWatchdogBaseMillis = 0L, turnWaitEnded = true))

        runTurnAutomationEffect(context)
        assertEquals("선택을 기다리는 동안 조용한 재시도는 없다", 0, context.dispatcher.queuedCount)
        assertEquals(2, events(context, "ai_turn_schedule"))

        // ── 사용자가 「엔진 다시 시작하기」를 고른다 → 새 프로세스를 맞춘 뒤 정상 분석이 둔다.
        dispatch(context, GameUiEvent.ForceResetEngine)
        pumpUntil(context, "다시 시작한 뒤의 AI 차례") { context.gameState().moves.size == 1 && !context.holder.current.autoAiTurn.isPending }
        drainQueue(context)

        assertAiStoneLandedOnce(context, aiColor = StoneColor.Black, failedGenMoves = 2)
        assertFreshProcessWasSyncedBeforeAnalysis(engine.calls)
        assertFalse(screenOf(context).isAwaitingEngineTimeoutChoice)
        assertEquals(1, events(context, "ai_turn_success"))
    }

    /**
     * B2 — **와치독은 차례가 아니라 시도마다 다시 건다**(refactor backlog #109 ⓑ). 첫 시도가 멎어 팝업이 뜨고, 그 시도가
     * 진짜 실패로 끝나면 팝업은 닫히고(완료 순번) 같은 차례를 조용히 한 번 더 시도한다. 예전에는 그 재시도가 또 멎어도
     * 팝업도 「엔진 다시 시작하기」도 없었다 — 와치독의 보고 표시가 차례 내내 남고, 차례 시작 시각이 키라 바뀌지 않았다.
     * 이제는 앞 시도가 끝난 순간부터 다시 재서 재시도가 한도를 넘기면 **다시** 뜨고, 다시 시작하기로 복구된다.
     */
    @Test
    fun theWatchdogRearmsForTheSilentRetryAfterARealFailureAndFiresAgainWhenThatRetryStalls() {
        val context = newContext(inGameSession(playerSetup = AiBlackHumanWhite, boardSize = BoardSize.Nine))
        val firstAttempt = engine.hangNextAnalysis()
        startAiTurnUntilTheEngineIsStuck(context, firstAttempt)
        val watchdog = WatchdogLoop(context, turnStartedAtMillis = 0L)

        // ── 첫 시도가 멎어 한도를 넘긴다 → 팝업(상태 A).
        assertFalse("한도 전에는 팝업이 없다", watchdog.tick(watchdogThresholdMillis - 1))
        assertTrue("한도를 넘기면 팝업이 뜬다", watchdog.tick(watchdogThresholdMillis))

        // ── 그 시도가 진짜 실패로 끝난다 → 차례는 그대로, 첫 실패라 묻지 않고 조용히 한 번 더 시도한다(#109 ⓐ).
        val retry = engine.wedgeNextAnalysis()
        val completionSeqBefore = screenOf(context).engine.engineTurnWaitCompletionSeq
        firstAttempt.failForReal()
        pumpUntil(context, "첫 시도의 실패") { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)
        assertEquals(1, events(context, "ai_turn_failure"))
        assertTrue("차례 대기가 끝나 완료 순번이 바뀌었다", screenOf(context).engine.engineTurnWaitCompletionSeq != completionSeqBefore)
        assertTrue("차례는 그대로다 — 와치독의 차례 키(차례 시작 시각)는 바뀌지 않는다", context.gameState().moves.isEmpty())
        assertFalse("첫 실패는 아직 묻지 않는다", screenOf(context).isAwaitingEngineTimeoutChoice)
        val failedAtMillis = watchdogThresholdMillis + 3_000L
        assertFalse(watchdog.tick(failedAtMillis))

        runTurnAutomationEffect(context)
        assertEquals("조용한 재시도", 2, events(context, "ai_turn_schedule"))
        assertTrue("재시도의 본문이 돈다", context.dispatcher.runNext())
        assertTrue("재시도가 분석에서 멎어야 한다 — 호출: ${engine.calls}", retry.awaitEntered())

        // ── 재시도도 멎는다 → 앞 시도가 끝난 순간부터 한도만큼 지나면 팝업이 **다시** 뜬다.
        assertFalse("다시 건 순간부터 잰다 — 그 한도 전에는 없다", watchdog.tick(failedAtMillis + watchdogThresholdMillis - 1))
        assertTrue(
            "실패 뒤 조용히 다시 도는 시도가 멎으면 팝업이 다시 떠야 한다 — 안 뜨면 「엔진 다시 시작하기」가 없다",
            watchdog.tick(failedAtMillis + watchdogThresholdMillis),
        )

        // ── 「엔진 다시 시작하기」 → 멎은 재시도는 취소로 끝나고(실패가 아니다), 새 프로세스에서 정상 분석이 둔다.
        dispatch(context, GameUiEvent.ForceResetEngine)
        pumpUntil(context, "취소된 재시도의 정리") { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)
        runTurnAutomationEffect(context)
        pumpUntil(context, "다시 시작한 뒤의 AI 차례") { context.gameState().moves.size == 1 && !context.holder.current.autoAiTurn.isPending }
        drainQueue(context)

        assertAiStoneLandedOnce(context, aiColor = StoneColor.Black, failedGenMoves = 1)
        assertFreshProcessWasSyncedBeforeAnalysis(engine.calls)
        assertEquals("닫힌 파이프의 예외는 실패로 세지 않는다", 1, events(context, "ai_turn_failure"))
    }

    // ── C: 기다리는 사이 앱이 멈췄다(backlog #204) ─────────────────────────────────────────────

    /**
     * C1 — backlog #204 (a)·(e): 엔진을 기다리는 사이 **앱이 얼었다가** 풀리는 순간 마감이 터졌다(동결이 `ON_STOP`보다
     * 먼저 — #202의 취소가 닿지 못하는 경합, 또는 VM 정지). 엔진은 멎지 않았다 — 그 마감은 언 시간을 쟀다. 그래서 팝업
     * 없이(표시·선택 대기 없음) **같은 국면**을 한 번 다시 요청하고, 그 차례가 정상 분석으로 둔다. 로그의
     * `ai_turn_timeout`이 멈춤과 처리를 적는다.
     */
    @Test
    fun aTimeoutAfterTheAppWasFrozenMidWaitRetriesTheSamePositionOnceWithoutThePopup() {
        val context = newContext(inGameSession(playerSetup = AiBlackHumanWhite, boardSize = BoardSize.Nine))
        val hang = engine.hangNextAnalysis()
        startAiTurnUntilTheEngineIsStuck(context, hang)

        // ── 앱이 69초 얼었다 — 풀리는 순간 마감이 이미 지나 있다.
        context.engineWaitPauses.pauseProcess(69_000L)
        hang.passDeadline()
        pumpUntil(context, "시간 초과로 끝난 차례의 정리") { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)

        // ── 팝업 없음: 표시도 선택 대기도 없다. 그 국면의 조용한 재시도 한 번을 썼다.
        val timeoutLine = context.runtimeLog.lines.single { it.contains("event=ai_turn_timeout") }
        assertTrue(timeoutLine, timeoutLine.contains("transition=\"keep_current_board_retry_same_position\""))
        assertTrue(timeoutLine, timeoutLine.contains("backgroundedDuringWait=false"))
        assertTrue(timeoutLine, timeoutLine.contains("processPauseMs=69000"))
        assertNull("멈춘 기다림의 시간 초과에 선택 팝업의 표시를 남기면 오탐이다", context.holder.current.autoAiTurn.timedOut)
        assertFalse(screenOf(context).isAwaitingEngineTimeoutChoice)
        assertFalse("「엔진 응답 지연」이 뜨면 안 된다", popupVisible(context, elapsedSinceWatchdogBaseMillis = 0L, turnWaitEnded = true))
        assertEquals(positionOf(context, moveCount = 0), context.holder.current.autoAiTurn.interruptedRetry)
        assertEquals("멈춤 측정은 기다림이 끝나면 닫힌다(박동을 끈다)", 0, context.engineWaitPauses.openCount)
        assertEquals("시간 초과는 genMove로 덮지 않는다", 0, engine.count("genMove"))

        // ── busy가 풀려 차례 자동화 효과가 돈다 → **같은 국면**을 다시 요청 → 정상 분석이 둔다.
        runTurnAutomationEffect(context)
        assertEquals("같은 국면을 한 번 다시 요청한다", 2, events(context, "ai_turn_schedule"))
        pumpUntil(context, "다시 요청한 AI 차례") { context.gameState().moves.size == 1 && !context.holder.current.autoAiTurn.isPending }
        drainQueue(context)

        assertAiStoneLandedOnce(context, aiColor = StoneColor.Black)
        assertEquals("같은 국면을 한 번 더 분석했다", 2, engine.count("analyze"))
        assertEquals(1, events(context, "ai_turn_success"))
        assertEquals(1, events(context, "ai_turn_timeout"))
        assertFalse(screenOf(context).isAwaitingEngineTimeoutChoice)
    }

    /**
     * C2 — backlog #204 (a): **포그라운드 세대**가 판정까지 닿는다(배선). 기다리는 사이 앱이 화면을 떠났다 돌아온 것으로
     * 수명 컨트롤러의 세대만 올린다. ⚠️ 앱의 `onAppBackgrounded`는 같은 메인 스레드에서 도는 차례를 먼저 **취소**하므로(#202 —
     * `AppBackgroundAiTurnWiringTest`) 앱 경로로는 이 순서가 나오지 않는다. 여기서는 세대라는 신호가 끊기지 않고 판정에
     * 닿는지만 본다 — 실제 경합은 C1(멈춤 박동)이 잡는다.
     */
    @Test
    fun aForegroundGenerationChangeDuringTheWaitReachesTheTimeoutClassification() {
        val context = newContext(inGameSession(playerSetup = AiBlackHumanWhite, boardSize = BoardSize.Nine))
        val hang = engine.hangNextAnalysis()
        startAiTurnUntilTheEngineIsStuck(context, hang)

        context.lifecycleController.markAppInForeground(false)
        context.lifecycleController.markAppInForeground(true)
        hang.passDeadline()
        pumpUntil(context, "시간 초과로 끝난 차례의 정리") { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)

        val timeoutLine = context.runtimeLog.lines.single { it.contains("event=ai_turn_timeout") }
        assertTrue(timeoutLine, timeoutLine.contains("backgroundedDuringWait=true"))
        assertTrue(timeoutLine, timeoutLine.contains("processPauseMs=0"))
        assertTrue(timeoutLine, timeoutLine.contains("transition=\"keep_current_board_retry_same_position\""))
        assertFalse(screenOf(context).isAwaitingEngineTimeoutChoice)

        runTurnAutomationEffect(context)
        pumpUntil(context, "다시 요청한 AI 차례") { context.gameState().moves.size == 1 && !context.holder.current.autoAiTurn.isPending }
        drainQueue(context)
        assertAiStoneLandedOnce(context, aiColor = StoneColor.Black)
    }

    /**
     * C3 — backlog #204 (b): 조용한 재시도는 **국면마다 한 번**이다. 다시 요청한 차례가 또 시간 초과면 — 또 얼었더라도 —
     * 지금처럼 「엔진 응답 지연」(상태 B)이고, 조용히 세 번째를 돌리지 않는다. 사용자가 「한 번 더 기다리기」를 고르면 그
     * 국면의 조용한 재시도도 다시 쓸 수 있게 되고, 정상 분석이 둔다.
     */
    @Test
    fun aSecondTimeoutOnTheSamePositionShowsThePopupEvenIfTheAppWasFrozenAgain() {
        val context = newContext(inGameSession(playerSetup = AiBlackHumanWhite, boardSize = BoardSize.Nine))
        val first = engine.hangNextAnalysis()
        startAiTurnUntilTheEngineIsStuck(context, first)
        val retry = engine.hangNextAnalysis()

        // ── 첫 시도: 얼었다 → 조용한 재시도.
        context.engineWaitPauses.pauseProcess(69_000L)
        first.passDeadline()
        pumpUntil(context, "첫 시도의 정리") { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)
        assertFalse(screenOf(context).isAwaitingEngineTimeoutChoice)
        runTurnAutomationEffect(context)
        assertEquals(2, events(context, "ai_turn_schedule"))
        assertTrue("재시도의 본문이 돈다", context.dispatcher.runNext())
        assertTrue("재시도가 분석에서 멎어야 한다 — 호출: ${engine.calls}", retry.awaitEntered())

        // ── 재시도도 얼었다가 마감 → 이번엔 팝업.
        context.engineWaitPauses.pauseProcess(69_000L)
        retry.passDeadline()
        pumpUntil(context, "재시도의 정리") { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)

        val timeoutLines = context.runtimeLog.lines.filter { it.contains("event=ai_turn_timeout") }
        assertEquals(2, timeoutLines.size)
        assertTrue(timeoutLines.last(), timeoutLines.last().contains("transition=\"keep_current_board_await_choice\""))
        assertTrue(timeoutLines.last(), timeoutLines.last().contains("processPauseMs=69000"))
        assertEquals(positionOf(context, moveCount = 0), context.holder.current.autoAiTurn.timedOut)
        assertTrue("두 번째 시간 초과는 지금처럼 선택 팝업이다", screenOf(context).isAwaitingEngineTimeoutChoice)
        assertTrue(popupVisible(context, elapsedSinceWatchdogBaseMillis = 0L, turnWaitEnded = true))
        runTurnAutomationEffect(context)
        assertEquals("선택을 기다리는 동안 조용한 세 번째는 없다", 0, context.dispatcher.queuedCount)
        assertEquals(2, events(context, "ai_turn_schedule"))

        // ── 사용자가 「한 번 더 기다리기」 → 그 국면의 조용한 재시도도 다시 쓸 수 있다 → 정상 분석이 둔다.
        dispatch(context, GameUiEvent.RetryTimedOutAiTurn)
        assertNull("고르면 그 국면의 조용한 재시도를 다시 쓸 수 있다", context.holder.current.autoAiTurn.interruptedRetry)
        pumpUntil(context, "다시 요청한 AI 차례") { context.gameState().moves.size == 1 && !context.holder.current.autoAiTurn.isPending }
        drainQueue(context)
        assertAiStoneLandedOnce(context, aiColor = StoneColor.Black)
        assertEquals(3, engine.count("analyze"))
    }

    /**
     * C4 — backlog #204 (d): 조용한 재시도는 국면에 묶여 있어 **국면이 바뀌면 다시 쓸 수 있다**. 한 국면에서 쓴 뒤 세대가
     * 오르면(무르기·새 대국·이어하기 — 판은 같은 빈 판이어도 다른 국면이다) 그 국면의 시간 초과도 얼었던 것이면 다시 조용히
     * 한 번 넘긴다.
     */
    @Test
    fun theSilentRetryIsAvailableAgainOnceThePositionChanges() {
        val context = newContext(inGameSession(playerSetup = AiBlackHumanWhite, boardSize = BoardSize.Nine))
        val first = engine.hangNextAnalysis()
        startAiTurnUntilTheEngineIsStuck(context, first)
        context.engineWaitPauses.pauseProcess(69_000L)
        first.passDeadline()
        pumpUntil(context, "첫 시도의 정리") { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)
        val spentAt = positionOf(context, moveCount = 0)
        assertEquals(spentAt, context.holder.current.autoAiTurn.interruptedRetry)

        // ── 국면이 바뀐다 — 세대가 오른다(무르기·새 대국이 하는 일).
        context.changeCore { core -> core.copy(runtimeState = core.runtimeState.copy(sessionGeneration = core.runtimeState.sessionGeneration + 1)) }
        val next = engine.hangNextAnalysis()
        runTurnAutomationEffect(context)
        assertTrue("새 국면의 차례 본문이 돈다", context.dispatcher.runNext())
        assertTrue("새 국면의 차례가 분석에서 멎어야 한다 — 호출: ${engine.calls}", next.awaitEntered())
        context.engineWaitPauses.pauseProcess(69_000L)
        next.passDeadline()
        pumpUntil(context, "새 국면 차례의 정리") { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)

        val timeoutLines = context.runtimeLog.lines.filter { it.contains("event=ai_turn_timeout") }
        assertEquals(2, timeoutLines.size)
        assertTrue(timeoutLines.last(), timeoutLines.last().contains("transition=\"keep_current_board_retry_same_position\""))
        assertNull("새 국면에서는 다시 조용히 한 번 넘긴다 — 팝업 없음", context.holder.current.autoAiTurn.timedOut)
        assertFalse(screenOf(context).isAwaitingEngineTimeoutChoice)
        assertTrue(positionOf(context, moveCount = 0) != spentAt)
        assertEquals(positionOf(context, moveCount = 0), context.holder.current.autoAiTurn.interruptedRetry)

        runTurnAutomationEffect(context)
        pumpUntil(context, "다시 요청한 AI 차례") { context.gameState().moves.size == 1 && !context.holder.current.autoAiTurn.isPending }
        drainQueue(context)
        assertAiStoneLandedOnce(context, aiColor = StoneColor.Black)
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

    /** 예약된(또는 지금 예약하는) AI 차례 하나를 끝까지 돌린다 — 예약·busy가 풀리고 꼬리까지. */
    private fun runAiTurnToItsEnd(context: FakeGoCoachAppWiringContext, what: String) {
        if (context.dispatcher.queuedCount == 0) runTurnAutomationEffect(context)
        assertTrue("$what — 예약된 차례의 본문이 돈다", context.dispatcher.runNext())
        pumpUntil(context, what) { !context.holder.current.autoAiTurn.isPending && !context.engineIsBusy }
        drainQueue(context)
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
                    engineTurnWaitCompletionSeq = context.engineTurnWaitCompletionSeq,
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

    /**
     * `GamePlaySection`의 와치독 루프를 **같은 순수 규칙**으로 돌린다(refactor backlog #109). 효과가 시작될 때 차례 시작
     * 시각과 지금의 완료 순번으로 시도를 걸고, 틱마다 [EngineTurnWatchdogAttempt.observe]로 순번을 본 뒤, 한도를 넘기면
     * 그 시도에서 한 번 보고한다. 틱 사이의 시간은 테스트가 숫자로 준다.
     */
    private inner class WatchdogLoop(private val context: FakeGoCoachAppWiringContext, turnStartedAtMillis: Long) {
        private var attempt = EngineTurnWatchdogAttempt(
            baseMillis = turnStartedAtMillis,
            completionSeq = screenOf(context).engine.engineTurnWaitCompletionSeq,
        )

        /** [nowMillis]의 한 틱 — 이번 틱에 팝업을 띄웠으면 참. */
        fun tick(nowMillis: Long): Boolean {
            val screen = screenOf(context)
            attempt = attempt.observe(nowMillis = nowMillis, completionSeq = screen.engine.engineTurnWaitCompletionSeq)
            if (attempt.isReported) return false
            val isAiTurn = when (context.turnTimeState().currentTurnPlayer) {
                StoneColor.Black -> screen.playerSetup.black.controller == SeatController.Ai
                StoneColor.White -> screen.playerSetup.white.controller == SeatController.Ai
            }
            val fired = isEngineTurnWatchdogTriggered(
                isAiTurn = isAiTurn,
                elapsedSinceTurnStartMillis = attempt.elapsedMillis(nowMillis),
                searchTimeLimit = screen.searchTimeSettings.limit,
                isResolvingEndgame = screen.gameState.hasConsecutivePasses() || screen.gameState.isBoardFull(),
            )
            if (fired) attempt = attempt.reported()
            return fired
        }
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

    /**
     * 수가 **정확히 하나** 늘었고, 그것이 정상 분석의 후보 자리에 놓인 AI의 돌이다.
     * @param failedGenMoves 앞선 진짜 실패들의 `genMove` 폴백(전부 실패했다, #109) — 그 밖의 `genMove`는 없어야 한다.
     */
    private fun assertAiStoneLandedOnce(context: FakeGoCoachAppWiringContext, aiColor: StoneColor, failedGenMoves: Int = 0) {
        val state = context.gameState()
        assertEquals("AI의 돌은 정확히 하나 — 수순: ${state.moves}", 1, state.moves.size)
        val move = state.moves.single()
        assertTrue("AI($aiColor)의 착수여야 한다: $move", move is Move.Play && move.player == aiColor)
        val point = (move as Move.Play).coordinate
        assertTrue("정상 분석의 후보 자리여야 한다: $point", point in StallScriptedEngine.freshCandidates)
        assertEquals("판에 그 돌이 보인다", aiColor, state.stoneAt(point))
        assertEquals("genMove로 몰래 두지 않았다", failedGenMoves, engine.count("genMove"))
        assertEquals(failedGenMoves, engine.count("genMove:failed"))
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

    /** 지금 세대에서 수순 길이 [moveCount]인 국면 — 시간 초과 표시·조용한 재시도(backlog #204)가 묶이는 열쇠. */
    private fun positionOf(context: FakeGoCoachAppWiringContext, moveCount: Int): AutoAiTurnTimeout =
        AutoAiTurnTimeout(sessionGeneration = generationOf(context), moveCount = moveCount)

    private companion object {
        const val PumpCapMillis = 10_000L
        val HumanBlackAiWhite = PlayerSetup()
        val AiBlackHumanWhite = PlayerSetup(black = SidePlayerSetup(SeatController.Ai), white = SidePlayerSetup(SeatController.Human))
        val BlackAtC3 = Move.Play(StoneColor.Black, BoardCoordinate(row = 2, column = 2))
    }
}
