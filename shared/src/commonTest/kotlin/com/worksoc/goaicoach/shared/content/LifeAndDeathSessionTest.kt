package com.worksoc.goaicoach.shared.content

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 문제를 푸는 흐름 — 화면이 보는 판과 단계가 풀이기의 판정을 그대로 따르는지(백로그 #184). */
class LifeAndDeathSessionTest {
    private fun problem(id: String) = lifeAndDeathProblems.single { it.id == id }

    private fun LifeAndDeathProblem.point(label: String) = BoardCoordinate.fromLabel(label, boardSize)

    @Test
    fun playingTheAnswerLineSolvesEveryProblem() {
        lifeAndDeathProblems.forEach { problem ->
            val session = LifeAndDeathSession(problem)
            var state = session.start()
            session.answerLine().forEachIndexed { index, point ->
                if (index % 2 == 0) {
                    state = requireNotNull(session.play(state, point)) { "${problem.id}: ${point.label(problem.boardSize)} refused" }
                } else {
                    val waiting = assertIs<LifeAndDeathPhase.AwaitingReply>(state.phase, problem.id)
                    assertEquals(point, waiting.reply, "${problem.id}: the reply differs from the shown answer")
                    state = session.applyReply(state)
                }
            }
            assertEquals(LifeAndDeathPhase.Solved, state.phase, problem.id)
            assertNull(state.mistake, problem.id)
        }
    }

    @Test
    fun aWrongMoveIsMarkedAndThenRefuted() {
        val problem = problem("ld.eyes.2")
        val session = LifeAndDeathSession(problem)
        val afterWrong = requireNotNull(session.play(session.start(), problem.point("A9")))
        assertEquals(problem.point("A9"), afterWrong.mistake)
        // 놓친 급소를 상대가 차지한다 — 「왜 틀렸나」가 판에 보인다.
        assertEquals(LifeAndDeathPhase.AwaitingReply(problem.point("B9"), wrong = true), afterWrong.phase)
        val refuted = session.applyReply(afterWrong)
        assertEquals(LifeAndDeathPhase.Failed, refuted.phase)
        assertEquals(StoneColor.White, refuted.board.stones[problem.point("B9")])
        // 틀린 뒤에는 더 두지 않는다 — 「다시 풀기」를 기다린다.
        assertNull(session.play(refuted, problem.point("C9")))
    }

    @Test
    fun aStoneOutsideTheProblemIsATenukiAndIsWrong() {
        val problem = problem("ld.eyes.1")
        val session = LifeAndDeathSession(problem)
        val outside = problem.point("H2")
        val state = requireNotNull(session.play(session.start(), outside))
        assertEquals(outside, state.mistake)
        assertEquals(StoneColor.Black, state.board.stones[outside], "the stone stays where it was played")
        assertEquals(LifeAndDeathPhase.AwaitingReply(problem.point("B9"), wrong = true), state.phase)
    }

    @Test
    fun anOccupiedOrSuicidalPointIsIgnored() {
        val problem = problem("ld.false.2")
        val session = LifeAndDeathSession(problem)
        val start = session.start()
        assertNull(session.play(start, problem.point("B9")), "occupied")
        // 귀(A9)는 백 둘에 둘러싸였고 따내는 것도 없다 — 자살수라 판이 받지 않는다(틀린 수로도 세지 않는다).
        assertNull(session.play(start, problem.point("A9")), "suicide")
        assertTrue(session.play(start, problem.point("C9")) != null, "an ordinary wrong move is still played")
    }

    @Test
    fun aCorrectMoveThatNeedsAFollowUpWaitsForTheReplyAndContinues() {
        val problem = problem("ld.vital.4")
        val session = LifeAndDeathSession(problem)
        val afterFirst = requireNotNull(session.play(session.start(), problem.point("D9")))
        assertEquals(LifeAndDeathPhase.AwaitingReply(problem.point("C9"), wrong = false), afterFirst.phase)
        val afterReply = session.applyReply(afterFirst)
        assertEquals(LifeAndDeathPhase.Solving(afterCorrect = true), afterReply.phase)
        assertEquals(Move.Play(StoneColor.White, problem.point("C9")), afterReply.board.moves.last())
        val solved = requireNotNull(session.play(afterReply, problem.point("D8")))
        assertEquals(LifeAndDeathPhase.Solved, solved.phase)
    }

    @Test
    fun anotherCorrectMoveIsAcceptedToo() {
        // 화오를 살리는 쪽 — 급소(D9) 말고 D8로도 산다. 수순 하나를 박았다면 틀렸다고 했을 수다.
        val problem = problem("ld.vital.5")
        val session = LifeAndDeathSession(problem)
        val state = requireNotNull(session.play(session.start(), problem.point("D8")))
        assertNull(state.mistake)
        val waiting = assertIs<LifeAndDeathPhase.AwaitingReply>(state.phase)
        assertEquals(false, waiting.wrong)
    }

    @Test
    fun theAnswerBoardsFollowTheAnswerLine() {
        val problem = problem("ld.space.4")
        val session = LifeAndDeathSession(problem)
        val line = session.answerLine()
        val boards = session.answerBoards()
        assertEquals(line.size, boards.size)
        boards.zip(line).forEach { (board, point) ->
            assertEquals(point, (board.moves.last() as Move.Play).coordinate)
        }
    }
}
