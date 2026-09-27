package com.worksoc.goaicoach

import com.worksoc.goaicoach.application.engine.AutoAiTurnResult
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

    @Test
    fun cancelWithNoTurnInFlightDoesNothing() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))
        val controllers = wireGoCoachControllers(context)

        controllers.autoAiTurnController.cancelInFlightTurn()

        assertTrue(context.diagnosticLog.events.isEmpty())
        assertEquals(0, context.dispatcher.queuedCount)
    }

    private companion object {
        val HumanBlackAiWhite = PlayerSetup()
        val AiBlackHumanWhite = PlayerSetup(black = SidePlayerSetup(SeatController.Ai), white = SidePlayerSetup(SeatController.Human))
        val BlackAtThreeThree = Move.Play(StoneColor.Black, BoardCoordinate(row = 2, column = 2))
    }
}
