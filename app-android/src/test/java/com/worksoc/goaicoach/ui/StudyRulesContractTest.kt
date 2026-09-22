package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.goRuleLessons
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「바둑 규칙 배우기」(백로그 #164)가 지켜야 할 것. 판 위의 사실은 `shared`의
 * `GoRuleLessonsTest`가 보고, 여기는 **문구와 배치**를 본다.
 */
class StudyRulesContractTest {

    /** 주석·import를 걷어낸 본문만 본다 — 이름이 주석에 남아 그물이 헐거워지는 것을 막는다(함정 10-2). */
    private fun source(path: String): String =
        File(path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    private val rules = source("src/main/java/com/worksoc/goaicoach/ui/StudyRulesScreen.kt")

    /**
     * ⚠️ **빈칸은 조용하다.** 표에 한 언어가 빠지면 폴백이 돌아 화면에 `ko.retake` 같은 키가
     * 그대로 뜬다 — 한국어로 보는 사람에게는 끝까지 보이지 않는다.
     *
     * ⚠️ 값을 폴백과 견주지 않고 **표의 열쇠를 직접 센다**(`goRuleLessonTitleLanguages`) —
     * 영어 단원 이름 `Liberties`가 enum 이름과 같은 낱말이라, 값 비교로는 멀쩡한 줄이
     * 빠진 줄로 읽힌다.
     */
    @Test
    fun everyLessonAndStepHasCopyInEveryLanguage() {
        val all = UiLanguage.entries.toSet()
        goRuleLessons.forEach { lesson ->
            assertEquals(
                "${lesson.id} 의 단원 이름이 네 언어를 다 채우지 못했다.",
                all,
                goRuleLessonTitleLanguages(lesson.id),
            )
            assertEquals(
                "${lesson.id} 의 한 줄 소개가 네 언어를 다 채우지 못했다.",
                all,
                goRuleLessonSummaryLanguages(lesson.id),
            )
            lesson.steps.forEach { step ->
                assertEquals(
                    "${step.id} 의 본문이 네 언어를 다 채우지 못했다.",
                    all,
                    goRuleStepBodyLanguages(step.id),
                )
            }
        }
    }

    /** 단계를 지웠는데 본문만 남으면 아무도 안 읽는 문구를 네 언어로 유지하게 된다. */
    @Test
    fun noOrphanStepCopyRemains() {
        val used = goRuleLessons.flatMap { lesson -> lesson.steps.map { it.id } }.toSet()
        assertEquals(
            "어느 단계도 쓰지 않는 본문이 표에 남아 있다.",
            emptySet<String>(),
            goRuleStepBodyKeys() - used,
        )
    }

    /** 조작부 문구 넷도 네 언어를 다 채워야 한다 — 같은 값이 겹치면 한 언어가 빠진 것이다. */
    @Test
    fun everyControlLabelHasCopyInEveryLanguage() {
        listOf(
            "이전" to UiLanguage.entries.map { studyRulesPreviousFor(it) },
            "다음" to UiLanguage.entries.map { studyRulesNextFor(it) },
            "다음 단원" to UiLanguage.entries.map { studyRulesNextLessonFor(it) },
            "단원 목록" to UiLanguage.entries.map { studyRulesBackToListFor(it) },
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
            goRuleLessons.flatMap { it.steps }.forEach { step ->
                val body = goRuleStepBodyFor(language, step.id)
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
        val lessonScreen = rules.substringAfter("private fun StudyRuleLessonScreen(")
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
            "`StudyRulesScreen.kt`의 `BackHandler`가 두 개가 아니다 — 단원과 목록이 각각 " +
                "자기 뒤로가기를 잡아야 한다(하나면 한 겹이 홈으로 튄다).",
            2,
            Regex("""BackHandler\s*\{""").findAll(rules).count(),
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
            rules.contains("weight(BoardShare)"),
        )
        assertTrue(
            "설명 상자가 `weight`로 높이를 받지 않는다 — 같은 이유다(함정 9).",
            rules.contains("weight(BodyShare)"),
        )
    }
}
