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
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import com.worksoc.goaicoach.testsupport.FakeEngineSessionClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI의 **기권 제안**이 실제 배선([wireGoCoachControllers])에서 도는지(backlog #213·#221, 사용자 2026-10-07·08).
 *
 * 사용자가 정한 것: 중반·후반에 **상대가 둔 뒤의 형세가 5회 연속 문턱 밖**이면 AI가 **한 판에 한 번** 기권을 제안하고, 사용자가
 * 받아들이거나 계속 두게 고른다. 판정 자체(`HopelessPosition.resignationGrounds`)는 `:shared`의 테스트가 본다 — 여기서 보는 것은:
 * - AI가 **두기 전에** 형세 기록을 보고 묻는다. 묻는 동안 AI는 두지 않는다(엔진을 부르지도 않는다).
 * - 받아들이면 AI의 기권으로 판이 끝난다. 거절하면 AI가 그대로 두고, 이 대국에서는 다시 묻지 않는다 — 무르기를 해도.
 * - 답할 사람이 없는 판(AI끼리)에서는 묻지 않는다.
 */
class AiResignationOfferWiringTest {
    @Test
    fun theAiAsksBeforeItPlaysAndHoldsItsTurnUntilTheUserAnswers() {
        val context = hopelessContext()

        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()

        assertEquals(
            "제안은 AI가 둘 차례인 지금 국면에 선다",
            AutoAiTurnTimeout(sessionGeneration = generationOf(context), moveCount = 35),
            context.holder.current.autoAiTurn.resignationOffer,
        )
        assertTrue(context.holder.current.isAwaitingAiResignationChoice)
        assertTrue("묻는 동안 AI 차례를 예약하지 않는다", context.autoAiTurnWrites.none { it.isPending })
        assertEquals(0, context.dispatcher.queuedCount)
        // 리포트만으로 왜 그때 물었는지 알 수 있다 — 근거가 된 형세 다섯(수순 · AI 기준 점수차 / 그 국면의 문턱).
        val offer = context.runtimeLog.lines.single { it.contains("event=ai_resignation_offer") }
        assertTrue(offer, offer.contains("readings=m27:-30.0/12.2,m29:-30.0/12.2,m31:-30.0/12.2,m33:-30.0/12.2,m35:-30.0/12.2"))

        // 트리거 효과가 다시 불러도 그대로다 — 다시 묻지도, 두지도 않는다.
        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()
        assertEquals(1, context.runtimeLog.lines.count { it.contains("event=ai_resignation_offer") })
        assertEquals(0, context.dispatcher.queuedCount)
    }

    @Test
    fun acceptingEndsTheGameByTheAisResignation() {
        val context = hopelessContext()
        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()

        wireGoCoachControllers(context).autoAiTurnController.answerResignationOffer(accepted = true)

        assertEquals("AI(백)가 제 차례에 기권한다", Move.Resign(StoneColor.White), context.gameState().moves.last())
        assertTrue("판이 그 자리에서 끝난다", context.holder.current.core.isGameEnded)
        assertFalse(context.holder.current.isAwaitingAiResignationChoice)
        assertNull(context.holder.current.autoAiTurn.resignationOffer)
        assertEquals("끝난 판에서 AI 차례를 예약하지 않는다", 0, context.dispatcher.queuedCount)
        val answer = context.runtimeLog.lines.single { it.contains("event=ai_resignation_answer") }
        assertTrue(answer, answer.contains("transition=\"end_game_by_ai_resignation\"") && answer.contains("accepted=true"))
    }

    @Test
    fun decliningLetsTheAiPlayOnAndTheSameGameIsNotAskedAgain() {
        val context = hopelessContext()
        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()

        wireGoCoachControllers(context).autoAiTurnController.answerResignationOffer(accepted = false)

        assertFalse("고르자마자 대기가 풀린다", context.holder.current.isAwaitingAiResignationChoice)
        assertEquals("거절하면 AI 차례를 바로 요청한다 — 같은 국면에서 다시 묻지 않는다", 1, context.dispatcher.queuedCount)
        assertFalse(context.holder.current.canOfferAiResignation)
        val answer = context.runtimeLog.lines.single { it.contains("event=ai_resignation_answer") }
        assertTrue(answer, answer.contains("transition=\"request_ai_turn\"") && answer.contains("accepted=false"))

        // 형세는 여전히 문턱 밖이다 — 그래도 이 대국에서는 다시 묻지 않고 AI가 계속 둔다.
        playAiTurn(context)
        assertEquals("AI가 둔다", 36, context.gameState().moves.size)
        humanPlays(context, whiteScoreLeadAfter = -30.0)
        playAiTurn(context)
        assertEquals(38, context.gameState().moves.size)
        assertEquals("한 판에 한 번만 묻는다", 1, context.runtimeLog.lines.count { it.contains("event=ai_resignation_offer") })
    }

    /** 무르기는 그 판이다 — 거절하고 무른 사람에게 다시 묻지 않는다. 새 대국에서는 다시 물을 수 있다. */
    @Test
    fun anUndoIsStillTheSameGameButANewGameCanBeAskedAgain() {
        val context = hopelessContext()
        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()
        wireGoCoachControllers(context).autoAiTurnController.answerResignationOffer(accepted = false)
        // 거절 뒤에 걸린 AI 차례는 버린다 — 이 테스트는 같은 국면에서 「다시 묻는가」만 본다.
        wireGoCoachControllers(context).autoAiTurnController.cancelInFlightTurn()
        while (context.dispatcher.runNext()) Unit

        // 무르기는 세션 세대만 올린다(판과 형세 기록은 그대로 둔 셈으로 — 같은 국면으로 돌아왔다).
        context.changeCore { core -> core.copy(runtimeState = core.runtimeState.nextSessionGeneration()) }
        assertNull("같은 판에서는 형세가 문턱 밖이어도 묻지 않는다", offerAt(context))

        context.changeCore { core -> core.copy(runtimeState = core.runtimeState.nextSessionGeneration().nextMatchGeneration()) }
        assertEquals("새 대국이면 다시 묻는다", 35, offerAt(context)?.moveCount)
    }

    /** 형세가 문턱 안이면(또는 다섯이 안 모였으면) 묻지 않고 그냥 둔다. */
    @Test
    fun aGameThatIsNotHopelessIsSimplyPlayed() {
        val context = hopelessContext(whiteScoreLead = -5.0)

        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()

        assertNull(context.holder.current.autoAiTurn.resignationOffer)
        assertEquals("AI 차례가 예약된다", 1, context.dispatcher.queuedCount)
        assertTrue(context.runtimeLog.lines.none { it.contains("event=ai_resignation_offer") })
    }

    @Test
    fun aGameWithNoHumanSeatIsNeverAsked() {
        val context = hopelessContext(playerSetup = AiBlackAiWhite)

        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()

        assertNull("답할 사람이 없는 판에서는 묻지 않는다", context.holder.current.autoAiTurn.resignationOffer)
        assertEquals("AI는 그냥 둔다", 1, context.dispatcher.queuedCount)
    }

    @Test
    fun anAnswerThatArrivesWhenNothingIsAskedDoesNothing() {
        val context = hopelessContext(whiteScoreLead = -5.0)

        wireGoCoachControllers(context).autoAiTurnController.answerResignationOffer(accepted = true)

        assertEquals("판은 그대로다", 35, context.gameState().moves.size)
        assertFalse(context.holder.current.core.isGameEnded)
        assertTrue("답하지 않은 것은 적지 않는다", context.runtimeLog.lines.none { it.contains("event=ai_resignation_answer") })
    }

    /**
     * 9줄 35수 판(중반) — 흑(사람)이 방금 35수째를 뒀고 백(AI)이 둘 차례다. 흑이 둔 수마다(27·29·31·33·35수째) 그 직후의 형세가
     * [whiteScoreLead](백 기준)로 적혀 있다. 9줄 중반의 문턱은 12.15집이라 −30집이면 제안이고 −5집이면 아니다.
     */
    private fun hopelessContext(
        playerSetup: PlayerSetup = HumanBlackAiWhite,
        whiteScoreLead: Double = -30.0,
    ): FakeGoCoachAppWiringContext {
        val context = FakeGoCoachAppWiringContext(
            inGameSession(playerSetup = playerSetup, boardSize = BoardSize.Nine),
            engineClient = PlaysTheFirstEmptyPoint(),
        )
        context.changeCore { core ->
            val state = (0 until 35).fold(core.gameState) { state, index -> state.play(Move.Play(state.nextPlayer, PointAt(index))) }
            core.copy(
                gameState = state,
                scoreState = core.scoreState.copy(scoreSnapshots = (27..35 step 2).map { moveNumber -> reading(moveNumber, whiteScoreLead) }),
            )
        }
        context.engineIsReady = true
        return context
    }

    /** 판 위의 사용자 — 바깥에서 한 수를 올리고 그 직후의 형세를 적는다(사람 수 동기화가 하는 일. 기록에는 남지 않는다). */
    private fun humanPlays(context: FakeGoCoachAppWiringContext, whiteScoreLeadAfter: Double) {
        context.changeCore { core ->
            val state = core.gameState.play(Move.Play(core.gameState.nextPlayer, firstEmptyPoint(core.gameState)))
            core.copy(gameState = state, scoreState = core.scoreState.copy(scoreSnapshots = core.scoreState.scoreSnapshots + reading(state.moves.size, whiteScoreLeadAfter)))
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

    /** 지금 국면에서 AI 차례를 요청했을 때 선 제안(없으면 `null`). 묻지 않아 걸린 AI 차례는 버린다. */
    private fun offerAt(context: FakeGoCoachAppWiringContext): AutoAiTurnTimeout? {
        wireGoCoachControllers(context).autoAiTurnController.requestAiTurn()
        val offer = context.holder.current.autoAiTurn.resignationOffer
        wireGoCoachControllers(context).autoAiTurnController.cancelInFlightTurn()
        while (context.dispatcher.runNext()) Unit
        return offer
    }

    private fun generationOf(context: FakeGoCoachAppWiringContext): Long = context.holder.current.core.runtimeState.sessionGeneration

    /** 빈 자리 하나에 두는 엔진 — 기권 제안은 엔진이 아니라 대국 세션이 형세 기록을 보고 한다. */
    private class PlaysTheFirstEmptyPoint : FakeEngineSessionClient() {
        override suspend fun runAutoAiTurn(
            currentState: GameState,
            playLevel: PlayLevelSetting,
            currentProfile: EngineProfile,
            searchTimeSettings: SearchTimeSettings,
            searchMode: EngineSearchMode,
            isolateSearchCache: Boolean,
        ): AutoAiTurnResult {
            return AutoAiTurnResult(
                turnOutcome = TurnOutcome(
                    gameState = currentState.play(Move.Play(currentState.nextPlayer, firstEmptyPoint(currentState))),
                    engineMessage = "",
                    candidateText = "",
                    lastMoveText = "",
                ),
                scoreEstimate = null,
                profile = currentProfile,
                playLevel = playLevel,
            )
        }
    }

    private companion object {
        const val PumpCapMillis = 10_000L
        val HumanBlackAiWhite = PlayerSetup()
        val AiBlackAiWhite = PlayerSetup(black = SidePlayerSetup(SeatController.Ai), white = SidePlayerSetup(SeatController.Ai))

        /**
         * [index]번째로 놓일 자리 — 흑(짝수 번째)은 위 두 줄을, 백(홀수 번째)은 아래 두 줄을 왼쪽부터 채운다.
         * 가운데 다섯 줄이 비어 서로 닿지 않는다(36수까지) — 따내거나 자충이 될 일이 없다.
         */
        fun PointAt(index: Int): BoardCoordinate {
            val nth = index / 2
            return if (index % 2 == 0) BoardCoordinate(row = nth / 9, column = nth % 9) else BoardCoordinate(row = 8 - nth / 9, column = nth % 9)
        }

        /** 그 뒤의 수가 놓일 자리 — 판을 위에서부터 훑어 처음 만나는 빈 자리(가운데 줄부터 찬다). */
        fun firstEmptyPoint(state: GameState): BoardCoordinate =
            (0 until 81).map { BoardCoordinate(row = it / 9, column = it % 9) }.first { it !in state.stones }

        fun reading(moveNumber: Int, whiteScoreLead: Double) =
            ScoreSnapshot(moveNumber = moveNumber, whiteScoreLead = whiteScoreLead, source = ScoreSnapshotSource.HumanNetworkEstimate)
    }
}
