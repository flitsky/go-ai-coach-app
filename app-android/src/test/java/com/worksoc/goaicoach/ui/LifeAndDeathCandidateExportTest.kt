package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.shared.content.LifeAndDeathGoal
import com.worksoc.goaicoach.shared.content.LifeAndDeathProblem
import com.worksoc.goaicoach.shared.content.LifeAndDeathSession
import com.worksoc.goaicoach.shared.content.LifeAndDeathSolver
import com.worksoc.goaicoach.shared.content.LifeAndDeathUnit
import com.worksoc.goaicoach.shared.content.LifeAndDeathVerdict
import com.worksoc.goaicoach.shared.content.SolvingJudge
import com.worksoc.goaicoach.shared.content.fitsTheNodeBudget
import com.worksoc.goaicoach.shared.content.lifeAndDeathProblems
import com.worksoc.goaicoach.shared.content.targetLibertiesOutside
import com.worksoc.goaicoach.shared.content.weakWalls
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.neighbors
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 「중·고급 사활」(백로그 #277)의 **문제 후보**를 9줄 판에 만들어 내보낸다 — 통과 기준을 거는 테스트가 아니다.
 * `make test`에서는 돌지 않는다 — `./gradlew :app-android:testDebugUnitTest --tests '*LifeAndDeathCandidateExportTest*' -PexportLifeAndDeathCandidates`.
 *
 * 만드는 법: 귀나 변에 궁도(빈 점 5~11개)를 그리고, 그 둘레를 지키는 쪽 돌로, 그 밖을 공격하는 쪽의 **끊긴 데 없는 벽**으로 두른 뒤
 * 둘레에 틈을 내거나 안에 돌을 놓아 흔든다. 흑이 공격하는 쪽이면 잡기, 지키는 쪽이면 살기 문제다. 판정은 **앱의 풀이기 그대로**다
 * (`LifeAndDeathSolver` — 흑백 모두 끝까지 최선으로 두어 본 결과, 증명이다). 남기는 것은 `LifeAndDeathProblemsTest`가 실은 문제에 거는
 * 잣대를 모두 지나는 것뿐이다 — 흑이 두면 이루고 손을 빼면 못 이룬다 · 정답 첫 수가 하나나 둘 · 틀린 첫 수마다 응수가 있다 ·
 * 벽이 판 그대로 단단하다 · 목표 무리의 공배가 문제 안이다 · 정답을 두어 가는 길에 통과만 이기는 자리가 없다.
 * 폰에서 그 자리에서 읽기에 큰 후보도 남기고 `fitsPhone`으로 표시한다 — 실으면 맥에서 판정 표를 만든다(`LifeAndDeathAnswerTableExportTest`).
 *
 * 난이도는 여기서 매기지 않는다 — 실험실 `engine-lab/experiments/e11_life_and_death_grades/`가 사람 모델로 「그 급수의 사람이 첫 수를
 * 찾을 확률」을 재고, 주 모델로 생사를 다시 본다(엔진 교차 검증). 같은 씨앗이면 같은 후보가 나온다.
 */
class LifeAndDeathCandidateExportTest {

    private val out = File(RepoPaths.root, "app-android/build/life-and-death-candidates")

    @Test
    fun exportCandidates() {
        assumeTrue("-PexportLifeAndDeathCandidates를 줄 때만 돈다", System.getProperty("exportLifeAndDeathCandidates") == "true")
        out.deleteRecursively()
        out.mkdirs()
        // 그림은 씨앗 하나로 차례대로(같은 씨앗이면 같은 후보), 되묻기는 여러 갈래로 나눠 — 풀이기는 문제마다 따로라 서로 기대지 않는다.
        val rng = Random(Seed)
        val seen = HashSet<String>()
        val drafts = (0 until Tries).mapNotNull { attempt ->
            val draft = draft(rng)?.takeIf { seen.add(it.canonical) } ?: return@mapNotNull null
            draft.problem("cand.$attempt")?.let { it to draft.corner }
        }
        println("life-and-death drafts: ${drafts.size} (of $Tries tries)")
        val pool = Executors.newFixedThreadPool(Threads)
        val checked = AtomicInteger()
        val tooBigCount = AtomicInteger()
        val futures = drafts.map { (problem, _) ->
            pool.submit<Verdict?> {
                val verdict = try {
                    assess(problem)
                } catch (limit: LifeAndDeathSolver.NodeLimitExceeded) {
                    tooBigCount.incrementAndGet()
                    null
                }
                if (checked.incrementAndGet() % 1000 == 0) File(out, "progress.txt").writeText("checked ${checked.get()} / ${drafts.size}\n")
                verdict
            }
        }
        val kept = JSONArray()
        drafts.zip(futures).forEach { (draft, future) -> future.get()?.let { kept.put(it.toJson(draft.first, draft.second)) } }
        pool.shutdown()
        val tooBig = tooBigCount.get()
        File(out, "candidates.json").writeText(
            JSONObject()
                .put("seed", Seed)
                .put("tries", Tries)
                .put("tooBig", tooBig)
                .put("nodeBudget", LifeAndDeathSolver.NodeBudget)
                .put("candidates", kept)
                // 실은 문제(「기본 사활」)도 같이 — 실험실이 같은 잣대로 재어 「기본」이 어디쯤인지 기준선으로 쓴다.
                .put("shipped", JSONArray(lifeAndDeathProblems.map { shippedJson(it) }))
                .toString(1),
        )
        println("life-and-death candidates: ${kept.length()} kept, $tooBig over the node budget → ${out.path}")
    }

    private fun shippedJson(problem: LifeAndDeathProblem): JSONObject {
        val solver = LifeAndDeathSolver(problem)
        val label = { point: BoardCoordinate -> point.label(problem.boardSize) }
        return JSONObject()
            .put("id", problem.id)
            .put("unit", problem.unit.name)
            .put("goal", problem.goal.name)
            .put("target", problem.target)
            .put("answer", problem.answer)
            .put("correct", JSONArray(solver.correctMoves(solver.root()).map(label)))
            .put("answerLine", JSONArray(LifeAndDeathSession(problem).answerLine().map(label)))
            .put("region", problem.playable.count { problem.stones[it] == null })
            .put("diagram", JSONArray(problem.diagram))
    }

    // ── 그리기 ──────────────────────────────────────────────────────────

    private data class Cell(val row: Int, val column: Int)

    private class Draft(
        val corner: Boolean,
        val blackIsAttacker: Boolean,
        val attacker: Set<Cell>,
        val defender: Set<Cell>,
        val empty: Set<Cell>,
    ) {
        val rows: List<String> = (0 until Size).map { row ->
            (0 until Size).joinToString(" ") { column ->
                val cell = Cell(row, column)
                when (cell) {
                    in attacker -> if (blackIsAttacker) "X" else "O"
                    in defender -> if (blackIsAttacker) "O" else "X"
                    in empty -> "."
                    else -> ","
                }
            }
        }

        /** 귀의 문제는 대각선으로 접어도 같은 문제다 — 둘 중 앞선 그림 하나만 센다. */
        val canonical: String = run {
            val plain = rows.joinToString("/")
            if (!corner) return@run plain
            val cells = rows.map { it.split(' ') }
            val transposed = (0 until Size).joinToString("/") { r -> (0 until Size).joinToString(" ") { c -> cells[c][r] } }
            minOf(plain, transposed)
        }

        fun problem(id: String): LifeAndDeathProblem? {
            val defenderColor = if (blackIsAttacker) StoneColor.White else StoneColor.Black
            val stones = buildMap {
                attacker.forEach { put(BoardCoordinate(it.row, it.column), defenderColor.opponent) }
                defender.forEach { put(BoardCoordinate(it.row, it.column), defenderColor) }
            }
            val target = largestGroup(stones, defenderColor) ?: return null
            val anyEmpty = empty.minWith(compareBy({ it.row }, { it.column }))
            return try {
                LifeAndDeathProblem(
                    id = id,
                    unit = LifeAndDeathUnit.Corner,
                    goal = if (blackIsAttacker) LifeAndDeathGoal.Kill else LifeAndDeathGoal.Live,
                    toPlay = StoneColor.Black,
                    target = target.label(BoardSize.Nine),
                    answer = BoardCoordinate(anyEmpty.row, anyEmpty.column).label(BoardSize.Nine),
                    diagram = rows,
                )
            } catch (invalid: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * 궁도 → 둘레(지키는 쪽) → 벽(공격하는 쪽, 둘레를 여덟 방향으로 한 겹 두른다 — 대각선 틈이 없다) → 흔들기.
     * 판의 오른쪽 한 줄과 아래 세 줄은 늘 문제 밖이다 — 벽의 바깥 활로가 넉넉해야 판에서 단단해 보인다.
     */
    private fun draft(rng: Random): Draft? {
        val corner = rng.nextInt(3) != 0
        val spaceSize = rng.nextInt(5, 12)
        val start = if (corner) Cell(0, 0) else Cell(0, rng.nextInt(2, 4))
        val space = linkedSetOf(start)
        while (space.size < spaceSize) {
            val frontier = space.flatMap { n4(it) }
                .filter { it !in space && it.row <= 2 && it.column <= 6 && (corner || it.column >= 2) }
                .distinct()
            if (frontier.isEmpty()) break
            // 1선 · 2선으로 기운다 — 3선까지 내려간 궁도는 드물게.
            space += frontier.minBy { it.row + rng.nextDouble() * 2.2 }
        }
        val boundary = space.flatMap { n4(it) }.filter { it !in space }.toSet()
        val wall = (space + boundary).flatMap { n8(it) }.filter { it !in space && it !in boundary }.toSet()
        if (wall.any { it.column >= Size - 1 || it.row >= Size - 3 }) return null

        val attacker = wall.toMutableSet()
        val defender = boundary.toMutableSet()
        val empty = space.toMutableSet()
        // 둘레에 틈: 벽과 닿은 둘레 돌을 비우거나(밀고 들어올 자리) 공격하는 쪽 돌로(이미 밀고 들어온 자리).
        repeat(rng.nextInt(0, 3)) {
            val gap = defender.filter { cell -> n4(cell).any { it in attacker } }.randomOrNull(rng) ?: return@repeat
            defender -= gap
            if (rng.nextBoolean()) attacker += gap else empty += gap
        }
        // 궁도 안의 돌: 공격하는 쪽의 치중이 지키는 쪽 돌보다 흔하다.
        repeat(rng.nextInt(0, 3)) {
            val point = empty.randomOrNull(rng) ?: return@repeat
            empty -= point
            if (rng.nextInt(3) == 0) defender += point else attacker += point
        }
        if (defender.isEmpty() || empty.size < 3) return null
        return Draft(corner, blackIsAttacker = rng.nextBoolean(), attacker = attacker, defender = defender, empty = empty)
    }

    private fun n4(cell: Cell): List<Cell> =
        listOf(Cell(cell.row - 1, cell.column), Cell(cell.row + 1, cell.column), Cell(cell.row, cell.column - 1), Cell(cell.row, cell.column + 1))
            .filter { it.row in 0 until Size && it.column in 0 until Size }

    private fun n8(cell: Cell): List<Cell> =
        (-1..1).flatMap { dr -> (-1..1).map { dc -> Cell(cell.row + dr, cell.column + dc) } }
            .filter { it != cell && it.row in 0 until Size && it.column in 0 until Size }

    // ── 되묻기 ──────────────────────────────────────────────────────────

    private class Verdict(
        val correct: List<BoardCoordinate>,
        val answerLine: List<BoardCoordinate>,
        val wrongFirstMoves: Int,
        val nodes: Int,
        val region: Int,
        val fitsPhone: Boolean,
    ) {
        fun toJson(problem: LifeAndDeathProblem, corner: Boolean): JSONObject {
            val label = { point: BoardCoordinate -> point.label(problem.boardSize) }
            return JSONObject()
                .put("id", problem.id)
                .put("goal", problem.goal.name)
                .put("corner", corner)
                .put("target", problem.target)
                .put("answer", label(correct.first()))
                .put("correct", JSONArray(correct.map(label)))
                .put("answerLine", JSONArray(answerLine.map(label)))
                .put("wrongFirstMoves", wrongFirstMoves)
                .put("nodes", nodes)
                .put("region", region)
                .put("fitsPhone", fitsPhone)
                .put("diagram", JSONArray(problem.diagram))
        }
    }

    /**
     * `LifeAndDeathProblemsTest`의 잣대를 모두 지나면 판정을, 하나라도 못 지나면 `null`. 폰에서 그 자리에서 읽기에 큰 문제도 남긴다 —
     * 실으면 맥에서 판정 표를 만든다(`fitsPhone`이 거짓). 맥에서도 [MacNodeLimit]을 넘으면 버린다.
     */
    private fun assess(draft: LifeAndDeathProblem): Verdict? {
        if (draft.targetLibertiesOutside().isNotEmpty() || draft.weakWalls().isNotEmpty()) return null
        val probe = LifeAndDeathSolver(draft, nodeLimit = MacNodeLimit)
        val root = probe.root()
        if (!probe.solverWins(root) || probe.isSettled(root)) return null
        val correct = probe.correctMoves(root)
        if (correct.size > MaxCorrectFirstMoves) return null

        // 의도한 첫 수 = 풀이기가 가장 가깝다고 본 정답. 폰이 문제를 여는 순서 그대로 새 판정에서 — 풀이기의 기억은 읽은 순서에 따라
        // 달라질 수 있다(`winOf`의 KDoc): 「정답 보기」 수순을 먼저 읽고, 처음 국면의 수 전부를 판정한다.
        val problem = draft.copy(answer = correct.first().label(draft.boardSize))
        val judge = SolvingJudge(problem, nodeLimit = MacNodeLimit)
        val line = try {
            LifeAndDeathSession(problem, judge).answerLine()
        } catch (onlyPassWins: NoSuchElementException) {
            return null
        }
        if (line.size < MinAnswerLine) return null
        val start = judge.root()
        val wrong = judge.legalMoves(start).filter { it !in correct }
        if ((wrong + null).any { (judge.verdict(start, it) as LifeAndDeathVerdict.Wrong).refutation == null }) return null
        if (LifeAndDeathSolver(problem, nodeLimit = MacNodeLimit).turnsWithoutAPlayableAnswer().isNotEmpty()) return null
        return Verdict(
            correct = correct,
            answerLine = line,
            wrongFirstMoves = wrong.size,
            nodes = judge.visitedNodes,
            region = problem.playable.count { problem.stones[it] == null },
            fitsPhone = problem.fitsTheNodeBudget(),
        )
    }

    private companion object {
        const val Size = 9
        const val Seed = 277L
        const val Tries = 40_000

        /** 맥의 성능 코어 수쯤 — 게이트와 함께 돌리지 않는다(일감 카드의 기조). */
        const val Threads = 6
        const val MaxCorrectFirstMoves = 2

        /** 맥에서도 이만큼 넘게 읽는 후보는 버린다 — 표를 만들 때 문제 하나에 몇 분씩 걸린다. */
        const val MacNodeLimit = 300_000

        /** 정답 수순이 이보다 짧으면 「기본 사활」의 몫이다(한 수로 끝나는 문제). */
        const val MinAnswerLine = 3
    }
}

/** 그 색의 가장 큰 무리 — 목표 무리다. 대표 돌은 맨 위 · 맨 왼쪽 돌. */
private fun largestGroup(stones: Map<BoardCoordinate, StoneColor>, color: StoneColor): BoardCoordinate? {
    val done = mutableSetOf<BoardCoordinate>()
    var best: Set<BoardCoordinate> = emptySet()
    stones.filter { it.value == color }.keys.sortedWith(compareBy({ it.row }, { it.column })).forEach { stone ->
        if (stone in done) return@forEach
        val group = mutableSetOf(stone)
        val pending = ArrayDeque(listOf(stone))
        while (pending.isNotEmpty()) {
            pending.removeFirst().neighbors(BoardSize.Nine).filter { stones[it] == color && group.add(it) }.forEach { pending += it }
        }
        done += group
        if (group.size > best.size) best = group
    }
    return best.minWithOrNull(compareBy({ it.row }, { it.column }))
}
