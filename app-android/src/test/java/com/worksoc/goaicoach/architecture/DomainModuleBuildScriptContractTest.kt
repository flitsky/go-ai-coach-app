package com.worksoc.goaicoach.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **`:core:domain`의 본 소스셋은 아무것에도 기대지 않는다**(refactor backlog #49·#81).
 *
 * 이 모듈이 따로 있는 이유가 그것 하나다 — 판·수·규칙이 코루틴도, 엔진 계약도, 앱 계층도 모른다는
 * 것을 **컴파일러가** 지키게 하려는 것. 그런데 컴파일러는 *지금 선언된 의존*만 지킨다. 누가
 * `commonMain.dependencies { implementation(libs.kotlinx.coroutines.core) }` 한 줄을 더하면 그 순간부터
 * 도메인이 코루틴을 import해도 빌드는 초록이고, 그 한 줄을 막는 것은 아무것도 없었다. 이 계약이 그 줄을 막는다.
 *
 * 허용되는 의존 선언은 **commonTest의 `kotlin("test")` 한 줄**뿐이다. 판정은 [DomainBuildScriptVerdict]가
 * 빌드 스크립트 문자열로 한다(주석은 걷어 낸다) — 자기검증이 심은 위반으로 같은 판정기를 시험한다.
 */
class DomainModuleBuildScriptContractTest {

    @Test
    fun domainModuleDeclaresNoDependencyOutsideCommonTestKotlinTest() {
        val script = RepoPaths.coreDomainBuildScript.readContractSource()
        assertEquals(
            "core/domain/build.gradle.kts가 commonTest의 kotlin(\"test\") 말고 의존을 선언했다 — 도메인 커널은 " +
                "아무것에도 기대지 않는다. 그 코드가 무엇을 필요로 한다면 이 모듈이 아니라 위 모듈에 속한다.\n" +
                DomainBuildScriptVerdict.offenders(script).joinToString("\n") { "  - $it" },
            emptyList<String>(),
            DomainBuildScriptVerdict.offenders(script),
        )
    }

    @Test
    fun verdictCatchesPlantedDependencies() {
        val allowed = """
            kotlin {
                sourceSets {
                    commonTest.dependencies {
                        implementation(kotlin("test"))
                    }
                }
            }
        """.trimIndent()
        assertEquals(emptyList<String>(), DomainBuildScriptVerdict.offenders(allowed))
        // 주석 안의 의존은 선언이 아니다.
        assertEquals(
            emptyList<String>(),
            DomainBuildScriptVerdict.offenders(
                allowed.replace("kotlin {", "// commonMain.dependencies { implementation(libs.x) }\n/* dependencies { api(project(\":a\")) } */\nkotlin {"),
            ),
        )

        val planted = mapOf(
            "commonMain 의존" to allowed.replace(
                "commonTest.dependencies {",
                "commonMain.dependencies {\n implementation(libs.kotlinx.coroutines.core)\n }\n commonTest.dependencies {",
            ),
            "최상위 dependencies 블록" to "$allowed\ndependencies {\n    implementation(project(\":shared\"))\n}",
            "commonTest에 한 줄 더" to allowed.replace(
                "implementation(kotlin(\"test\"))",
                "implementation(kotlin(\"test\"))\n implementation(project(\":shared\"))",
            ),
            "소스셋 블록 안의 dependencies" to allowed.replace(
                "commonTest.dependencies {",
                "androidMain { dependencies { implementation(libs.x) } }\n commonTest.dependencies {",
            ),
            "getByName 꼴" to "$allowed\nkotlin.sourceSets.getByName(\"commonMain\").dependencies { api(libs.y) }",
            "의존 블록 밖의 라이브러리 참조" to "$allowed\nconfigurations.all { libs.kotlinx.coroutines.core }",
            "허용 블록이 사라짐" to allowed.replace("commonTest.dependencies {", "commonTest.languageSettings {"),
        )
        planted.forEach { (label, script) ->
            assertTrue("심은 위반을 못 잡았다: $label", DomainBuildScriptVerdict.offenders(script).isNotEmpty())
        }
    }
}

/** `:core:domain` 빌드 스크립트 판정기 — 입력은 스크립트 문자열 하나라 자기검증이 디스크 없이 쓴다. */
internal object DomainBuildScriptVerdict {

    private val DEPENDENCIES_WORD = Regex("""(?<![\w.])(?:[\w.]*\.)?dependencies\b""")
    private val ALLOWED_OPENING = Regex("""^commonTest\.dependencies\s*\{""")
    private const val ALLOWED_BODY = """implementation(kotlin("test"))"""

    /** 의존을 더하는 호출 — Gradle 구성 이름(`implementation`·`api`·`…Implementation` 등)이나 `project(…)`. */
    private val DEPENDENCY_CALL = Regex(
        """(?<![\w.])(?:implementation|api|compileOnly|runtimeOnly|compileOnlyApi|kapt|ksp|coreLibraryDesugaring|project|\w+(?:Implementation|Api|CompileOnly|RuntimeOnly))\s*\(""",
    )

    /** 버전 카탈로그의 라이브러리 참조 — 플러그인(`libs.plugins.…`)과 버전(`libs.versions.…`)만 된다. */
    private val LIBRARY_REFERENCE = Regex("""(?<![\w.])libs\.(?!plugins\.|versions\.)\w+""")

    fun offenders(script: String): List<String> {
        val code = withoutComments(script)
        val offenders = mutableListOf<String>()
        val allowedBlocks = mutableListOf<IntRange>()
        for (match in DEPENDENCIES_WORD.findAll(code)) {
            val opening = ALLOWED_OPENING.find(code.substring(match.range.first))
            if (opening == null) {
                offenders += "commonTest 밖의 의존 선언: ${lineAt(code, match.range.first)}"
                continue
            }
            val open = match.range.first + opening.value.length - 1
            val close = closingBrace(code, open)
            val body = code.substring(open + 1, close).filterNot { it.isWhitespace() }
            if (body != ALLOWED_BODY) {
                offenders += "commonTest에는 $ALLOWED_BODY 한 줄만 둔다: ${body.ifEmpty { "(빈 블록)" }}"
            }
            allowedBlocks += open..close
        }
        if (allowedBlocks.size != 1) {
            offenders += "commonTest.dependencies { $ALLOWED_BODY } 블록이 정확히 하나여야 한다(${allowedBlocks.size}개)"
        }
        (DEPENDENCY_CALL.findAll(code) + LIBRARY_REFERENCE.findAll(code))
            .filter { hit -> allowedBlocks.none { hit.range.first in it } }
            .forEach { hit -> offenders += "의존 블록 밖의 의존 참조: ${lineAt(code, hit.range.first)}" }
        return offenders
    }

    /** [open]의 `{`에 맞는 `}` 위치. 안 닫히면 끝. */
    private fun closingBrace(code: String, open: Int): Int {
        var depth = 0
        for (i in open until code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return i
            }
        }
        return code.length
    }

    private fun lineAt(code: String, pos: Int): String {
        val start = code.lastIndexOf('\n', pos - 1) + 1
        val end = code.indexOf('\n', pos).let { if (it < 0) code.length else it }
        return code.substring(start, end).trim()
    }

    /** `//`·`/* */`(중첩 포함) 주석을 걷어 낸다. 문자열 안의 `//`는 건드리지 않는다. */
    private fun withoutComments(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            when {
                src.startsWith("/*", i) -> {
                    var depth = 1
                    i += 2
                    while (i < src.length && depth > 0) {
                        when {
                            src.startsWith("/*", i) -> { depth++; i += 2 }
                            src.startsWith("*/", i) -> { depth--; i += 2 }
                            else -> { if (src[i] == '\n') out.append('\n'); i++ }
                        }
                    }
                }
                src.startsWith("//", i) -> while (i < src.length && src[i] != '\n') i++
                src[i] == '"' -> {
                    var j = i + 1
                    while (j < src.length && src[j] != '"' && src[j] != '\n') {
                        if (src[j] == '\\') j++
                        j++
                    }
                    val end = minOf(src.length, j + 1)
                    out.append(src, i, end)
                    i = end
                }
                else -> { out.append(src[i]); i++ }
            }
        }
        return out.toString()
    }
}
