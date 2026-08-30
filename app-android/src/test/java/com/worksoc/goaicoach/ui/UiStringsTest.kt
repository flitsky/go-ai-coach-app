package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.SearchTimeLimit
import org.junit.Assert.assertEquals
import org.junit.Test

class UiStringsTest {
    /**
     * 이 값들은 **3분할 버튼에 들어가야 해서** 짧다(#29). 360dp 화면에서 그 버튼의 글자
     * 가용폭은 96dp뿐인데, 예전 일본어 `新しい対局を開始`는 전각 8자 = 92dp라 배율 1.0배에서도
     * 말줄임됐다. 길이를 되돌리려면 폭 계산부터 다시 해야 한다.
     *
     * 같은 뜻의 긴 문구는 [UiStrings.overwriteWarningTitle]이 그대로 갖고 있다 — 그쪽은
     * 다이얼로그 제목이라 폭이 넉넉하다. 아래 테스트가 그 분기를 함께 고정한다.
     *
     * **영어만 그대로 둔 이유**: 폭으로는 `Rematch`(48dp)가 `New Game`(58dp)보다 유리하지만,
     * 이 버튼이 쏘는 `StartConfiguredGame`은 **그 시점의 현재 설정**으로 시작한다. 대국이
     * 끝난 뒤 헤더에서 상대나 판 크기를 바꿔 놓고 눌러도 되므로 "같은 상대와 다시"를 뜻하는
     * `Rematch`는 거짓이 될 수 있다. 나머지 세 언어가 전부 "새 대국"인데 영어만 뜻이 갈리는
     * 것도 나쁘다. 대신 영어는 배율 1.8배 이상에서 `New Ga…`로 말줄임되는데(#27의 Ellipsis),
     * 일본어가 **기본 배율에서** 잘리던 것에 비하면 훨씬 가벼운 손해다.
     */
    @Test
    fun startGameActionUsesShortLocalizedCopyInEverySupportedLanguage() {
        assertEquals("새 대국", UiStrings.forLanguage(UiLanguage.Korean).newGameAction)
        assertEquals("New Game", UiStrings.forLanguage(UiLanguage.English).newGameAction)
        assertEquals("新規対局", UiStrings.forLanguage(UiLanguage.Japanese).newGameAction)
        assertEquals("新对局", UiStrings.forLanguage(UiLanguage.ChineseSimplified).newGameAction)
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

    @Test
    fun overwriteWarningStringsUseLocalizedCopyInEverySupportedLanguage() {
        assertEquals("새 대국 시작", UiStrings.forLanguage(UiLanguage.Korean).overwriteWarningTitle)
        assertEquals("Start New Match", UiStrings.forLanguage(UiLanguage.English).overwriteWarningTitle)
        assertEquals("新しい対局を開始", UiStrings.forLanguage(UiLanguage.Japanese).overwriteWarningTitle)
        assertEquals("开始新对局", UiStrings.forLanguage(UiLanguage.ChineseSimplified).overwriteWarningTitle)
    }
}
