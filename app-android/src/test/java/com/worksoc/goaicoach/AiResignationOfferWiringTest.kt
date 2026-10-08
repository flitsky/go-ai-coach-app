package com.worksoc.goaicoach

import com.worksoc.goaicoach.application.engine.AutoAiTurnResult
import com.worksoc.goaicoach.application.session.AutoAiTurnTimeout
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.testsupport.FakeEngineSessionClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI의 **기권 제안**이 실제 배선([wireGoCoachControllers])에서 도는지(backlog #213, 사용자 2026-10-07).
 *
 * 사용자가 정한 것: 대국 중반에 가망이 없으면 AI가 **한 번** 기권을 제안하고, 사용자가 받아들이거나 계속 두게 고른다.
 * 엔진은 판단만 싣는다(`AutoAiTurnResult.offersResignation`) — 여기서 보는 것은 그 뒤다:
 * - 제안은 **AI의 다음 차례**에 선다(받아들이면 AI가 제 차례에 기권할 수 있게). 그동안 AI는 두지 않는다.
 * - 받아들이면 AI의 기권으로 판이 끝난다. 거절하면 AI가 그대로 두고, 이 대국에서는 다시 묻지 않는다.
 * - 답할 사람이 없는 판(AI끼리)에서는 묻지 않는다.
 */
class AiResignationOfferWiringTest {
    @Test
    fun theOfferWaitsAtTheAisNextTurnAndHoldsTheAiUntilTheUserAnswers() {
        val context = offeringContext()

        playAiTurn(context)

        val generation = generationOf(context)
        assertEquals("AI(백)의 돌이 놓였다", 2, context.gameState().moves.size)
        assertEquals(
            "제안은 AI의 다음 차례(사용자가 한 수 둔 뒤)에 선다",
            AutoAiTurnTimeout(sessionGeneration = generation, moveCount = 3),
            context.holder.current.autoAiTurn.resignationOffer,
        )
        assertFalse("사용자가 둘 차례에는 아직 묻지 않는다", context.holder.current.isAwaitingAiResignationChoice)

        humanPlays(context, row = 6, column = 6)

        assertTrue("AI가 둘 차례가 되면 답을 기다린다", context.holder.current.isAwaitingAiResignationChoice)
        val scheduledBefore = context.autoAiTurnWrites.count { it.isPending }
        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()
        assertEquals("답을 기다리는 동안 AI는 두지 않는다", scheduledBefore, context.autoAiTurnWrites.count { it.isPending })
        assertEquals(0, context.dispatcher.queuedCount)
    }

    @Test
    fun acceptingEndsTheGameByTheAisResignation() {
        val context = offeringContext()
        playAiTurn(context)
        humanPlays(context, row = 6, column = 6)

        wireGoCoachControllers(context).autoAiTurnController.answerResignationOffer(accepted = true)

        assertEquals("AI(백)가 제 차례에 기권한다", Move.Resign(StoneColor.White), context.gameState().moves.last())
        assertTrue("판이 그 자리에서 끝난다", context.holder.current.core.isGameEnded)
        assertFalse(context.holder.current.isAwaitingAiResignationChoice)
        assertNull(context.holder.current.autoAiTurn.resignationOffer)
        assertEquals("끝난 판에서 AI 차례를 예약하지 않는다", 0, context.dispatcher.queuedCount)
        // 리포트에 제안과 답이 남는다 — 제안은 그 수의 성공 줄에, 답은 제 줄에.
        assertTrue(context.runtimeLog.lines.any { it.contains("event=ai_turn_success") && it.contains("offersResignation=true ") })
        val answer = context.runtimeLog.lines.single { it.contains("event=ai_resignation_answer") }
        assertTrue(answer, answer.contains("transition=\"end_game_by_ai_resignation\"") && answer.contains("accepted=true"))
    }

    @Test
    fun decliningLetsTheAiPlayOnAndTheSameGameIsNotAskedAgain() {
        val context = offeringContext()
        playAiTurn(context)
        humanPlays(context, row = 6, column = 6)

        wireGoCoachControllers(context).autoAiTurnController.answerResignationOffer(accepted = false)

        assertFalse("고르자마자 대기가 풀린다", context.holder.current.isAwaitingAiResignationChoice)
        assertEquals("거절하면 AI 차례를 바로 요청한다", 1, context.dispatcher.queuedCount)
        assertEquals(generationOf(context), context.holder.current.autoAiTurn.resignationAnsweredGeneration)
        val answer = context.runtimeLog.lines.single { it.contains("event=ai_resignation_answer") }
        assertTrue(answer, answer.contains("transition=\"request_ai_turn\"") && answer.contains("accepted=false"))

        // 엔진은 여전히 가망 없다고 싣는다 — 그래도 이 대국에서는 다시 묻지 않는다.
        playAiTurn(context)
        assertEquals("AI가 계속 둔다", 4, context.gameState().moves.size)
        assertNull("한 판에 한 번만 묻는다", context.holder.current.autoAiTurn.resignationOffer)
        humanPlays(context, row = 7, column = 7)
        assertFalse(context.holder.current.isAwaitingAiResignationChoice)
    }

    @Test
    fun anAnswerThatArrivesAfterThePositionChangedDoesNothing() {
        val context = offeringContext()
        playAiTurn(context)

        // 아직 사용자가 둘 차례다 — 팝업이 떠 있을 국면이 아니다.
        wireGoCoachControllers(context).autoAiTurnController.answerResignationOffer(accepted = true)

        assertEquals("판은 그대로다", 2, context.gameState().moves.size)
        assertFalse(context.holder.current.core.isGameEnded)
        assertEquals("제안은 남아 다음 AI 차례에 선다", 3, context.holder.current.autoAiTurn.resignationOffer?.moveCount)
        assertTrue("답하지 않은 것은 적지 않는다", context.runtimeLog.lines.none { it.contains("event=ai_resignation_answer") })
    }

    @Test
    fun aGameWithNoHumanSeatIsNeverAsked() {
        val context = offeringContext(playerSetup = AiBlackAiWhite, firstMoveByTheHuman = false)

        playAiTurn(context)

        assertEquals(1, context.gameState().moves.size)
        assertNull("답할 사람이 없는 판에서는 묻지 않는다", context.holder.current.autoAiTurn.resignationOffer)
    }

    /** 사람(흑)이 한 수 둔 판 — AI(백)의 차례마다 엔진이 「기권을 제안한다」고 싣는다. */
    private fun offeringContext(
        playerSetup: PlayerSetup = HumanBlackAiWhite,
        firstMoveByTheHuman: Boolean = true,
    ): FakeGoCoachAppWiringContext {
        val context = FakeGoCoachAppWiringContext(
            inGameSession(playerSetup = playerSetup, boardSize = BoardSize.Nine),
            engineClient = AlwaysOfferingEngine(),
        )
        if (firstMoveByTheHuman) humanPlays(context, row = 2, column = 2)
        context.engineIsReady = true
        return context
    }

    /** 설정 화면이 아닌 판 위의 사용자 — 바깥에서 한 수를 올린다(기록에는 남지 않는다). */
    private fun humanPlays(context: FakeGoCoachAppWiringContext, row: Int, column: Int) {
        context.changeCore { core ->
            core.copy(gameState = core.gameState.play(Move.Play(core.gameState.nextPlayer, BoardCoordinate(row = row, column = column))))
        }
    }

    /** AI 차례를 요청하고(이미 예약돼 있으면 그것을) 끝까지 돌린다. */
    private fun playAiTurn(context: FakeGoCoachAppWiringContext) {
        if (context.dispatcher.queuedCount == 0) wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()
        assertTrue("AI 차례가 예약된다", context.dispatcher.runNext())
        val deadline = System.nanoTime() + PumpCapMillis * 1_000_000L
        while (context.holder.current.autoAiTurn.isPending || context.engineIsBusy) {
            val remainingMillis = (deadline - System.nanoTime()) / 1_000_000L
            check(remainingMillis > 0L) { "AI 차례가 ${PumpCapMillis}ms 안에 끝나지 않았다\n" + context.runtimeLog.lines.takeLast(6).joinToString("\n") }
            context.dispatcher.runNextArrivingWithin(remainingMillis)
        }
        while (context.dispatcher.runNext()) Unit
    }

    private fun generationOf(context: FakeGoCoachAppWiringContext): Long = context.holder.current.core.runtimeState.sessionGeneration

    /** 빈 자리 하나에 두고, 늘 「기권을 제안한다」고 싣는 엔진 — 판단 자체(`LosingStreak`)는 `:shared`의 테스트가 본다. */
    private class AlwaysOfferingEngine : FakeEngineSessionClient() {
        override suspend fun runAutoAiTurn(
            currentState: GameState,
            playLevel: PlayLevelSetting,
            currentProfile: EngineProfile,
            searchTimeSettings: SearchTimeSettings,
            searchMode: EngineSearchMode,
            isolateSearchCache: Boolean,
        ): AutoAiTurnResult {
            val size = currentState.boardSize.value
            val empty = (0 until size * size)
                .map { BoardCoordinate(row = it / size, column = it % size) }
                .first { it !in currentState.stones }
            val move = Move.Play(currentState.nextPlayer, empty)
            return AutoAiTurnResult(
                turnOutcome = TurnOutcome(
                    gameState = currentState.play(move),
                    engineMessage = "",
                    candidateText = "",
                    lastMoveText = "",
                ),
                scoreEstimate = null,
                profile = currentProfile,
                playLevel = playLevel,
                offersResignation = true,
            )
        }
    }

    private companion object {
        const val PumpCapMillis = 10_000L
        val HumanBlackAiWhite = PlayerSetup()
        val AiBlackAiWhite = PlayerSetup(black = SidePlayerSetup(SeatController.Ai), white = SidePlayerSetup(SeatController.Ai))
    }
}
