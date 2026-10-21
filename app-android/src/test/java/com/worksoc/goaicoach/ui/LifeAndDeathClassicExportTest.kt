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
import com.worksoc.goaicoach.shared.content.targetLibertiesOutside
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.neighbors
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 고전 사활(백로그 #277)의 **문제 후보**를 풀이기로 증명해 내보낸다 — 통과 기준을 거는 테스트가 아니다. `make test`에서는 돌지 않는다 —
 * `./gradlew :app-android:testDebugUnitTest --tests '*LifeAndDeathClassicExportTest*' -PexportLifeAndDeathClassics`.
 *
 * 들어오는 것은 실험실 E12(`engine-lab/experiments/e12_classic_life_and_death/`)의 가장 최근 결과다 — 고전의 국면마다 엔진이 찾은 **목표 무리와 목표**
 * (흑이 먼저 두느냐로 생사가 뒤집히는 무리). 여기서는 그 무리의 **둘레만** 문제 구역으로 잡아 앱의 풀이기로 끝까지 읽는다:
 * 목표 무리에서 시작해 빈 점과 지키는 쪽 돌을 따라 퍼지되 공격하는 쪽 돌에서 멈추고, 목표 무리에서 [Reaches]칸 안까지만(전사본 그림의 마지막 줄 · 칸은
 * 앞뒤를 보이는 빈 줄이라 뺀다). 좁은 구역부터 해 보고, 흑이 두면 이루고 손을 빼면 못 이루는 문제가 되는 첫 구역을 쓴다.
 *
 * ⚠️ 구역 밖은 아무도 두지 않는다는 가정이다 — 고전의 바깥 돌은 「벽」처럼 단단하지 않을 수 있다. 그래서 여기서 남은 후보는 **E11의 셋째 검사**
 * (정답 뒤 백이 판 어디에든 받는다)로 판 전체에서 다시 본다 — `app-android/build/life-and-death-classics/candidates.json`은 E11이 그대로 읽는 모양이다.
 */
class LifeAndDeathClassicExportTest {

    private val out = File(RepoPaths.root, "app-android/build/life-and-death-classics")

    @Test
    fun exportClassicCandidates() {
        assumeTrue("-PexportLifeAndDeathClassics를 줄 때만 돈다", System.getProperty("exportLifeAndDeathClassics") == "true")
        val runs = File(RepoPaths.root, "engine-lab/experiments/e12_classic_life_and_death/runs").listFiles().orEmpty()
            .filter { File(it, "samples.jsonl").exists() }.sortedBy { it.name }
        val source = File(runs.last(), "samples.jsonl")
        // 국면은 결과에 없다(전사본을 저장소에 싣지 않는다) — 그 실행이 읽은 전사본 폴더(`run.json`의 `source`)에서 다시 읽는다.
        val transcripts = File(JSONObject(File(runs.last(), "run.json").readText()).getJSONObject("args").getString("source"))
        val diagrams = transcriptDiagrams(transcripts)
        val rows = source.readLines().filter { it.isNotBlank() }.map { JSONObject(it) }.filter { !it.isNull("goal") }
            .onEach { row -> row.put("rows", JSONArray(diagrams.getValue(row.getString("book") to row.getString("id")))) }
        out.deleteRecursively()
        out.mkdirs()
        val pool = Executors.newFixedThreadPool(Threads)
        val futures = rows.map { row -> pool.submit<JSONObject?> { prove(row) } }
        val kept = JSONArray()
        val log = StringBuilder()
        rows.zip(futures).forEach { (row, future) ->
            val result = future.get()
            log.appendLine("${row.getString("book")} ${row.getString("id")} ${row.getString("goal")} → ${result?.optString("note") ?: "no"}")
            if (result != null && result.optBoolean("proven")) kept.put(result)
        }
        pool.shutdown()
        File(out, "candidates.json").writeText(JSONObject().put("source", source.parentFile.name).put("candidates", kept).toString(1))
        File(out, "log.txt").writeText(log.toString())
        println("classic candidates: ${kept.length()} proven of ${rows.size} → ${out.path}")
    }

    /** 전사본(`books/problems/<책>.txt`)의 그림 — (책, 번호) → 줄들. 글 모양은 E12와 같다(`problem <번호>` 뒤에 그림, `title`과 `#` 줄은 건너뛴다). */
    private fun transcriptDiagrams(folder: File): Map<Pair<String, String>, List<String>> = buildMap {
        folder.listFiles { file -> file.extension == "txt" }.orEmpty().forEach { file ->
            var id: String? = null
            val lines = mutableListOf<String>()
            fun flush() = id?.let { put(file.nameWithoutExtension to it, lines.toList()) }
            file.readLines().forEach { raw ->
                val line = raw.trimEnd()
                when {
                    line.isEmpty() || line.startsWith("#") || line.startsWith("title ") -> Unit
                    line.startsWith("problem ") -> {
                        flush()
                        id = line.removePrefix("problem ").trim()
                        lines.clear()
                    }
                    else -> lines += line
                }
            }
            flush()
        }
    }

    /** 한 문제 — 구역을 넓혀 가며 풀이기에게 묻는다. 증명되면 E11이 읽는 모양의 후보를, 아니면 까닭만 적은 것을. */
    private fun prove(row: JSONObject): JSONObject? {
        val lines = row.getJSONArray("rows").let { array -> (0 until array.length()).map { array.getString(it) } }
        val height = lines.size
        val width = lines.maxOf { it.length }
        val rowLimit = if (height >= 19) 18 else height - 2
        val columnLimit = if (width >= 19) 18 else width - 2
        val stones = buildMap {
            lines.forEachIndexed { r, line ->
                line.forEachIndexed { c, ch ->
                    when (ch) {
                        'x', 'b' -> put(BoardCoordinate(r, c), StoneColor.Black)
                        'o', 'w' -> put(BoardCoordinate(r, c), StoneColor.White)
                    }
                }
            }
        }
        val goal = LifeAndDeathGoal.valueOf(row.getString("goal"))
        val target = BoardCoordinate.fromLabel(row.getString("target"), BoardSize.Nineteen)
        val defender = stones.getValue(target)
        val group = groupOf(stones, target)
        var note = "no region"
        for (reach in Reaches) {
            val region = region(stones, group, defender, reach) { it.row <= rowLimit && it.column <= columnLimit }
            if (region.size < 3) continue
            val diagram = (0 until 19).map { r ->
                (0 until 19).joinToString(" ") { c ->
                    val point = BoardCoordinate(r, c)
                    when {
                        stones[point] == StoneColor.Black -> "X"
                        stones[point] == StoneColor.White -> "O"
                        point in region -> "."
                        else -> ","
                    }
                }
            }
            val anyEmpty = region.minWith(compareBy({ it.row }, { it.column }))
            val draft = try {
                LifeAndDeathProblem(
                    id = "${row.getString("book")}.${row.getString("id")}",
                    unit = LifeAndDeathUnit.Advanced,
                    goal = goal,
                    toPlay = StoneColor.Black,
                    target = target.label(BoardSize.Nineteen),
                    answer = anyEmpty.label(BoardSize.Nineteen),
                    diagram = diagram,
                )
            } catch (invalid: IllegalArgumentException) {
                note = "invalid: ${invalid.message}"
                continue
            }
            if (draft.targetLibertiesOutside().isNotEmpty()) {
                note = "target breathes outside at reach $reach"
                continue
            }
            val verdict = try {
                assess(draft, row.optString("top"))
            } catch (tooBig: LifeAndDeathSolver.NodeLimitExceeded) {
                note = "over ${MacNodeLimit} nodes at reach $reach"
                break
            }
            if (verdict == null) {
                note = "not a problem at reach $reach"
                continue
            }
            return verdict.put("reach", reach).put("note", "proven at reach $reach, region ${region.size}")
                .put("book", row.getString("book")).put("source", row.getString("id")).put("proven", true)
        }
        return JSONObject().put("note", note).put("proven", false)
    }

    /** 생성기(`LifeAndDeathCandidateExportTest`)와 같은 잣대 — 벽 검사만 뺀다(고전의 바깥 돌은 E11이 판 전체에서 본다). */
    private fun assess(draft: LifeAndDeathProblem, engineTop: String): JSONObject? {
        val probe = LifeAndDeathSolver(draft, nodeLimit = MacNodeLimit)
        val root = probe.root()
        if (!probe.solverWins(root) || probe.isSettled(root)) return null
        val correct = probe.correctMoves(root)
        if (correct.size > MaxCorrectFirstMoves) return null
        val label = { point: BoardCoordinate -> point.label(BoardSize.Nineteen) }
        // 지은이의 첫 수를 모르므로 엔진이 고른 수가 정답이면 그것을, 아니면 풀이기가 가장 가깝다고 본 정답을 의도로 둔다.
        val intended = correct.firstOrNull { label(it) == engineTop } ?: correct.first()
        val problem = draft.copy(answer = label(intended))
        val judge = SolvingJudge(problem, nodeLimit = MacNodeLimit)
        val line = try {
            LifeAndDeathSession(problem, judge).answerLine()
        } catch (onlyPassWins: NoSuchElementException) {
            return null
        }
        val start = judge.root()
        val wrong = judge.legalMoves(start).filter { it !in correct }
        if ((wrong + null).any { (judge.verdict(start, it) as LifeAndDeathVerdict.Wrong).refutation == null }) return null
        if (LifeAndDeathSolver(problem, nodeLimit = MacNodeLimit).turnsWithoutAPlayableAnswer().isNotEmpty()) return null
        return JSONObject()
            .put("id", problem.id)
            .put("goal", problem.goal.name)
            .put("target", problem.target)
            .put("answer", problem.answer)
            .put("correct", JSONArray(correct.map(label)))
            .put("answerLine", JSONArray(line.map(label)))
            .put("wrongFirstMoves", wrong.size)
            .put("nodes", judge.visitedNodes)
            .put("region", problem.playable.count { problem.stones[it] == null })
            .put("fitsPhone", problem.fitsTheNodeBudget())
            .put("engineTop", engineTop)
            .put("diagram", JSONArray(problem.diagram))
    }

    /** 목표 무리에서 빈 점 · 지키는 쪽 돌을 따라 퍼지고 공격하는 쪽 돌에서 멈춘다 — 목표 무리에서 [reach]칸(체비쇼프) 안, [inside] 안까지. */
    private fun region(
        stones: Map<BoardCoordinate, StoneColor>,
        group: Set<BoardCoordinate>,
        defender: StoneColor,
        reach: Int,
        inside: (BoardCoordinate) -> Boolean,
    ): Set<BoardCoordinate> {
        val near = { point: BoardCoordinate -> group.any { maxOf(abs(it.row - point.row), abs(it.column - point.column)) <= reach } }
        val seen = group.toMutableSet()
        val pending = ArrayDeque(group.toList())
        while (pending.isNotEmpty()) {
            for (next in pending.removeFirst().neighbors(BoardSize.Nineteen)) {
                if (next in seen || !inside(next) || !near(next) || stones[next] == defender.opponent) continue
                seen += next
                pending += next
            }
        }
        return seen.filter { stones[it] == null }.toSet()
    }

    private fun groupOf(stones: Map<BoardCoordinate, StoneColor>, start: BoardCoordinate): Set<BoardCoordinate> {
        val color = stones.getValue(start)
        val group = mutableSetOf(start)
        val pending = ArrayDeque(listOf(start))
        while (pending.isNotEmpty()) {
            pending.removeFirst().neighbors(BoardSize.Nineteen).filter { stones[it] == color && group.add(it) }.forEach { pending += it }
        }
        return group
    }

    private companion object {
        /** 좁은 구역부터 — 넓을수록 읽을 국면이 곱으로 는다. */
        val Reaches = listOf(2, 3)
        const val MaxCorrectFirstMoves = 2

        /** 19줄 판의 국면 열쇠는 크다 — 갈래 수 × 이 상한이 테스트 JVM의 힙(이 도구를 돌릴 때만 6GB, `build.gradle.kts`) 안에 들어야 한다. */
        val MacNodeLimit = System.getProperty("lifeAndDeathClassicNodeLimit")?.toIntOrNull() ?: 300_000
        const val Threads = 3
    }
}
