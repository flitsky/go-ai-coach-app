package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.aiResignationOfferAcceptFor
import com.worksoc.goaicoach.ui.l10n.aiResignationOfferBodyFor
import com.worksoc.goaicoach.ui.l10n.aiResignationOfferDeclineFor
import com.worksoc.goaicoach.ui.l10n.aiResignationOfferTitleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AI의 기권 제안 팝업(backlog #213, 사용자 2026-10-07)의 문구 — 받아들이기 / 계속 두기. */
class UiStringsResignationOfferTest {
    private fun everyStringIn(language: UiLanguage): List<String> =
        listOf(
            aiResignationOfferTitleFor(language),
            aiResignationOfferBodyFor(language),
            aiResignationOfferAcceptFor(language),
            aiResignationOfferDeclineFor(language),
        )

    @Test
    fun everyOfferStringExistsInEveryLanguageWithoutFallingBackToKorean() {
        assertTrue("자기검증 — 한국어 문구에는 한글이 있어야 한다", everyStringIn(UiLanguage.Korean).all { it.containsHangul() })
        UiLanguage.entries.forEach { language ->
            everyStringIn(language).forEach { text ->
                assertTrue("$language 문구가 비었다", text.isNotBlank())
                if (language != UiLanguage.Korean) assertFalse("$language: 한글이 남았다 — $text", text.containsHangul())
            }
        }
    }

    /** 두 버튼은 서로 다른 일을 한다 — 같은 글자가 되면 사용자가 고를 수 없다. */
    @Test
    fun theTwoChoicesAreSpelledDifferentlyInEveryLanguage() {
        UiLanguage.entries.forEach { language ->
            assertFalse("$language", aiResignationOfferAcceptFor(language) == aiResignationOfferDeclineFor(language))
        }
    }

    /** 팝업은 **한 번만 묻는다**는 것까지 말한다 — 거절한 사람이 수마다 팝업을 기다리지 않게. */
    @Test
    fun theBodySaysTheOfferIsMadeOnlyOnce() {
        assertEquals("상대가 기권을 제안합니다", aiResignationOfferTitleFor(UiLanguage.Korean))
        assertEquals("기권 받기" to "계속 두기", aiResignationOfferAcceptFor(UiLanguage.Korean) to aiResignationOfferDeclineFor(UiLanguage.Korean))
        assertTrue(aiResignationOfferBodyFor(UiLanguage.Korean).contains("이 대국에서는 다시 묻지 않습니다"))
        assertTrue(aiResignationOfferBodyFor(UiLanguage.English).contains("will not be asked again"))
    }
}
