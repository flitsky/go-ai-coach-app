package com.worksoc.goaicoach.shared.content

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.neighbors
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
    @Test
    fun everyProblemNeedsAMoveAndTheIntendedMoveSolvesIt() {
        lifeAndDeathProblems.forEach { problem ->
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
        )
        assertEquals(expected.keys, lifeAndDeathProblems.map { it.id }.toSet(), "the table must cover every problem")
        lifeAndDeathProblems.forEach { problem ->
            val solver = LifeAndDeathSolver(problem)
            val correct = solver.correctMoves(solver.root()).map { it.label(problem.boardSize) }.toSet()
            assertEquals(expected.getValue(problem.id), correct, problem.id)
        }
    }

    @Test
    fun everyWrongFirstMoveIsAnsweredOnTheBoard() {
        lifeAndDeathProblems.forEach { problem ->
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

    /** 폰에서 문제를 열 때 푼다 — 정답 보기 · 첫 수 전부 · 그 응수까지 읽어도 상한 아래여야 한다. */
    @Test
    fun solvingStaysInsideTheNodeBudget() {
        lifeAndDeathProblems.forEach { problem ->
            val solver = LifeAndDeathSolver(problem)
            val root = solver.root()
            val correct = solver.correctMoves(root).toSet()
            solver.legalPoints(root).forEach { point ->
                val child = solver.play(root, point)
                if (point in correct) solver.replyAfterCorrect(child) else solver.refutation(child, correct)
            }
            LifeAndDeathSession(problem).answerLine()
            assertTrue(solver.visitedNodes <= LifeAndDeathSolver.NodeBudget, "${problem.id}: ${solver.visitedNodes} nodes")
        }
    }

    /** 목표 무리의 공배가 문제 밖(`,`)에 있으면 아무도 메우지 못해 잡을 수 없는 무리가 된다(살리기 문제는 거저 산다). */
    @Test
    fun theTargetGroupBreathesOnlyInsideTheProblem() {
        lifeAndDeathProblems.forEach { problem ->
            val group = groupOf(problem, problem.targetPoint)
            val liberties = group.flatMap { it.neighbors(problem.boardSize) }.filter { problem.stones[it] == null }.toSet()
            val outside = liberties - problem.playable.toSet()
            assertTrue(outside.isEmpty(), "${problem.id}: target liberties outside the problem ${outside.map { it.label(problem.boardSize) }}")
        }
    }

    /**
     * **판에 보이는 그대로도 맞는 문제여야 한다**(사용자 2026-10-21, 급소 1·2번 → 급소 여섯 전부). 풀이기는 문제 밖(`,`)에 아무도
     * 두지 않는다고 보므로, 판에서는 약한 벽도 풀이기에게는 영원히 살아 있다 — 문제 밖 활로가 하나뿐이면 단수이고(상대가 그 점을 두면
     * 따내고 산다), 벽이 대각선으로만 닿아 있으면 끊기는 자리가 남는다.
     * 그래서 목표 무리가 아닌 무리는 ① 활로가 전부 문제 안이라 풀이기가 다 읽거나 ② **그 색의 바깥 벽 하나**에 속해야 한다 —
     * 한 색에 문제 밖으로 숨 쉬는 무리는 하나뿐이고, 그 무리의 문제 밖 활로는 [SafeOutsideLiberties]개 이상이다.
     */
    @Test
    fun everyWallIsOneSolidPieceOnTheBoard() {
        val weak = lifeAndDeathProblems.flatMap { problem ->
            val playable = problem.playable.toSet()
            val seen = groupOf(problem, problem.targetPoint).toMutableSet()
            val walls = mutableListOf<Pair<Set<BoardCoordinate>, Set<BoardCoordinate>>>()
            problem.stones.keys.sortedWith(compareBy({ it.row }, { it.column })).forEach { stone ->
                if (stone in seen) return@forEach
                val group = groupOf(problem, stone)
                seen += group
                val outside = group.flatMap { it.neighbors(problem.boardSize) }
                    .filter { problem.stones[it] == null && it !in playable }.toSet()
                if (outside.isNotEmpty()) walls += group to outside
            }
            val labels = { points: Collection<BoardCoordinate> -> points.map { it.label(problem.boardSize) }.sorted() }
            walls.groupBy { (group, _) -> problem.stones.getValue(group.first()) }.flatMap { (color, pieces) ->
                buildList {
                    if (pieces.size > 1) add("${problem.id}: the $color wall is cut into ${pieces.size} pieces ${pieces.map { labels(it.first) }}")
                    pieces.filter { it.second.size < SafeOutsideLiberties }
                        .forEach { add("${problem.id}: $color stones ${labels(it.first)} breathe outside only through ${labels(it.second)}") }
                }
            }
        }
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

    private companion object {
        /** 문제 밖 활로가 이만큼 있으면 상대가 문제 밖에 한두 수를 두어도 잡히지 않는다 — 판에서 「안정」으로 보이는 하한. */
        const val SafeOutsideLiberties = 3
    }

    private fun groupOf(problem: LifeAndDeathProblem, start: BoardCoordinate): Set<BoardCoordinate> {
        val color = problem.stones.getValue(start)
        val seen = mutableSetOf(start)
        val pending = ArrayDeque(listOf(start))
        while (pending.isNotEmpty()) {
            pending.removeFirst().neighbors(problem.boardSize)
                .filter { problem.stones[it] == color && seen.add(it) }
                .forEach { pending += it }
        }
        return seen
    }
}
