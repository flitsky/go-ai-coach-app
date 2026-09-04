package com.worksoc.goaicoach.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **`@Test`가 빠진 테스트 함수는 조용히 사라진다** — 이 저장소가 실제로 그렇게 8건을 잃었던
 * 사고의 그물(백로그 #68, 2026-09-03).
 *
 * ⚠️ **아무것도 실패하지 않는 종류의 사고다.** 컴파일도 되고, 빌드도 초록이고, 테스트 수만
 * 조용히 줄어든다. `AttendanceRewardGrantTest`에서 **여덟 함수**가 그렇게 한 번도 돌지 않은 채
 * 남아 있었고, 그중에는 무르기 그랜드파더링(#66이 *"지우면 기존 보유자가 전원 잠긴다"* 고 경고한
 * 바로 그 판정)과 주간 반복 회차가 캐릭터 회차를 건너뛰는지를 지키는 것이 들어 있었다.
 * 되살려 보니 여덟 다 통과했으므로 **숨은 회귀는 없었지만, 그동안의 초록은 근거가 없었다.**
 *
 * 검사 대상은 `shared`의 공용 테스트와 `app-android`의 단위 테스트 소스 전부다.
 *
 * ⚠️ **한 가지 사각지대가 있다 — Gradle의 up-to-date 판정.** 이 테스트는 소스 파일을 **실행 중에**
 * 읽지만 Gradle은 그것을 입력으로 모르므로, `shared`의 테스트 소스만 바뀐 빌드에서는
 * `:app-android:testDebugUnitTest`가 통째로 건너뛰어져 **이 그물도 함께 쉰다.** app-android 쪽이
 * 한 줄이라도 바뀌면 다시 돈다. 확실히 확인하려면 `--rerun-tasks`를 붙일 것.
 *
 * ⚠️ **헬퍼와 테스트를 이름이 아니라 모양으로 가른다** — 클래스 본문에 있고, 인자가 없고,
 * 반환형을 적지 않은(`Unit`) 함수만 테스트 후보로 본다. `checkInAt(...)`/`grant(...)`처럼 인자나
 * 반환형이 있는 픽스처 헬퍼는 애초에 후보가 아니다.
 */
class TestAnnotationContractTest {

    @Test
    fun everyTestShapedFunctionCarriesTheTestAnnotation() {
        val roots = listOf(
            repoRoot().resolve("shared/src/commonTest"),
            repoRoot().resolve("app-android/src/test"),
        ).filter { it.exists() }
        check(roots.isNotEmpty()) { "테스트 소스 루트를 하나도 찾지 못했다 — 이 그물이 아무것도 안 보고 있다." }

        val offenders = roots
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .flatMap { file -> unannotatedTestFunctions(file) }

        assertEquals(
            "@Test가 빠져 한 번도 실행되지 않는 테스트 함수가 있다 — 빌드는 초록인데 그 계약은 " +
                "지켜지지 않는다. 헬퍼라면 인자나 반환형을 주어 모양으로 구분되게 할 것.\n" +
                offenders.joinToString("\n") { "  - $it" },
            emptyList<String>(),
            offenders,
        )
    }

    /** 파일 하나에서 `@Test` 없는 테스트 모양 함수를 찾아 `경로:줄 이름`으로 돌려준다. */
    private fun unannotatedTestFunctions(file: File): List<String> {
        val lines = file.readLines()
        val signature = Regex("""^\s{4}(?:internal\s+|private\s+)?fun\s+(\w+)\(\)\s*\{\s*$""")
        return lines.mapIndexedNotNull { index, line ->
            val name = signature.find(line)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
            if (precedingAnnotationIsTest(lines, index)) return@mapIndexedNotNull null
            "${file.relativeTo(repoRoot()).path}:${index + 1}  $name"
        }
    }

    /**
     * 함수 바로 위 줄을 거슬러 올라가며 `@Test`를 찾는다.
     *
     * ⚠️ 주석·KDoc·빈 줄은 건너뛴다 — 이 저장소는 테스트마다 긴 KDoc을 붙이므로, 바로 윗줄만
     * 보면 **KDoc이 달린 테스트를 전부 위반으로 잡는다.**
     */
    private fun precedingAnnotationIsTest(lines: List<String>, functionIndex: Int): Boolean {
        var cursor = functionIndex - 1
        while (cursor >= 0) {
            val trimmed = lines[cursor].trim()
            val isCommentOrBlank = trimmed.isEmpty() ||
                trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")
            if (!isCommentOrBlank) {
                return trimmed == "@Test" || trimmed == "@org.junit.Test" || trimmed == "@kotlin.test.Test"
            }
            cursor--
        }
        return false
    }

    private fun repoRoot(): File {
        var current = File(".").canonicalFile
        while (true) {
            if (File(current, "settings.gradle.kts").exists()) return current
            current = current.parentFile ?: break
        }
        error("Could not locate repository root from ${File(".").canonicalPath}")
    }
}
