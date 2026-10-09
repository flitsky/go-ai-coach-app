package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.gamehistory.WinRatePeriod
import com.worksoc.goaicoach.application.gamehistory.WinRateShortGameMaxMoveCount
import com.worksoc.goaicoach.application.gamehistory.WinRateTally
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.winRateNoGamesFor
import com.worksoc.goaicoach.ui.l10n.winRatePeriodLabelFor
import com.worksoc.goaicoach.ui.l10n.winRateSummaryFor
import com.worksoc.goaicoach.ui.l10n.winRateWhatCountsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 대국 기록 화면의 승률 문구(backlog #227). */
class UiStringsWinRateTest {
    private fun everyStringIn(language: UiLanguage): List<String> =
        WinRatePeriod.entries.map { winRatePeriodLabelFor(language, it) } + listOfNotNull(
            winRateSummaryFor(language, WinRateTally(wins = 6, losses = 3, draws = 1)),
            winRateNoGamesFor(language),
            winRateWhatCountsFor(language, WinRateShortGameMaxMoveCount),
        )

    @Test
    fun everyWinRateStringExistsInEveryLanguageWithoutFallingBackToKorean() {
        assertTrue("자기검증 — 한국어 문구에는 한글이 있어야 한다", everyStringIn(UiLanguage.Korean).all { it.containsHangul() })
        UiLanguage.entries.forEach { language ->
            val strings = everyStringIn(language)
            assertEquals("$language: 문구 수가 다르다", 6, strings.size)
            strings.forEach { text ->
                assertTrue("$language 문구가 비었다", text.isNotBlank())
                if (language != UiLanguage.Korean) assertFalse("$language: 한글이 남았다 — $text", text.containsHangul())
            }
        }
    }

    /** 사용자가 든 세 기간 그대로다(2026-10-08): 최근 10게임 · 누적 · 최근 한 달. */
    @Test
    fun thePeriodsAreTheThreeTheUserNamed() {
        assertEquals(listOf("최근 10판", "최근 한 달", "누적"), WinRatePeriod.entries.map { winRatePeriodLabelFor(UiLanguage.Korean, it) })
    }

    /** 전적 한 줄 — 무승부는 있을 때만 붙고, 센 판이 없으면 줄이 없다(0%라고 말하지 않는다). */
    @Test
    fun theSummaryShowsTheRateAndTheRecord() {
        assertEquals("승률 60% · 6승 4패", winRateSummaryFor(UiLanguage.Korean, WinRateTally(wins = 6, losses = 4)))
        assertEquals("승률 60% · 6승 3패 1무", winRateSummaryFor(UiLanguage.Korean, WinRateTally(wins = 6, losses = 3, draws = 1)))
        assertEquals("60% wins · 6W 4L", winRateSummaryFor(UiLanguage.English, WinRateTally(wins = 6, losses = 4)))
        UiLanguage.entries.forEach { language -> assertNull(winRateSummaryFor(language, WinRateTally())) }
    }

    /** 무엇을 세는지 밝히는 글은 규칙과 같은 숫자를 말한다 — 10수 이하 제외(#208과 같은 잣대). */
    @Test
    fun theCaptionStatesTheSameCutoffAsTheRule() {
        assertEquals(10, WinRateShortGameMaxMoveCount)
        assertEquals("사람 대 AI 대국만 · 10수 이하 제외", winRateWhatCountsFor(UiLanguage.Korean, WinRateShortGameMaxMoveCount))
    }
}
