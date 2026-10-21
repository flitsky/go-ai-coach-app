package com.worksoc.goaicoach.shared.content

import com.worksoc.goaicoach.shared.domain.BoardRules
import com.worksoc.goaicoach.shared.domain.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 「기본 사활」의 문제가 **문제로 성립하는지** 풀이기에게 되묻는다(백로그 #184).
 *
 * 지은이가 적는 것은 판 그림과 의도한 첫 수뿐이다 — 그 그림이 정말 「흑이 두면 이루고, 손을 빼면 못 이루는」 국면인지,
 * 의도한 수가 정말 정답인지는 여기서만 확인된다. 그림을 한 칸 잘못 그리면 여기가 빨개진다.
 */
class LifeAndDeathProblemsTest {
    /**
     * 풀이기가 정답을 정하는 문제 — 기본 · 중급 · 고급. 고전(#277)은 원전의 수순이 정답이라 풀이기에게 되묻지 않는다(19줄 판의 넓은 구역은 끝까지
     * 읽을 수도 없다 — E12). 고전은 아래 `classic…` 검사가 본다.
     */
    private val solverProblems = lifeAndDeathProblems.filter { it.unit.section != LifeAndDeathSection.Classics }
    private val classicProblems = lifeAndDeathProblems.filter { it.unit.section == LifeAndDeathSection.Classics }

    @Test
    fun everyProblemNeedsAMoveAndTheIntendedMoveSolvesIt() {
        solverProblems.forEach { problem ->
            val solver = LifeAndDeathSolver(problem)
            val root = solver.root()
            assertTrue(solver.solverWins(root), "${problem.id}: the side to play cannot reach the goal")
            assertFalse(solver.isSettled(root), "${problem.id}: already settled — tenuki also reaches the goal, so there is nothing to solve")
            assertTrue(problem.answerPoint in solver.correctMoves(root), "${problem.id}: the intended ${problem.answer} is not a correct move")
        }
    }

    /**
     * 정답 첫 수의 전부 — 문제를 고치거나 풀이기를 고쳤을 때 **정답이 몰래 바뀌는 것**을 잡는다.
     * 여럿인 문제는 일부러 그렇다(`ld.vital.5`·`ld.corner.4`·`ld.space.5` — 다른 수로도 산다).
     */
    @Test
    fun theCorrectFirstMovesAreExactlyThese() {
        val expected = mapOf(
            "ld.eyes.1" to setOf("B9"), "ld.eyes.2" to setOf("B9"), "ld.eyes.3" to setOf("A9"),
            "ld.eyes.4" to setOf("A9"), "ld.eyes.5" to setOf("D9"),
            "ld.false.1" to setOf("D8"), "ld.false.2" to setOf("C8"), "ld.false.3" to setOf("C8"),
            "ld.false.4" to setOf("D8"), "ld.false.5" to setOf("C8"),
            "ld.vital.1" to setOf("D9"), "ld.vital.2" to setOf("D9"), "ld.vital.3" to setOf("D8"),
            "ld.vital.4" to setOf("D9"), "ld.vital.5" to setOf("D9", "C9", "D8"), "ld.vital.6" to setOf("D8"),
            "ld.space.1" to setOf("D8"), "ld.space.2" to setOf("D8"), "ld.space.3" to setOf("A8"),
            "ld.space.4" to setOf("A8"), "ld.space.5" to setOf("B9", "E9"),
            "ld.corner.1" to setOf("B9"), "ld.corner.2" to setOf("B9"), "ld.corner.3" to setOf("B8"),
            "ld.corner.4" to setOf("B8", "B9"), "ld.corner.5" to setOf("B8"),
            // 중급 · 고급(#277) — 맥에서 만든 문제는 정답 첫 수가 하나인 것만 골랐다.
            "ld.mid.1" to setOf("C9"), "ld.mid.2" to setOf("F9"), "ld.mid.3" to setOf("B8"), "ld.mid.4" to setOf("E8"),
            "ld.mid.5" to setOf("C8"), "ld.mid.6" to setOf("A8"), "ld.mid.7" to setOf("B9"), "ld.mid.8" to setOf("A8"),
            "ld.mid.9" to setOf("D8"), "ld.mid.10" to setOf("E8"),
            "ld.adv.1" to setOf("C9"), "ld.adv.2" to setOf("B9"), "ld.adv.3" to setOf("C8"), "ld.adv.4" to setOf("A8"),
            "ld.adv.5" to setOf("D8"), "ld.adv.6" to setOf("D9"), "ld.adv.7" to setOf("B8"), "ld.adv.8" to setOf("E9"),
            "ld.adv.9" to setOf("B9"), "ld.adv.10" to setOf("B9"),
        )
        assertEquals(expected.keys, solverProblems.map { it.id }.toSet(), "the table must cover every problem")
        solverProblems.forEach { problem ->
            val solver = LifeAndDeathSolver(problem)
            val correct = solver.correctMoves(solver.root()).map { it.label(problem.boardSize) }.toSet()
            assertEquals(expected.getValue(problem.id), correct, problem.id)
        }
    }

    /** 화면에 통과 단추가 없다 — 정답을 두어 가는 길에 「통과해야만 이기는」 차례가 있으면 그 문제는 풀 수 없다(#277). */
    @Test
    fun everyTurnOnTheWayHasAMoveToPlay() {
        solverProblems.forEach { problem ->
            val stuck = LifeAndDeathSolver(problem).turnsWithoutAPlayableAnswer()
            assertTrue(stuck.isEmpty(), "${problem.id}: ${stuck.size} turn(s) where only a pass wins")
        }
    }

    @Test
    fun everyWrongFirstMoveIsAnsweredOnTheBoard() {
        solverProblems.forEach { problem ->
            val solver = LifeAndDeathSolver(problem)
            val root = solver.root()
            val correct = solver.correctMoves(root).toSet()
            (solver.legalPoints(root) - correct).forEach { wrong ->
                val refutation = solver.refutation(solver.play(root, wrong), prefer = correct)
                assertTrue(refutation != null, "${problem.id}: ${wrong.label(problem.boardSize)} is wrong but has no refutation to show")
            }
            assertTrue(solver.refutation(solver.tenuki(root), prefer = correct) != null, "${problem.id}: tenuki has no refutation to show")
        }
    }

    /**
     * 폰에서 문제를 열 때 그 자리에서 읽는다 — 정답 보기 · 첫 수 전부 · 그 응수까지 읽어도 상한 아래여야 한다. 넘는 문제(고전 · 고급)는
     * 맥에서 미리 읽은 표를 싣는다 — 표가 있는 문제는 여기서 빠지고, 표가 낡지 않았는지는 `LifeAndDeathAnswerTableTest`가 본다.
     */
    @Test
    fun solvingStaysInsideTheNodeBudget() {
        solverProblems.filter { it.id !in lifeAndDeathAnswerTables }.forEach { problem ->
            assertTrue(problem.fitsTheNodeBudget(), "${problem.id}: reads more than ${LifeAndDeathSolver.NodeBudget} nodes — ship an answer table")
        }
    }

    /** 목표 무리의 공배가 문제 밖(`,`)에 있으면 아무도 메우지 못해 잡을 수 없는 무리가 된다(살리기 문제는 거저 산다). */
    @Test
    fun theTargetGroupBreathesOnlyInsideTheProblem() {
        solverProblems.forEach { problem ->
            val outside = problem.targetLibertiesOutside()
            assertTrue(outside.isEmpty(), "${problem.id}: target liberties outside the problem ${outside.map { it.label(problem.boardSize) }}")
        }
    }

    /** **판에 보이는 그대로도 맞는 문제여야 한다**(사용자 2026-10-21) — 잣대는 [weakWalls] 한 곳이다(맥의 문제 만들기 도구와 같은 함수). */
    @Test
    fun everyWallIsOneSolidPieceOnTheBoard() {
        val weak = solverProblems.flatMap { it.weakWalls() }
        assertTrue(weak.isEmpty(), "weak walls on the board:\n" + weak.joinToString("\n"))
    }

    @Test
    fun idsAreUniqueStableKeysAndEveryUnitHasProblems() {
        val ids = lifeAndDeathProblems.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate problem ids")
        LifeAndDeathUnit.entries.forEach { unit ->
            assertTrue(lifeAndDeathProblemsOf(unit).size >= 4, "$unit has too few problems")
        }
        // 단원끼리 섞이지 않는다 — 화면은 단원 순서대로, 단원 안은 목록 순서대로 보인다.
        assertEquals(
            LifeAndDeathUnit.entries.flatMap { lifeAndDeathProblemsOf(it) }.map { it.id },
            ids,
            "problems must be listed unit by unit in unit order",
        )
    }

    @Test
    fun theAnswerLineIsDeterministicAndEndsSettled() {
        lifeAndDeathProblems.forEach { problem ->
            val first = LifeAndDeathSession(problem).answerLine()
            val second = LifeAndDeathSession(problem).answerLine()
            assertEquals(first, second, "${problem.id}: the shown answer must not depend on the run")
            assertEquals(problem.answerPoint, first.first(), "${problem.id}: the shown answer starts with the intended move")
            assertEquals(1, first.size % 2, "${problem.id}: the line ends with the solver's move")
        }
    }

    /** 고전은 모두 원전의 수순으로 만든 판정 표를 싣는다 — 낡지 않았고, 원전의 첫 수가 그 표의 정답이며, 수순을 끝까지 두면 다 풀린다. */
    @Test
    fun everyClassicProblemCarriesAFreshSourceTable() {
        classicProblems.forEach { problem ->
            val text = lifeAndDeathAnswerTables[problem.id]
            assertTrue(text != null, "${problem.id}: a classic problem needs its answer table")
            val table = AnswerTableJudge.read(problem, text!!)
            assertTrue(table != null, "${problem.id}: the answer table is stale — regenerate it")
            assertEquals(listOf(problem.answerPoint), table!!.correctMoves(table.root()), "${problem.id}: the source's first move is the answer")
        }
    }

    /** 표는 차례마다 판이 받는 문제 안의 수 **전부**를 판정해야 한다 — 빠진 수를 누르면 판이 아무 일도 하지 않는다. */
    @Test
    fun everyClassicTurnJudgesEveryMoveTheBoardAccepts() {
        classicProblems.forEach { problem ->
            val session = LifeAndDeathSession(problem)
            val judge = lifeAndDeathJudgeFor(problem)
            var state = session.start()
            val line = session.answerLine()
            line.forEachIndexed { index, point ->
                if (index % 2 == 0) {
                    problem.playable.filter { it != point && BoardRules.validate(state.board, Move.Play(state.board.nextPlayer, it)) == null }
                        .forEach { assertTrue(judge.accepts(state.turn, it), "${problem.id}: turn ${index / 2} has no verdict for ${it.label(problem.boardSize)}") }
                    state = requireNotNull(session.play(state, point)) { "${problem.id}: the source move ${point.label(problem.boardSize)} is refused" }
                } else {
                    state = session.applyReply(state)
                }
            }
            assertEquals(LifeAndDeathPhase.Solved, state.phase, "${problem.id}: the source line ends solved")
        }
    }
}
