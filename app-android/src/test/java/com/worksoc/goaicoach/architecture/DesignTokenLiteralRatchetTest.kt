package com.worksoc.goaicoach.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 색 리터럴은 `ui.designsystem` **안에만** 있다(refactor backlog #51).
 *
 * 2026-09-28까지 `Color(0xFF…)`·`Color.White` 같은 리터럴이 ui 17개 파일에 130곳 흩어져 있었다. 그것을
 * 값 그대로 디자인 시스템(`AppPalette.kt`·`GoBoardPalette.kt`·`GoBoardTheme.kt`·`PremiumTheme.kt`·
 * `AppColorScheme.kt`)으로 옮기고 **기준선을 0으로 박았다.** 새 화면이 색을 쓰려면 먼저
 * `MaterialTheme.colorScheme`의 역할을 찾고, 없으면 디자인 시스템에 이름을 붙여 둔다 — 화면 파일에
 * 리터럴이 다시 생기면 여기서 빨개진다.
 *
 * ## 무엇을 "리터럴"로 세는가
 * - `Color(0x…)` — 16진 값.
 * - `Color(1f, 0f, 0f)`·`Color(red = 0.5f, …)` — **숫자로 시작하는** 성분 생성자. `Color(red = red * 0.62f, …)`
 *   처럼 기존 색을 계산하는 코드는 리터럴이 아니다(`GoBoard.kt`의 `darken`/`brighten`).
 * - `Color.White`·`Color.Black`·`Color.Gray` 같은 이름 붙은 상수, `Color.hsv(…)`/`Color.hsl(…)`.
 * - `android.graphics.Color`의 상수·`rgb(…)`/`argb(…)`/`parseColor(…)` — 네이티브 캔버스 글자색도 색이다.
 *
 * ## 예외(명시 목록 — 늘릴 때는 사유를 적을 것)
 * [SentinelConstants]는 **색이 아니라 "색 없음"** 이다. `Color.Unspecified`는 "호출자가 안 줬다"는 기본값
 * 표시(`SocialLoginButton`의 `glyphColor`, 첫돌이 아바타의 `seamColor`)이고, `Transparent`는 AdView처럼
 * 바탕을 **비우는** 값이다. 이름을 붙여 옮겨 봐야 뜻이 흐려질 뿐이라 센 대상에서 뺀다.
 *
 * ⚠️ 주석과 문자열 안은 세지 않는다 — KDoc이 `Color.Gray`라는 규칙을 **글로** 적는 것은 권장 사항이다.
 */
class DesignTokenLiteralRatchetTest {

    @Test
    fun colorLiteralsLiveOnlyInTheDesignSystem() {
        val found = screenFiles().flatMap { file ->
            ColorLiteralScanner.literals(file.readText()).map { (line, literal) ->
                "${file.relativeTo(RepoPaths.uiRoot).path}:$line  $literal"
            }
        }

        assertEquals(
            "ui.designsystem 밖에 색 리터럴이 생겼다(기준선 $Baseline). 먼저 `MaterialTheme.colorScheme`의 역할을 " +
                "찾고, 없으면 `ui/designsystem/AppPalette.kt`(바둑판 위라면 `GoBoardPalette.kt`)에 이름을 붙여 옮겨라 " +
                "— 값은 그대로 두고(refactor backlog #51).\n" + found.joinToString("\n") { "  - $it" },
            Baseline,
            found.size,
        )
    }

    /** 검사가 **헛돌지 않는지** — 경로가 바뀌어 한 파일도 안 읽으면 위 테스트는 조용히 초록이 된다. */
    @Test
    fun theScanActuallyReadsTheUiTreeAndTheDesignSystemOwnsThePalette() {
        val screens = screenFiles()
        val designSystem = RepoPaths.uiSourceFiles().filter { it.isInDesignSystem() }

        assertTrue("ui 화면 파일을 거의 못 읽었다(${screens.size}개) — RepoPaths.uiRoot를 확인하라.", screens.size > 100)
        assertTrue(
            "디자인 시스템에 색 리터럴이 없다 — 팔레트가 옮겨졌다면 이 검사의 기준 경로도 옮겨라.",
            designSystem.sumOf { ColorLiteralScanner.literals(it.readText()).size } > 50,
        )
    }

    @Test
    fun theScannerSeesEveryLiteralFormAndNothingElse() {
        val sample = """
            val a = Color(0xFF123456)
            val b = Color.White.copy(alpha = 0.5f)
            val c = Color(1f, 0f, 0f)
            val d = Color(red = 0.2f, green = 0f, blue = 0f)
            val e = android.graphics.Color.rgb(1, 2, 3)
            val f = android.graphics.Color.BLACK
            val g = Color.hsv(10f, 1f, 1f)
            // Color(0xFF000000) in a comment
            /* Color.Black in a block comment */
            val h = "Color.Gray in a string"
            val i = StoneColor.White
            val j = Color.Unspecified
            val k = Color.Transparent
            val l = android.graphics.Color.TRANSPARENT
            val m = Color(red = red * 0.62f, green = green, blue = blue)
            val n = MaterialTheme.colorScheme.onPrimary
        """.trimIndent()

        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7),
            ColorLiteralScanner.literals(sample).map { it.first },
        )
    }

    private fun screenFiles(): List<File> = RepoPaths.uiSourceFiles().filterNot { it.isInDesignSystem() }

    private fun File.isInDesignSystem(): Boolean =
        relativeTo(RepoPaths.uiRoot).invariantSeparatorsPath.startsWith("$DesignSystemDir/")

    private companion object {
        const val DesignSystemDir = "designsystem"

        /** ⚠️ 0에서 올리지 말 것. 새 예외가 필요하면 [SentinelConstants]에 **사유와 함께** 더하라. */
        const val Baseline = 0
    }
}

