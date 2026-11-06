package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshInviteFor
import com.worksoc.goaicoach.ui.l10n.fortuneRefreshInviteTwoLinesFor
import org.junit.Assert.assertEquals
import org.junit.Test

/** 운 바꾸기 창(#303)의 안내는 첫 쉼표(영어는 줄표) 뒤에서 두 줄이 된다(사용자 2026-11-06) — 실험실 카드의 문장을 나눈 것이다. */
class FortuneRefreshInviteTest {

    @Test
    fun everyLanguageBreaksOnceAfterTheFirstPause() {
        UiLanguage.entries.forEach { language ->
            val twoLines = fortuneRefreshInviteTwoLinesFor(language)
            assertEquals("$language: 나눌 자리를 못 찾았다 — 문장을 고쳤다면 나누는 자리도 본다", 1, twoLines.count { it == '\n' })
            // 줄바꿈만 더했을 뿐 글자는 실험실 카드의 문장 그대로다(나눈 자리의 공백만 빠진다).
            assertEquals(language.toString(), fortuneRefreshInviteFor(language).filterNot(Char::isWhitespace), twoLines.filterNot(Char::isWhitespace))
        }
        assertEquals("한 수로 판이 바뀌듯,\n오늘의 운도 내 손으로 바꿀 수 있어요.", fortuneRefreshInviteTwoLinesFor(UiLanguage.Korean))
    }
}
