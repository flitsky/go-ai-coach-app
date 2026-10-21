package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.shared.content.AnswerTableJudge
import com.worksoc.goaicoach.shared.content.SolvingJudge
import com.worksoc.goaicoach.shared.content.fitsTheNodeBudget
import com.worksoc.goaicoach.shared.content.lifeAndDeathProblems
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 폰에서 그 자리에서 읽기에 큰 사활 문제의 **판정 표**를 맥에서 만들어 `LifeAndDeathAnswerTables.kt`를 다시 쓴다(백로그 #277) — 통과 기준을
 * 거는 테스트가 아니다. `make test`에서는 돌지 않는다 —
 * `./gradlew :app-android:testDebugUnitTest --tests '*LifeAndDeathAnswerTableExportTest*' -PexportLifeAndDeathAnswerTables`.
 *
 * 어느 문제가 표를 갖는지는 `fitsTheNodeBudget` 한 곳이 정한다(실은 문제를 되묻는 테스트와 같은 함수). 표는 같은 풀이기가 정답의 길을 모두
 * 돌며 차례마다 판이 받는 수 전부를 판정한 것이다(`AnswerTableJudge.write`) — 문제를 더하거나 고치면 다시 돌린다(낡은 표는 테스트가 잡는다).
 */
class LifeAndDeathAnswerTableExportTest {

    @Test
    fun exportAnswerTables() {
        assumeTrue("-PexportLifeAndDeathAnswerTables를 줄 때만 돈다", System.getProperty("exportLifeAndDeathAnswerTables") == "true")
        val tables = lifeAndDeathProblems
            .filterNot { it.fitsTheNodeBudget() }
            .map { problem -> problem.id to AnswerTableJudge.write(problem, SolvingJudge(problem)) }
        val target = File(RepoPaths.root, "shared/src/commonMain/kotlin/com/worksoc/goaicoach/shared/content/LifeAndDeathAnswerTables.kt")
        target.writeText(render(tables))
        println("life-and-death answer tables: ${tables.size} → ${target.path}")
    }

    private fun render(tables: List<Pair<String, String>>): String = buildString {
        appendLine("package com.worksoc.goaicoach.shared.content")
        appendLine()
        appendLine("/**")
        appendLine(" * 맥에서 미리 읽어 둔 판정 표(백로그 #277) — 문제 id → 표의 글([AnswerTableJudge]). **손으로 고치지 않는다** —")
        appendLine(" * `./gradlew :app-android:testDebugUnitTest --tests '*LifeAndDeathAnswerTableExportTest*' -PexportLifeAndDeathAnswerTables`가 다시 쓴다.")
        appendLine(" * 폰에서 그 자리에서 읽기에 큰 문제만 여기 있다(나머지는 [SolvingJudge]가 읽는다).")
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
