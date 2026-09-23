package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.StudyLessonTrack
import com.worksoc.goaicoach.shared.studyLessons
import com.worksoc.goaicoach.architecture.readContractSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 학습 단원 화면(백로그 #164 「바둑 규칙 배우기」 · #183 「바둑 기초 행마」)이 지켜야 할 것.
 * 판 위의 사실은 `shared`의 `StudyLessonsTest`가 보고, 여기는 **문구와 배치**를 본다.
 */
class StudyLessonContractTest {

    /** 주석·import를 걷어낸 본문만 본다 — 이름이 주석에 남아 그물이 헐거워지는 것을 막는다(함정 10-2). */
    private fun source(path: String): String =
        File(path).readContractSource()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    private val screen = source("src/main/java/com/worksoc/goaicoach/ui/StudyLessonScreen.kt")
    private val hub = source("src/main/java/com/worksoc/goaicoach/ui/StudyScreen.kt")

    /**
     * ⚠️ **빈칸은 조용하다.** 표에 한 언어가 빠지면 폴백이 돌아 화면에 `ko.retake` 같은 키가
     * 그대로 뜬다 — 한국어로 보는 사람에게는 끝까지 보이지 않는다.
     *
     * ⚠️ 값을 폴백과 견주지 않고 **표의 열쇠를 직접 센다**(`studyLessonTitleLanguages`) —
     * 영어 단원 이름 `Liberties`가 enum 이름과 같은 낱말이라, 값 비교로는 멀쩡한 줄이
     * 빠진 줄로 읽힌다.
     */
    @Test
    fun everyLessonAndStepHasCopyInEveryLanguage() {
        val all = UiLanguage.entries.toSet()
        studyLessons.forEach { lesson ->
            assertEquals(
                "${lesson.id} 의 단원 이름이 네 언어를 다 채우지 못했다.",
                all,
                studyLessonTitleLanguages(lesson.id),
            )
            assertEquals(
                "${lesson.id} 의 한 줄 소개가 네 언어를 다 채우지 못했다.",
                all,
                studyLessonSummaryLanguages(lesson.id),
            )
            lesson.steps.forEach { step ->
                assertEquals(
                    "${step.id} 의 본문이 네 언어를 다 채우지 못했다.",
                    all,
                    studyLessonBodyLanguages(step.id),
                )
            }
        }
    }

    /** 단계를 지웠는데 본문만 남으면 아무도 안 읽는 문구를 네 언어로 유지하게 된다. */
    @Test
    fun noOrphanStepCopyRemains() {
        val used = studyLessons.flatMap { lesson -> lesson.steps.map { it.id } }.toSet()
        assertEquals(
            "어느 단계도 쓰지 않는 본문이 표에 남아 있다.",
            emptySet<String>(),
            studyLessonBodyKeys() - used,
        )
    }

    /** 조작부 문구 넷도 네 언어를 다 채워야 한다 — 같은 값이 겹치면 한 언어가 빠진 것이다. */
    @Test
    fun everyControlLabelHasCopyInEveryLanguage() {
        listOf(
            "이전" to UiLanguage.entries.map { studyLessonPreviousFor(it) },
            "다음" to UiLanguage.entries.map { studyLessonNextFor(it) },
            "다음 단원" to UiLanguage.entries.map { studyLessonNextLessonFor(it) },
            "단원 목록" to UiLanguage.entries.map { studyLessonBackToListFor(it) },
        ).forEach { (name, labels) ->
            assertEquals(
                "「$name」 버튼이 네 언어를 다 채우지 못했다: $labels",
                UiLanguage.entries.size,
                labels.distinct().size,
            )
        }
    }

    /**
     * ⚠️ **`Text`는 마크다운을 모른다** — `**굵게**`라고 적으면 별표가 그대로 그려진다.
     * 문서를 쓰던 손이 그대로 문구를 쓰면 걸리는 자리라 그물로 든다.
     */
    @Test
    fun noLessonCopyUsesMarkdown() {
        UiLanguage.entries.forEach { language ->
            studyLessons.flatMap { it.steps }.forEach { step ->
                val body = studyLessonBodyFor(language, step.id)
                assertFalse(
                    "${step.id} / $language 의 본문에 마크다운(`**`)이 남아 별표가 화면에 그려진다.",
                    body.contains("**"),
                )
            }
        }
    }

    /**
     * ⚠️ **판을 스크롤 안에 넣지 말 것**(함정 44). `GoBoard`는 `awaitFirstDown().consume()`을
     * `inputEnabled` 판정보다 **먼저** 하므로(`GoBoard.kt:238`), 읽기 전용이어도 판 위에서
     * 시작한 끌기를 삼킨다 — 스크롤 부모를 두면 판 위에서 화면이 굴러가지 않는다. 손으로
     * 밟아 보기 전에는 아무도 모르고, 다시보기 화면이 이미 같은 값을 치렀다.
     *
     * 판보다 **뒤에** 선언된 설명 상자만 구르게 두었는지를 본다.
     */
    @Test
    fun theBoardIsNotInsideAScrollParent() {
        // ⚠️ **자르는 표식이 없으면 크게 실패시킨다.** `substringAfter`는 표식을 못 찾으면
        // **문자열 전체**를 돌려준다 — 이름을 바꾼 날 그물이 조용히 다른 것을 재기 시작한다
        // (#183의 이름 정리에서 실제로 걸렸다. 그때는 빨개져서 알았지만, 반대로 초록이 될
        // 수도 있었다).
        val marker = "private fun StudyLessonScreen("
        assertTrue("`$marker` 를 찾지 못했다 — 그물이 파일 전체를 재고 있다.", screen.contains(marker))
        val lessonScreen = screen.substringAfter(marker)
        val board = lessonScreen.indexOf("GoBoard(")
        val scroll = lessonScreen.indexOf("verticalScroll")

        assertTrue("단원 화면에서 `GoBoard(`를 찾지 못했다 — 그물이 헛돌고 있다.", board >= 0)
        assertTrue("단원 화면에서 `verticalScroll`을 찾지 못했다 — 설명이 길어지면 잘린다.", scroll >= 0)
        assertTrue(
            "`verticalScroll`이 판보다 먼저 선언됐다 — 판이 스크롤 안에 들어갔을 수 있다. " +
                "판 위에서 시작한 끌기를 판이 삼켜 화면이 굴러가지 않는다(함정 44).",
            scroll > board,
        )
    }

    /**
     * ⚠️ **뒤로가기가 세 겹이 됐다** — 단원 → 목록 → 허브 → 홈. 셸의 `BackHandler`는 목적지가
     * Home이 아니면 무조건 `exitToHome()`을 부르므로, 새 겹이 자기 핸들러를 갖지 않으면
     * **두 겹을 건너뛰고 홈으로 튄다**(백로그 #163이 같은 값을 치렀다).
     */
    @Test
    fun bothRulesLayersCatchSystemBackThemselves() {
        assertEquals(
            "`StudyLessonScreen.kt`의 `BackHandler`가 두 개가 아니다 — 단원과 목록이 각각 " +
                "자기 뒤로가기를 잡아야 한다(하나면 한 겹이 홈으로 튄다).",
            2,
            Regex("""BackHandler\s*\{""").findAll(screen).count(),
        )
    }

    /**
     * ⚠️ **글꼴 배율이 1.3까지 올라간다**(`AppFontScales` — 이 앱은 시스템 배율을 따르지
     * 않는다). 판과 설명의 높이를 dp로 못박으면 큰 글씨에서 설명이 잘리거나 판이 밀려난다 —
     * 남은 높이를 **비율로** 나눠야 한다(함정 9).
     */
    @Test
    fun theBoardAndBodyShareHeightByWeightNotFixedDp() {
        assertTrue(
            "판이 `weight`로 높이를 받지 않는다 — 고정 dp면 큰 글꼴에서 설명이 잘린다(함정 9).",
            screen.contains("weight(BoardShare)"),
        )
        assertTrue(
            "설명 상자가 `weight`로 높이를 받지 않는다 — 같은 이유다(함정 9).",
            screen.contains("weight(BodyShare)"),
        )
    }

    /**
     * ⚠️ **갈래가 늘 때 화면을 복사하는 것이 가장 흔한 실수다.** 규칙과 행마는 형식이 같아
     * (정적 문구 + 도해 넘김) 한 화면이 갈래만 바꿔 그린다 — 두 벌이 되면 한쪽만 고치는 날이
     * 반드시 온다(#164의 레이아웃 비율이 실기로 정해진 값이라 더 그렇다).
     */
    @Test
    fun oneScreenServesEveryTrack() {
        assertEquals(
            "허브가 갈래마다 다른 화면을 부른다 — 학습 단원 화면은 하나여야 한다.",
            1,
            Regex("""StudyLessonTrackScreen\(""").findAll(hub).count(),
        )
        assertTrue(
            "허브가 분류를 갈래로 직접 옮긴다 — 그 자리는 `studyLessonTrackFor` 하나여야 한다.",
            hub.contains("studyLessonTrackFor("),
        )
    }

    /**
     * ⚠️ **열린 분류에는 갈래가 있어야 한다.** `available = true`로 돌려 놓고 갈래를 잇는 것을
     * 잊으면 행이 눌리는데 **아무 일도 일어나지 않는다** — 고장으로 읽히고, 컴파일은 통과한다.
     */
    @Test
    fun everyOpenLessonCategoryHasATrackWithLessons() {
        StudyCategory.entries
            .filter { it.available && it != StudyCategory.YoutubeLessons }
            .forEach { category ->
                val track = studyLessonTrackFor(category)
                assertTrue("$category 가 열려 있는데 갈래가 없다 — 눌러도 아무 일도 안 일어난다.", track != null)
                assertTrue(
                    "$category 갈래에 단원이 하나도 없다 — 빈 목록이 열린다.",
                    studyLessons.any { it.track == track },
                )
            }
    }

    /**
     * ⚠️ **표를 `+`로 합치면 같은 키가 말없이 덮인다.** 갈래 둘이 우연히 같은 열쇠를 쓰면
     * 한쪽 문구가 통째로 사라지는데 화면에는 *다른 갈래의 멀쩡한 문장*이 뜬다 — 빈칸보다
     * 알아채기 어렵다.
     */
    @Test
    fun mergingTheTrackTablesLosesNothing() {
        val (titles, summaries, bodies) = studyLessonCopyKeyCounts()
        assertEquals("단원 이름 표를 합치며 줄이 덮였다.", titles, studyLessons.size)
        assertEquals("한 줄 소개 표를 합치며 줄이 덮였다.", summaries, studyLessons.size)
        assertEquals(
            "단계 본문 표를 합치며 줄이 덮였다 — 갈래끼리 열쇠가 겹친다.",
            bodies,
            studyLessonBodyKeys().size,
        )
    }

    /** 갈래마다 단원이 몇 개인지 — 콘텐츠를 실수로 지웠을 때 조용히 줄어드는 것을 막는다. */
    @Test
    fun eachTrackKeepsItsLessonCount() {
        assertEquals("규칙 갈래의 단원 수가 여섯이 아니다.", 6, studyLessons.count { it.track == StudyLessonTrack.Rules })
        assertEquals("행마 갈래의 단원 수가 다섯이 아니다.", 5, studyLessons.count { it.track == StudyLessonTrack.Shapes })
    }
}
