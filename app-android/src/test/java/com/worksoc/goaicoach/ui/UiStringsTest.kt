package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.SearchTimeLimit
import org.junit.Assert.assertEquals
import org.junit.Test

class UiStringsTest {
    @Test
    fun startGameActionUsesLocalizedCopyInEverySupportedLanguage() {
        assertEquals("대국 시작", UiStrings.forLanguage(UiLanguage.Korean).newGameAction)
        assertEquals("Start Game", UiStrings.forLanguage(UiLanguage.English).newGameAction)
        assertEquals("対局開始", UiStrings.forLanguage(UiLanguage.Japanese).newGameAction)
        assertEquals("开始对局", UiStrings.forLanguage(UiLanguage.ChineseSimplified).newGameAction)
    }

    @Test
    fun maximumSearchTimeOptionsAreLocalizedInEverySupportedLanguage() {
        UiLanguage.entries.forEach { language ->
            val strings = UiStrings.forLanguage(language)

            assertEquals(5, SearchTimeLimit.entries.map(strings::searchTimeLimitLabel).distinct().size)
        }
        assertEquals("최대 탐색 시간 제한", UiStringsKorean.maximumSearchTimeLimit)
        assertEquals("사용 안 함", UiStringsKorean.searchTimeLimitLabel(SearchTimeLimit.Off))
        assertEquals("10초 이내", UiStringsKorean.searchTimeLimitLabel(SearchTimeLimit.WithinTenSeconds))
    }

    @Test
    fun compactDisplayLabelsUseShortLocalizedCopy() {
        assertEquals("착수 표시", UiStringsKorean.lastMoveRing)
        assertEquals("Move mark", UiStringsEnglish.lastMoveRing)
        assertEquals("着手表示", UiStringsJapanese.lastMoveRing)
        assertEquals("落子标记", UiStringsChineseSimplified.lastMoveRing)
    }
}
