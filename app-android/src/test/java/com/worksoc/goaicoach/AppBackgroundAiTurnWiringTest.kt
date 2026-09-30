package com.worksoc.goaicoach

import com.worksoc.goaicoach.application.engine.AutoAiTurnResult
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
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
 * backlog #202 — 앱이 백그라운드로 가면 도는 AI 차례를 **취소**하고, 백그라운드 동안 새 차례를 띄우지 않고,
 * 돌아오면 다시 요청한다. 실제 배선([wireGoCoachControllers]) 위에서 잰다 — 컨트롤러는 다시 배선되므로
 * 부를 때마다 새로 배선한다(`AutoAiTurnCancellationWiringTest`와 같은 이유).
 *
 * 사연(2026-09-30 AI 대 AI 리포트): 백그라운드에서 탐색하던 중 안드로이드가 앱과 KataGo를 동결했고, 36분 뒤 복귀하는
 * 순간 동결 중에도 흐른 30초 마감이 터져 멀쩡한 엔진을 내리고 「엔진 응답 지연」이 떴다.
 */
class AppBackgroundAiTurnWiringTest {
    @Test
    fun goingToTheBackgroundWhileTheAiThinksCancelsTheTurnWithoutATimeout() {
        val context = thinkingAiContext()

        wireGoCoachControllers(context).autoAiTurnController.onAppBackgrounded()
        drain(context)

        assertFalse("취소된 차례도 예약 표시를 푼다", context.autoAiTurnWrites.last().isPending)
        assertFalse("취소된 차례도 busy를 푼다", context.engineIsBusy)
        assertTrue(context.diagnosticLog.events.any { it.code == "engine_operation_cancelled" })
        assertTrue("취소는 시간 초과가 아니다", context.runtimeLog.lines.none { it.contains("event=ai_turn_timeout") })
        assertEquals(null, context.autoAiTurnWrites.last().timedOut)
        assertTrue(
            "리포트만으로 동결을 가를 수 있게 전환을 적는다",
            context.runtimeLog.lines.any { it.contains("event=app_background") && it.contains("cancelled=true") },
        )
    }

    /** 취소된 차례가 busy를 풀면 앱의 트리거 효과가 [requestAiTurn]을 다시 부른다 — 백그라운드면 띄우지 않는다. */
    @Test
    fun noNewTurnStartsWhileInTheBackgroundAndTheTurnIsRequestedAgainOnReturn() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = AiBlackHumanWhite))
        context.engineIsReady = true
        wireGoCoachControllers(context).autoAiTurnController.onAppBackgrounded()

        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()
        assertTrue("백그라운드에서는 예약하지 않는다", context.autoAiTurnWrites.isEmpty())
        assertEquals(0, context.dispatcher.queuedCount)

        wireGoCoachControllers(context).autoAiTurnController.onAppForegrounded()

        assertEquals("돌아오면 같은 국면을 다시 요청한다", listOf(true), context.autoAiTurnWrites.map { it.isPending })
        assertEquals(1, context.dispatcher.queuedCount)
        assertTrue(context.runtimeLog.lines.any { it.contains("event=app_foreground") })
    }

    /** 사람 차례면 돌아와도 AI 차례를 띄우지 않는다 — 다시 요청은 평소의 요청 규칙을 그대로 탄다. */
    @Test
    fun returningOnAHumanTurnSchedulesNothing() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))
        context.engineIsReady = true

        wireGoCoachControllers(context).autoAiTurnController.onAppForegrounded()

        assertTrue(context.autoAiTurnWrites.isEmpty())
        assertEquals(0, context.dispatcher.queuedCount)
    }

    /**
     * 양패스 뒤 계가 중이면 취소하지 않는다 — 계가는 이미 둔 수 뒤의 정리라 다시 요청할 길이 없어 판이 계가 중에
     * 멈춘다. 늦어져도 로컬 계가로 떨어질 뿐 팝업은 없다.
     */
    @Test
    fun anEndgameResolutionInFlightIsNotCancelled() {
        val context = thinkingAiContext()
        context.changeCore { core -> core.copy(gameState = core.gameState.play(BlackPass).play(WhitePass)) }

        wireGoCoachControllers(context).autoAiTurnController.onAppBackgrounded()

        assertTrue("계가 중인 작업을 취소하면 안 된다", context.diagnosticLog.events.none { it.code == "engine_operation_cancelled" })
        assertTrue(
            context.runtimeLog.lines.any {
                it.contains("event=app_background") && it.contains("cancelled=false") && it.contains("resolvingEndgame=true")
            },
        )
    }

    @Test
    fun goingToTheBackgroundWithNoTurnInFlightOnlyLogsTheTransition() {
        val context = FakeGoCoachAppWiringContext(inGameSession(playerSetup = HumanBlackAiWhite))

        wireGoCoachControllers(context).autoAiTurnController.onAppBackgrounded()

        assertTrue(context.diagnosticLog.events.isEmpty())
        assertTrue(
            context.runtimeLog.lines.any {
                it.contains("event=app_background") && it.contains("aiTurnInFlight=false") && it.contains("cancelled=false")
            },
        )
    }

    /** AI(흑)가 엔진 안에서 생각하는 중 — 엔진은 취소될 때까지 답하지 않는다. */
    private fun thinkingAiContext(): FakeGoCoachAppWiringContext {
        val context = FakeGoCoachAppWiringContext(
            inGameSession(playerSetup = AiBlackHumanWhite),
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
        context.engineIsReady = true
        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()
        check(context.dispatcher.runNext()) { "검증을 지나 엔진 호출에 들어간다" }
        check(context.engineIsBusy) { "AI가 생각하는 중이면 엔진이 바쁘다" }
        return context
    }

    private fun drain(context: FakeGoCoachAppWiringContext) {
        while (context.autoAiTurnWrites.last().isPending && context.dispatcher.runNextArrivingWithin(2_000L)) Unit
    }

    private companion object {
        val HumanBlackAiWhite = PlayerSetup()
        val AiBlackHumanWhite = PlayerSetup(black = SidePlayerSetup(SeatController.Ai), white = SidePlayerSetup(SeatController.Human))
        val BlackPass = Move.Pass(StoneColor.Black)
        val WhitePass = Move.Pass(StoneColor.White)
    }
}
