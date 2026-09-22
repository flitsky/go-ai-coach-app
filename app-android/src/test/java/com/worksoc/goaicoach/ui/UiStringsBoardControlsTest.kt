package com.worksoc.goaicoach.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백로그 #39 — 보드 위 두 토글의 문구. */
class UiStringsBoardControlsTest {

    private val languages = UiLanguage.entries

    @Test
    fun everyToggleStringExistsInEveryLanguage() {
        languages.forEach { language ->
            assertTrue("$language 대상 이름이 비었다", boardSizeSubjectFor(language).isNotBlank())
            listOf(true, false).forEach { on ->
                assertTrue(
                    "$language / isMaxSize=$on 라벨이 비었다",
                    boardSizeToggleLabelFor(language, on).isNotBlank(),
                )
            }
        }
    }

    /** 바둑판 쪽은 켜짐/꺼짐이 아니라 **이름이 다른 두 모드**라, 라벨이 계속 바뀌어야 한다. */
    @Test
    fun theBoardSizeLabelStillNamesTheCurrentMode() {
        assertEquals("바둑판 최대", boardSizeToggleLabelFor(UiLanguage.Korean, isMaxSize = true))
        assertEquals("바둑판 여백", boardSizeToggleLabelFor(UiLanguage.Korean, isMaxSize = false))
        languages.forEach { language ->
            assertNotEquals(
                "$language: 최대/여백 라벨이 같다",
                boardSizeToggleLabelFor(language, true),
                boardSizeToggleLabelFor(language, false),
            )
        }
    }

    /**
     * ⚠️ **`최대`/`여백`만으로는 무엇의 최대인지 안 읽힌다**(2026-08-31 사용자 지적). 네 언어 모두
     * 대상(바둑판)을 함께 말하는지 본다 — 짧게 줄이려는 다음 사람을 여기서 막는다.
     */
    @Test
    fun theBoardSizeLabelAlwaysNamesTheBoard() {
        val boardWords = mapOf(
            UiLanguage.Korean to "바둑판",
            UiLanguage.English to "Board",
            UiLanguage.Japanese to "碁盤",
            UiLanguage.ChineseSimplified to "棋盘",
        )
        languages.forEach { language ->
            listOf(true, false).forEach { isMax ->
                val label = boardSizeToggleLabelFor(language, isMax)
                assertTrue(
                    "$language / isMaxSize=$isMax 라벨이 대상을 안 말한다: $label",
                    label.contains(boardWords.getValue(language)),
                )
            }
        }
    }

    /**
     * 스크린 리더는 `⇅` 글리프를 읽어 주지 않으므로, **소리로는 "무엇의" 토글인지 알 수 없다** —
     * 접근성 문구가 대상 이름을 먼저 말하는지 본다.
     */
    @Test
    fun theSpokenSubjectNamesWhatIsBeingToggled() {
        assertEquals("바둑판 크기", boardSizeSubjectFor(UiLanguage.Korean))
    }

    @Test
    fun theKoreanTableIsActuallyKorean() {
        listOf(
            boardSizeSubjectFor(UiLanguage.Korean),
            boardSizeToggleLabelFor(UiLanguage.Korean, true),
            boardSizeToggleLabelFor(UiLanguage.Korean, false),
        ).forEach { text -> assertTrue("한국어가 아닌 문구: $text", text.containsHangul()) }
    }
}
