package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.shared.content.AnswerTableJudge
import com.worksoc.goaicoach.shared.content.LifeAndDeathGoal
import com.worksoc.goaicoach.shared.content.LifeAndDeathProblem
import com.worksoc.goaicoach.shared.content.LifeAndDeathSection
import com.worksoc.goaicoach.shared.content.LifeAndDeathUnit
import com.worksoc.goaicoach.shared.content.SolvingJudge
import com.worksoc.goaicoach.shared.content.fitsTheNodeBudget
import com.worksoc.goaicoach.shared.content.lifeAndDeathProblems
import com.worksoc.goaicoach.shared.content.section
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import java.io.File
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 사활 문제의 **판정 표**와 **고전 문제**를 맥에서 만들어 두 파일을 다시 쓴다(백로그 #277) — 통과 기준을 거는 테스트가 아니다.
 * `make test`에서는 돌지 않는다 — `./gradlew :app-android:testDebugUnitTest --tests '*LifeAndDeathAnswerTableExportTest*' -PexportLifeAndDeathAnswerTables`.
 *
 * - 고전(`LifeAndDeathClassicProblems.kt`): 실험실 E13(`engine-lab/experiments/e13_classic_answers/`)이 원전의 정답 수순을 국면에 맞추고 엔진으로 확인해
 *   `app-android/build/life-and-death-classic-answers/answers.json`에 쓴 것에서 문제를 만든다. 판정 표는 **원전의 수순**으로 만든다
 *   (`AnswerTableJudge.fromSourceLine` — 원전의 수만 정답, 다른 수는 오답 + E13이 고른 엔진의 반박).
 * - 그 밖의 문제: 폰에서 그 자리에서 읽기에 큰 것만(`fitsTheNodeBudget` 한 곳이 정한다) 같은 풀이기로 표를 만든다(`AnswerTableJudge.write`).
 */
class LifeAndDeathAnswerTableExportTest {

    private val content = File(RepoPaths.root, "shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/content")
    private val classicAnswers = File(RepoPaths.root, "app-android/build/life-and-death-classic-answers/answers.json")

    @Test
    fun exportAnswerTables() {
        assumeTrue("-PexportLifeAndDeathAnswerTables를 줄 때만 돈다", System.getProperty("exportLifeAndDeathAnswerTables") == "true")
        val classics = if (classicAnswers.exists()) readClassics(JSONObject(classicAnswers.readText())) else emptyList()
        val made = lifeAndDeathProblems.filter { it.unit.section != LifeAndDeathSection.Classics }
        val tables = made.filterNot { it.fitsTheNodeBudget() }.map { it.id to AnswerTableJudge.write(it, SolvingJudge(it)) } +
            classics.map { it.problem.id to AnswerTableJudge.fromSourceLine(it.problem, it.line) { turn, move -> it.refutation(turn, move) } }
        File(content, "LifeAndDeathClassicProblems.kt").writeText(renderClassics(classics))
        File(content, "LifeAndDeathAnswerTables.kt").writeText(renderTables(tables))
        println("life-and-death: ${classics.size} classic problems, ${tables.size} answer tables")
    }

    private class Classic(
        val problem: LifeAndDeathProblem,
        val line: List<BoardCoordinate>,
        private val refutations: List<Map<String, String?>>,
    ) {
        fun refutation(turn: Int, move: BoardCoordinate?): BoardCoordinate? {
            val key = move?.label(BoardSize.Nineteen) ?: "tenuki"
            return refutations.getOrNull(turn)?.get(key)?.let { BoardCoordinate.fromLabel(it, BoardSize.Nineteen) }
        }
    }

    /** E13의 결과 → 문제. 그림의 `.`은 E13의 문제 구역(처음 돌에서 두 칸 안)과 원전 수순의 자리, 그 밖은 `,`(손 뺌)다. */
    private fun readClassics(json: JSONObject): List<Classic> {
        val items = json.getJSONArray("problems")
        return (0 until items.length()).map { items.getJSONObject(it) }.mapNotNull { item ->
            val unit = when (item.getString("book")) {
                "gokyoshumyo" -> LifeAndDeathUnit.GokyoShumyo
                "hatsuyoron" -> LifeAndDeathUnit.Hatsuyoron
                else -> return@mapNotNull null
            }
            val rows = item.getJSONArray("rows").let { array -> (0 until array.length()).map { array.getString(it) } }
            val line = item.getJSONArray("line").let { array -> (0 until array.length()).map { BoardCoordinate.fromLabel(array.getString(it), BoardSize.Nineteen) } }
            val region = item.getJSONArray("region").let { array -> (0 until array.length()).map { BoardCoordinate.fromLabel(array.getString(it), BoardSize.Nineteen) } }.toSet() + line
            val diagram = (0 until 19).map { r ->
                (0 until 19).joinToString(" ") { c ->
                    when (rows.getOrNull(r)?.getOrNull(c)) {
                        'x', 'b' -> "X"
                        'o', 'w' -> "O"
                        else -> if (BoardCoordinate(r, c) in region) "." else ","
                    }
                }
            }
            val refutations = item.getJSONArray("refutations").let { turns ->
                (0 until turns.length()).map { t ->
                    val entry = turns.getJSONObject(t)
                    entry.keys().asSequence().associateWith { key -> if (entry.isNull(key)) null else entry.getString(key) }
                }
            }
            val problem = LifeAndDeathProblem(
                id = classicId(item.getString("book"), item.getString("bookId")),
                unit = unit,
                goal = LifeAndDeathGoal.valueOf(item.getString("goal")),
                toPlay = StoneColor.Black,
                target = item.getString("target"),
                answer = line.first().label(BoardSize.Nineteen),
                diagram = diagram,
            )
            Classic(problem, line, refutations)
        }.sortedWith(compareBy({ it.problem.unit.ordinal }, { classicOrder(it.problem.id) }))
    }

    /** 저장 키 — 원전의 번호를 ASCII로(발양론 1914년판 「死」부 39번 → `ld.hatsu.d39`). ⚠️ 바꾸지 말 것(푼 기록). */
    private fun classicId(book: String, bookId: String): String = when (book) {
        "gokyoshumyo" -> "ld.gokyo.$bookId"
        "hatsuyoron" -> "ld.hatsu.d" + bookId.substringAfter("-")
        else -> error("unknown book $book")
    }

    private fun classicOrder(id: String): Int =
        id.substringAfterLast('.').filter { it.isDigit() || it == '-' }.split('-').fold(0) { acc, part -> acc * 1000 + (part.toIntOrNull() ?: 0) }

    private fun renderClassics(classics: List<Classic>): String = buildString {
        appendLine("package com.worksoc.goaicoach.shared.content")
        appendLine()
        if (classics.isNotEmpty()) {
            appendLine("import com.worksoc.goaicoach.shared.domain.StoneColor")
            appendLine()
        }
        appendLine("/**")
        appendLine(" * 고전 사활(백로그 #277) — 원전의 국면과 정답 수순에서 옮긴 문제. **손으로 고치지 않는다** —")
        appendLine(" * `./gradlew :app-android:testDebugUnitTest --tests '*LifeAndDeathAnswerTableExportTest*' -PexportLifeAndDeathAnswerTables`가")
        appendLine(" * 실험실 E13의 결과(`app-android/build/life-and-death-classic-answers/answers.json`)에서 판정 표와 함께 다시 쓴다.")
        appendLine(" */")
        if (classics.isEmpty()) {
            appendLine("internal val lifeAndDeathClassicProblems: List<LifeAndDeathProblem> = emptyList()")
            return@buildString
        }
        appendLine("internal val lifeAndDeathClassicProblems: List<LifeAndDeathProblem> = listOf(")
        classics.forEach { classic ->
            val p = classic.problem
            appendLine("    LifeAndDeathProblem(")
            appendLine("        id = \"${p.id}\",")
            appendLine("        unit = LifeAndDeathUnit.${p.unit.name},")
            appendLine("        goal = LifeAndDeathGoal.${p.goal.name},")
            appendLine("        toPlay = StoneColor.Black,")
            appendLine("        target = \"${p.target}\",")
            appendLine("        answer = \"${p.answer}\",")
            appendLine("        diagram = listOf(")
            p.diagram.forEach { appendLine("            \"$it\",") }
            appendLine("        ),")
            appendLine("    ),")
        }
        appendLine(")")
    }

    private fun renderTables(tables: List<Pair<String, String>>): String = buildString {
        appendLine("package com.worksoc.goaicoach.shared.content")
        appendLine()
        appendLine("/**")
        appendLine(" * 맥에서 미리 읽어 둔 판정 표(백로그 #277) — 문제 id → 표의 글([AnswerTableJudge]). **손으로 고치지 않는다** —")
        appendLine(" * `./gradlew :app-android:testDebugUnitTest --tests '*LifeAndDeathAnswerTableExportTest*' -PexportLifeAndDeathAnswerTables`가 다시 쓴다.")
        appendLine(" * 폰에서 그 자리에서 읽기에 큰 문제와 고전(원전의 수순으로 만든 표)만 여기 있다(나머지는 [SolvingJudge]가 읽는다).")
        appendLine(" */")
        if (tables.isEmpty()) {
            appendLine("internal val lifeAndDeathAnswerTables: Map<String, String> = emptyMap()")
            return@buildString
        }
        appendLine("internal val lifeAndDeathAnswerTables: Map<String, String> = mapOf(")
        tables.forEach { (id, text) ->
            appendLine("    \"$id\" to \"\"\"")
            text.lines().forEach { appendLine(it) }
            appendLine("\"\"\",")
        }
        appendLine(")")
    }
}
