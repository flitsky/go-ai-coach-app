package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.shared.content.lifeAndDeathProblems
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathCopyLanguages
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathExplanationLanguages
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「기본 사활」(백로그 #184)의 문구와 화면 배선 — 판 위의 사실(문제가 성립하는가)은 `shared`의 `LifeAndDeathProblemsTest`가 본다.
 */
class LifeAndDeathCopyContractTest {

    /** 주석·import를 걷어낸 본문만 본다(함정 10-2 — 이름이 주석에만 남아 그물이 헐거워지는 것을 막는다). */
    private fun source(fileName: String): String =
        File(RepoPaths.uiFile(fileName).path).readContractSource()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    /** 표가 비면 폴백이 돌아 화면에 열쇠(`ld.eyes.1` · `TwoEyes`)가 그대로 뜬다 — 한국어로 보는 사람에게는 끝까지 안 보인다. */
    @Test
    fun everyLabelHasCopyInEveryLanguage() {
        lifeAndDeathCopyLanguages().forEach { (key, languages) ->
            assertEquals("「기본 사활」 문구 `$key`의 언어가 빠졌다", UiLanguage.entries.toSet(), languages)
        }
    }

    @Test
    fun everyProblemHasAnExplanationAndNoExplanationIsOrphaned() {
        val explanations = lifeAndDeathExplanationLanguages()
        assertEquals(
            "해설 표의 열쇠가 문제 목록과 다르다 — 문제를 더했는데 해설이 없거나, 지웠는데 해설이 남았다",
            lifeAndDeathProblems.map { it.id }.toSet(),
            explanations.keys,
        )
        explanations.forEach { (id, languages) ->
            assertEquals("`$id` 해설의 언어가 빠졌다", UiLanguage.entries.toSet(), languages)
        }
    }

    /** 안내문이 「흑 차례」를 박아 두었다 — 백이 먼저 두는 문제를 더하면 안내문부터 색으로 갈라야 한다. */
    @Test
    fun everyProblemIsBlackToPlayAsThePromptSays() {
        lifeAndDeathProblems.forEach { problem ->
            assertEquals("${problem.id}: the prompt says Black to play", StoneColor.Black, problem.toPlay)
        }
    }

    @Test
    fun theShellDoesNotKnowAboutLifeAndDeath() {
        val shell = source("GoCoachApp.kt")
        listOf("LifeAndDeathTrackScreen", "LifeAndDeathProgressStore", "lifeAndDeathProblems").forEach { name ->
            assertFalse("셸이 `$name`을 안다 — 단원과 문제는 학습 허브 안의 상태다(함정 3)", shell.contains(name))
        }
        assertTrue("허브가 「기본 사활」 화면을 잇지 않는다", source("StudyScreen.kt").contains("LifeAndDeathTrackScreen("))
    }

    /**
     * 뒤로가기가 네 겹이다 — 문제 → 단원 목록 → 허브 → 홈. 겹마다 자기 `BackHandler`가 없으면 시스템 뒤로가기가 셸로 가서
     * 홈까지 튄다(학습 허브와 같은 함정, 백로그 #163).
     */
    @Test
    fun bothLevelsCatchSystemBack() {
        val screen = source("LifeAndDeathScreen.kt")
        assertEquals("단원 목록과 문제 화면이 각자 `BackHandler`를 가져야 한다", 2, Regex("""BackHandler\s*\{""").findAll(screen).count())
    }

    /** 틀린 수의 붉은 표시(사용자 2026-10-02, U-51)는 판의 인자로만 그려진다 — 배선이 빠지면 표시가 조용히 사라진다. */
    @Test
    fun theWrongMoveIsMarkedOnTheBoard() {
        val screen = source("LifeAndDeathScreen.kt")
        assertTrue("문제 화면이 틀린 수를 판에 넘기지 않는다", screen.contains("mistakePoints = ") && screen.contains("state.mistake"))
        assertTrue("판이 틀린 수를 그리지 않는다", source("GoBoard.kt").contains("mistakePoints.forEach"))
    }
}
