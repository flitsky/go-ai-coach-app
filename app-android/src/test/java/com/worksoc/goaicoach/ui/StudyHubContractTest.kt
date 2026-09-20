package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 학습 허브(백로그 #163)가 지켜야 할 배치. **전부 어겨도 컴파일은 되고 화면도 뜬다** —
 * 그래서 소스 계약으로 든다. 구조는 `GameReplayContractTest`와 같다.
 */
class StudyHubContractTest {

    /** 주석·import를 걷어낸 본문만 본다 — 이름이 주석에 남아 그물이 헐거워지는 것을 막는다(함정 10-2). */
    private fun source(path: String): String =
        File(path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    private val hub = source("src/main/java/com/worksoc/goaicoach/ui/StudyScreen.kt")
    private val videos = source("src/main/java/com/worksoc/goaicoach/ui/StudyVideoListScreen.kt")
    private val shell = source("src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")

    /**
     * ⚠️ **셸의 상태 훅 예산은 42/42로 여유 0이다**(함정 3). 하위 분류를 `ScreenDestination`으로
     * 올리면 목적지 하나당 최소 한 줄의 상태가 셸에 생겨 `LayeringContractTest`가 깨지는데,
     * 그 실패 메시지는 "셸이 커졌다"고만 말해서 다음 사람이 원인을 여기서 찾지 못한다.
     * 다시보기가 같은 이유로 하위 상태가 됐다(백로그 #156).
     */
    @Test
    fun theShellDoesNotKnowAboutTheStudySubCategories() {
        listOf("StudyCategory", "StudyVideoListScreen", "studyVideoEntries").forEach { name ->
            assertFalse(
                "`GoCoachApp.kt`가 `$name`을 안다 — 학습 하위 분류 배선이 셸로 올라왔다. " +
                    "하위 상태는 학습 허브가 소유한다(백로그 #163).",
                shell.contains(name),
            )
        }
        assertTrue(
            "셸이 더 이상 `StudyScreen`을 부르지 않는다 — 허브까지 화면 안으로 들어가면 " +
                "「학습 하기」로 가는 길이 사라진다.",
            shell.contains("StudyScreen("),
        )
    }

    /**
     * ⚠️ **이 조각의 본체가 여기다**(백로그 #163). 뒤로가기 경로가 **둘**이다 — 상단 화살표와
     * 시스템 뒤로가기. 셸의 `BackHandler`는 목적지가 Home이 아니면 무조건 `exitToHome()`을
     * 부르므로, 하위 화면이 자기 핸들러를 갖지 않으면 **허브를 건너뛰고 홈으로 튄다.**
     * 화살표만 고치고 이걸 잊는 것이 가장 흔한 실수라 그물로 든다.
     */
    @Test
    fun theVideoListCatchesSystemBackItself() {
        assertTrue(
            "`StudyVideoListScreen`에 중첩 `BackHandler`가 없다 — 시스템 뒤로가기가 허브를 " +
                "건너뛰고 홈으로 튄다(셸이 `exitToHome()`을 부른다).",
            videos.contains("BackHandler"),
        )
    }

    /**
     * ⚠️ **아직 없는 분류는 눌리면 안 된다**(U-16, 2026-09-20 사용자 결정 — 토스트가 아니라
     * **비활성** + 「준비 중」 배지). `available`을 보지 않고 무조건 `clickable`을 달면
     * 빈 화면으로 들어가거나 아무 일도 안 일어나 고장으로 읽힌다.
     */
    @Test
    fun theHubGatesRowsOnAvailability() {
        assertTrue(
            "허브가 `available`을 보지 않는다 — 준비되지 않은 분류가 눌린다(U-16).",
            hub.contains("category.available"),
        )
        assertTrue(
            "「준비 중」 배지 문구를 쓰지 않는다 — 회색 줄이 왜 안 눌리는지 알 수 없다(U-16).",
            hub.contains("studyComingSoon"),
        )
    }

    /**
     * ⚠️ **[UiStrings]는 JVM 인자 한도 255칸에 이미 딱 붙어 있다 — 여유가 0이다.**
     *
     * 이 조각을 짜면서 문구 아홉 줄을 생성자에 더했더니 **앱은 멀쩡히 컴파일되고 테스트만
     * 570개 중 29개가** `ClassFormatError: Too many arguments in method signature` **로 죽었다.**
     * 컴파일러가 막아 주지 않고, 실패 메시지도 "문구를 너무 많이 더했다"고 말해 주지 않는다.
     *
     * 한도를 먼저 깨는 것은 생성자가 아니라 **data class가 자동으로 만드는 `copy$default`** 다.
     * 그 정적 메서드는 `인스턴스 1 + 필드 N + 기본값 비트마스크 ceil(N/32) + 마커 1` 칸을 쓴다 —
     * 필드가 245개인 지금 245+1+8+1 = **정확히 255칸**이다. **한 칸만 더해도 깨진다.**
     *
     * 그래서 새 문구는 생성자에 더하지 말고 `UiStringsStudyCategories.kt`처럼
     * **곁표 + 멤버 함수**로 뺄 것 — 함수는 이 산수에 끼지 않는다.
     */
    @Test
    fun theUiStringsConstructorStaysUnderTheJvmArgumentLimit() {
        val declaration = File("src/main/java/com/worksoc/goaicoach/ui/UiStrings.kt").readText()
            .substringAfter("internal data class UiStrings(")
            .substringBefore("\n) {")
        val fields = Regex("""^\s{4}val\s+\w+:""", RegexOption.MULTILINE).findAll(declaration).count()
        val copyDefaultSlots = 1 + fields + (fields + 31) / 32 + 1

        assertTrue(
            "`UiStrings`의 필드가 ${fields}개라 `copy\$default`가 ${copyDefaultSlots}칸을 쓴다 — " +
                "JVM 한도 255칸을 넘겨 **테스트 전체가** `ClassFormatError`로 죽는다. " +
                "새 문구는 생성자가 아니라 곁표(`UiStringsStudyCategories.kt` 꼴)로 뺄 것.",
            copyDefaultSlots <= 255,
        )
    }

    /** 곁표가 네 언어를 다 채웠는지 — 하나가 비면 화면에 `LifeAndDeath`라고 뜬다. */
    @Test
    fun everyCategoryHasCopyInEveryLanguage() {
        StudyCategory.entries.forEach { category ->
            UiLanguage.entries.forEach { language ->
                assertFalse(
                    "$category / $language 의 이름이 비어 폴백(enum 이름)이 노출된다.",
                    studyCategoryTitleFor(language, category) == category.name,
                )
                assertFalse(
                    "$category / $language 의 한 줄 소개가 비어 폴백(enum 이름)이 노출된다.",
                    studyCategorySubtitleFor(language, category) == category.name,
                )
            }
        }
        assertEquals(
            "「준비 중」 배지가 네 언어를 다 채우지 못했다.",
            UiLanguage.entries.size,
            UiLanguage.entries.map { studyComingSoonFor(it) }.distinct().size,
        )
    }
}
