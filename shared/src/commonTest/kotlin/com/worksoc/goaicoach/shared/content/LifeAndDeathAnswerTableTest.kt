package com.worksoc.goaicoach.shared.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 미리 읽어 둔 표(백로그 #277)가 **그 자리에서 읽는 풀이기와 같은 답**을 내는가 — 기본 사활 26문제로 표를 만들어 되읽고, 차례마다
 * 판이 받는 모든 수의 판정과 「정답 보기」 수순을 견준다. 표는 판정의 둘째 규칙이 아니라 같은 규칙을 미리 적어 둔 것이어야 한다.
 */
class LifeAndDeathAnswerTableTest {
    @Test
    fun aTableAnswersExactlyAsTheSolverDoes() {
        lifeAndDeathProblems.forEach { problem ->
            val table = assertNotNull(AnswerTableJudge.read(problem, AnswerTableJudge.write(problem, SolvingJudge(problem))), problem.id)
            val live = SolvingJudge(problem)
            val pending = ArrayDeque(listOf(live.root() to table.root()))
            var turns = 0
            while (pending.isNotEmpty()) {
                val (liveTurn, tableTurn) = pending.removeFirst()
                turns += 1
                assertEquals(live.isSettled(liveTurn), table.isSettled(tableTurn), problem.id)
                if (live.isSettled(liveTurn)) continue
                assertEquals(live.correctMoves(liveTurn), table.correctMoves(tableTurn), "${problem.id}: correct moves")
                (live.legalMoves(liveTurn) + null).forEach { move ->
                    if (move != null) assertTrue(table.accepts(tableTurn, move), "${problem.id}: the table refuses $move")
                    val verdict = live.verdict(liveTurn, move)
                    assertEquals(verdict, table.verdict(tableTurn, move), "${problem.id}: verdict for $move")
                    val reply = (verdict as? LifeAndDeathVerdict.Correct)?.reply
                    if (move != null && reply != null) pending += live.next(liveTurn, move, reply) to table.next(tableTurn, move, reply)
                }
            }
            assertEquals(turns, table.turnCount, "${problem.id}: the table holds exactly the turns on the way")
            assertEquals(
                LifeAndDeathSession(problem, SolvingJudge(problem)).answerLine(),
                LifeAndDeathSession(problem, table).answerLine(),
                "${problem.id}: answer line",
            )
        }
    }

    @Test
    fun aStaleTableIsNotUsed() {
        val problem = lifeAndDeathProblems.first()
        val text = AnswerTableJudge.write(problem, SolvingJudge(problem))
        val changed = problem.copy(answer = problem.correctAnswerOtherThanIntended() ?: return)
        assertNull(AnswerTableJudge.read(changed, text), "a table written for another drawing must not be read")
    }

    /** 실은 표는 모두 지금 문제의 것이어야 한다 — 문제를 고치고 표를 다시 만들지 않으면 폰이 조용히 그 자리에서 읽게 된다(느려진다). */
    @Test
    fun everyShippedTableIsFreshAndBelongsToAProblem() {
        val ids = lifeAndDeathProblems.associateBy { it.id }
        lifeAndDeathAnswerTables.forEach { (id, text) ->
            val problem = assertNotNull(ids[id], "a table for an unknown problem $id")
            assertNotNull(AnswerTableJudge.read(problem, text), "$id: the table is stale — regenerate it (LifeAndDeathAnswerTableExportTest)")
        }
    }

    /** 지문이 바뀌는지만 보려고 그림 대신 의도한 첫 수를 바꾼다 — 빈 점이면 무엇이든 된다. */
    private fun LifeAndDeathProblem.correctAnswerOtherThanIntended(): String? =
        playable.firstOrNull { it != answerPoint && stones[it] == null }?.label(boardSize)
}
