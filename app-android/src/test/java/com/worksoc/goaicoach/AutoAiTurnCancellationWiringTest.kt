package com.worksoc.goaicoach

import com.worksoc.goaicoach.application.engine.AutoAiTurnResult
import com.worksoc.goaicoach.application.session.AutoAiTurnTimeout
import com.worksoc.goaicoach.application.session.AutoAiTurnUiState
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.testsupport.FakeEngineSessionClient
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T8(refactor backlog #74) — AI 차례의 **실제 취소**가 실제 배선([wireGoCoachControllers])에서 닿는지.
 *
 * ⚠️ 설계는 Job을 `AutoAiTurnController`에 두라고 했지만, 그 컨트롤러는 `GoCoachApp`의
 * `remember(wiringContext)`로 세션 스냅샷이 바뀔 때마다 새로 만들어진다 — 예약하는 순간 pending이 바뀌므로
 * 무르기·나가기가 닿는 인스턴스는 **이미 다른 인스턴스**다. 그래서 Job은 키 없는 `remember`인
 * `EngineOperationLifecycleController`에 맡긴다. 첫 테스트가 그 이유를 그대로 재현한다(배선을 두 번 한다).
 */
class AutoAiTurnCancellationWiringTest {
    @Test
    fun cancelThroughARewiredControllerReachesTheTurnAnEarlierInstanceLaunched() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = AiBlackHumanWhite))
        context.engineIsReady = true
        val first = wireGoCoachControllers(context)
        first.autoAiTurnController.requestAiTurn()
        assertEquals(listOf(true), context.autoAiTurnWrites.map { it.isPending })
        assertEquals("AI 착수 블록이 한 번 걸려야 한다", 1, context.dispatcher.queuedCount)

        // 앱은 pending이 바뀐 순간 wiringContext를 새로 만들고 컨트롤러를 다시 배선한다.
        val rewired = wireGoCoachControllers(context)
        rewired.autoAiTurnController.cancelInFlightTurn()
        assertTrue("취소된 launch도 줄에서 한 번은 돈다(본문에는 들어가지 않는다)", context.dispatcher.runNext())

        assertFalse(
            "다시 배선된 컨트롤러의 취소가 앞 인스턴스가 띄운 차례에 닿지 않았다 — pending이 남으면 AI가 다시는 두지 않는다",
            context.autoAiTurnWrites.last().isPending,
        )
        assertTrue("취소된 차례는 시작하지 않는다", context.runtimeLog.lines.none { it.contains("event=ai_turn_begin") })
        assertTrue(context.diagnosticLog.events.any { it.code == "engine_operation_cancelled" })
    }

    /**
     * 무르기 — AI가 **엔진 안에서 생각하는 중**(검증을 지나 엔진 호출에 들어간 뒤)이면 그 Job을 취소한다.
     * 엔진은 취소될 때까지 답하지 않는다. 취소가 닿으면 Job이 끝나며 `finally`가 busy·예약 표시를 푼다.
     */
    @Test
    fun undoWhileTheAiIsThinkingCancelsTheTurnAndItsCleanupRuns() {
        val context = FakeGoCoachAppWiringContext(
            inGameSession(playerSetup = HumanBlackAiWhite),
            engineClient = object : FakeEngineSessionClient() {
                override suspend fun runAutoAiTurn(
                    currentState: GameState,
                    playLevel: PlayLevelSetting,
                    currentProfile: EngineProfile,
                    searchTimeSettings: SearchTimeSettings,
                    searchMode: EngineSearchMode,
                    isolateSearchCache: Boolean,
                ): AutoAiTurnResult = awaitCancellation()
            },
        )
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtThreeThree)) }
        context.engineIsReady = true
        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()
        assertTrue("검증을 지나 엔진 호출에 들어간다", context.dispatcher.runNext())
        assertTrue("AI가 생각하는 중이면 엔진이 바쁘다", context.engineIsBusy)

        wireGoCoachControllers(context).undoController.undoLastTurn()
        while (context.autoAiTurnWrites.last().isPending && context.dispatcher.runNextArrivingWithin(2_000L)) Unit

        assertTrue("판이 한 수 앞으로 돌아가야 한다", context.coreWrites.last().gameState.moves.isEmpty())
        assertFalse("무르기가 AI 차례를 멈추지 않았다 — pending이 남는다", context.autoAiTurnWrites.last().isPending)
        assertFalse("취소된 차례도 작업 완료를 적어 busy를 푼다", context.engineIsBusy)
        assertTrue("무른 판에 AI의 수가 적용되면 안 된다", context.runtimeLog.lines.none { it.contains("event=ai_turn_success") })
    }

    /**
     * 시간 초과로 끝난 국면(상태 B)은 트리거 효과가 다시 불러도 **조용히 다시 탐색하지 않는다**(설계 C-8). 팝업의
     * 「한 번 더 기다리기」(`retryTimedOutTurn`)가 표시를 지우고 같은 국면을 다시 요청한다.
     */
    @Test
    fun aTimedOutPositionIsNotRetriedSilentlyButTheWaitChoiceRetriesIt() {
        val context = timedOutAiToMoveContext()

        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()
        assertTrue("시간 초과 표시가 맞는 동안은 예약하지 않는다", context.autoAiTurnWrites.isEmpty())
        assertEquals(0, context.dispatcher.queuedCount)

        wireGoCoachControllers(context).autoAiTurnController.retryTimedOutTurn()

        assertEquals("표시를 지운 뒤 예약한다", listOf(false, true), context.autoAiTurnWrites.map { it.isPending })
        assertEquals(null, context.autoAiTurnWrites.last().timedOut)
        assertEquals("같은 국면을 다시 요청한다", 1, context.dispatcher.queuedCount)
    }

    /** 「엔진 다시 시작하기」(상태 B) — 엔진을 내리고, 표시를 지우고, 같은 국면을 다시 요청한다. */
    @Test
    fun restartAfterATimedOutTurnResetsTheEngineClearsTheMarkAndRequestsTheTurn() {
        val context = timedOutAiToMoveContext()
        var resets = 0

        wireGoCoachControllers(context).autoAiTurnController.restartEngineForStalledTurn { resets += 1 }

        assertEquals(1, resets)
        assertEquals(null, context.autoAiTurnWrites.last().timedOut)
        assertTrue(context.autoAiTurnWrites.last().isPending)
        assertEquals(1, context.dispatcher.queuedCount)
    }

    /**
     * 「엔진 다시 시작하기」(상태 A — 차례가 아직 돈다) — **취소가 먼저, 엔진 내리기가 나중**이다. 그래야 파이프가
     * 닫혀 풀린 읽기의 예외가 진짜 실패로 읽혀 맞추지 않은 새 프로세스에서 genMove로 떨어지지 않는다(설계 F2).
     * 도는 차례가 있으니 여기서 새로 예약하지 않는다(취소된 차례가 끝나 busy가 풀리면 트리거 효과가 맡는다).
     */
    @Test
    fun restartWhileTheTurnRunsCancelsItBeforeResettingTheEngineAndDoesNotScheduleASecondTurn() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = AiBlackHumanWhite))
        context.engineIsReady = true
        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()
        var cancelledBeforeReset: Boolean? = null

        wireGoCoachControllers(context).autoAiTurnController.restartEngineForStalledTurn {
            cancelledBeforeReset = context.diagnosticLog.events.any { it.code == "engine_operation_cancelled" }
        }

        assertEquals(true, cancelledBeforeReset)
        assertEquals("두 번째 차례를 예약하지 않는다", 1, context.dispatcher.queuedCount)
    }

    @Test
    fun cancelWithNoTurnInFlightDoesNothing() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))
        val controllers = wireGoCoachControllers(context)

        controllers.autoAiTurnController.cancelInFlightTurn()

        assertTrue(context.diagnosticLog.events.isEmpty())
        assertEquals(0, context.dispatcher.queuedCount)
    }

    /** AI(흑) 차례인 빈 판에서, 이 국면의 탐색이 이미 시간 초과로 끝나 선택을 기다리는 상태(상태 B). */
    private fun timedOutAiToMoveContext(): FakeGoCoachAppWiringContext {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = AiBlackHumanWhite))
        context.engineIsReady = true
        context.changeSession { session ->
            session.withAutoAiTurn(
                AutoAiTurnUiState(
                    timedOut = AutoAiTurnTimeout(
                        sessionGeneration = session.core.runtimeState.sessionGeneration,
                        moveCount = session.gameState.moves.size,
                    ),
                ),
            )
        }
        check(context.holder.current.isAwaitingAutoAiTurnTimeoutChoice)
        return context
    }

    private companion object {
        val HumanBlackAiWhite = PlayerSetup()
        val AiBlackHumanWhite = PlayerSetup(black = SidePlayerSetup(SeatController.Ai), white = SidePlayerSetup(SeatController.Human))
        val BlackAtThreeThree = Move.Play(StoneColor.Black, BoardCoordinate(row = 2, column = 2))
    }
}
