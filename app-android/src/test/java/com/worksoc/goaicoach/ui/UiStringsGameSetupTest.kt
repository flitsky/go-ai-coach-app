package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 대국 설정 화면의 계가 칸(backlog #225, 사용자 피드백 2026-10-08: 「집계가 → 계가 (계가: 집 계산)」). */
class UiStringsGameSetupTest {
    /** 옆의 칸들(「바둑판 (9x9)」·「덤 (6.5집)」)처럼 **이름 (값)** 이다 — 예전에는 값 「집계가」만 있어 무엇을 정하는 칸인지 이름이 없었다. */
    @Test
    fun theScoringCellIsNamedAndShowsItsValueInParentheses() {
        val korean = UiStrings.forLanguage(UiLanguage.Korean)

        assertEquals("계가 (집 계산)", korean.compactRulesetLabel(Ruleset.Japanese))
        assertEquals("계가 (면적 계산)", korean.compactRulesetLabel(Ruleset.Chinese))
        assertEquals("Scoring (Territory)", UiStrings.forLanguage(UiLanguage.English).compactRulesetLabel(Ruleset.Japanese))
    }

    /** 펼친 목록의 선택지는 값만 말한다 — 칸의 이름이 「계가」라 「집 계산」 / 「면적 계산」으로 충분하고, 둘은 서로 달라야 한다. */
    @Test
    fun theOptionsAreTheBareValuesAndDifferInEveryLanguage() {
        UiLanguage.entries.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            val territory = strings.compactRulesetValueLabel(Ruleset.Japanese)
            val area = strings.compactRulesetValueLabel(Ruleset.Chinese)

            assertTrue("$language", territory.isNotBlank() && area.isNotBlank() && strings.compactRulesetTitle().isNotBlank())
            assertFalse("$language: 두 선택지가 같은 글자다", territory == area)
            assertEquals("${strings.compactRulesetTitle()} ($territory)", strings.compactRulesetLabel(Ruleset.Japanese))
            if (language != UiLanguage.Korean) {
                listOf(territory, area, strings.compactRulesetTitle()).forEach { assertFalse("$language: 한글이 남았다 — $it", it.containsHangul()) }
            }
        }
    }
}