/** 소스 텍스트에서 색 리터럴을 (줄 번호, 리터럴) 목록으로 뽑는다. 주석·문자열은 먼저 지운다. */
internal object ColorLiteralScanner {
    private val SentinelConstants = setOf(
        "Color.Unspecified",
        "Color.Transparent",
        "android.graphics.Color.TRANSPARENT",
    )

    private val Patterns = listOf(
        Regex("""\bandroid\.graphics\.Color\.(?:[A-Z_]+\b|(?:rgb|argb|parseColor|valueOf)\()"""),
        Regex("""(?<![\w.])Color\(\s*0x[0-9A-Fa-f_]+"""),
        Regex("""(?<![\w.])Color\(\s*(?:red\s*=\s*)?\d"""),
        Regex("""(?<![\w.])Color\.(?:hsv|hsl)\("""),
        Regex("""(?<![\w.])Color\.[A-Z][A-Za-z]*\b"""),
    )

    fun literals(source: String): List<Pair<Int, String>> =
        codeOnly(source).lines().flatMapIndexed { index, line ->
            val hits = mutableListOf<Pair<Int, String>>()
            val taken = mutableListOf<IntRange>()
            Patterns.forEach { pattern ->
                pattern.findAll(line).forEach { match ->
                    val overlaps = taken.any { it.first <= match.range.last && match.range.first <= it.last }
                    if (!overlaps && match.value !in SentinelConstants) {
                        hits += (index + 1) to match.value
                    }
                    taken += match.range
                }
            }
            hits
        }

    /** 줄 수를 지키며 주석과 문자열 내용을 공백으로 바꾼다. */
    private fun codeOnly(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("//", i) -> {
                    val end = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                    repeat(end - i) { out.append(' ') }
                    i = end
                }
                source.startsWith("/*", i) -> {
                    val end = source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
                    source.substring(i, end).forEach { out.append(if (it == '\n') '\n' else ' ') }
                    i = end
                }
                source.startsWith("\"\"\"", i) -> {
                    val end = source.indexOf("\"\"\"", i + 3).let { if (it < 0) source.length else it + 3 }
                    source.substring(i, end).forEach { out.append(if (it == '\n') '\n' else ' ') }
                    i = end
                }
                source[i] == '"' -> {
                    var j = i + 1
                    while (j < source.length && source[j] != '"' && source[j] != '\n') {
                        if (source[j] == '\\') j++
                        j++
                    }
                    val end = minOf(j + 1, source.length)
                    repeat(end - i) { out.append(' ') }
                    i = end
                }
                else -> {
                    out.append(source[i])
                    i++
                }
            }
        }
        return out.toString()
    }
}
