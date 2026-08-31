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
            assertTrue("$language 돋보기 라벨이 비었다", playMagnifierLabelFor(language).isNotBlank())
            assertTrue("$language 대상 이름이 비었다", boardSizeSubjectFor(language).isNotBlank())
            listOf(true, false).forEach { on ->
                assertTrue(
                    "$language / isMaxSize=$on 라벨이 비었다",
                    boardSizeToggleLabelFor(language, on).isNotBlank(),
                )
                assertTrue(
                    "$language / enabled=$on 상태 문구가 비었다",
                    playMagnifierStateFor(language, on).isNotBlank(),
                )
            }
        }
    }

    /**
     * ⚠️ **돋보기 라벨에는 상태가 없다** — 켜짐/꺼짐을 테두리 색만으로 말하기로 했다(2026-08-31
     * 사용자 지시). 다음 사람이 "상태를 글자로도 보여주자"며 되살리면 **글자와 테두리가 같은 말을
     * 두 번** 하게 되고, 그게 바로 이번에 걷어낸 상태다.
     */
    @Test
    fun theMagnifierLabelCarriesNoStateWord() {
        val banned = listOf("켜", "끄", "on", "off", "オン", "オフ", "开", "关")
        languages.forEach { language ->
            val label = playMagnifierLabelFor(language)
            banned.forEach { word ->
                assertFalse(
                    "$language 돋보기 라벨에 상태 낱말이 남았다('$word'): $label",
                    label.contains(word, ignoreCase = true),
                )
            }
        }
        assertEquals("착수 돋보기", playMagnifierLabelFor(UiLanguage.Korean))
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
     * ⚠️ **화면에 상태를 말하는 글자가 없으므로 이것이 유일한 경로다.** 지우면 시각장애 사용자에게
     * 돋보기 버튼은 **상태를 알 수 없는 버튼**이 된다.
     */
    @Test
    fun theSpokenStateDistinguishesOnFromOffInEveryLanguage() {
        languages.forEach { language ->
            assertNotEquals(
                "$language: 켜짐/꺼짐 낭독 문구가 같다",
                playMagnifierStateFor(language, true),
                playMagnifierStateFor(language, false),
            )
        }
        assertEquals("켜짐", playMagnifierStateFor(UiLanguage.Korean, enabled = true))
        assertEquals("꺼짐", playMagnifierStateFor(UiLanguage.Korean, enabled = false))
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
            playMagnifierLabelFor(UiLanguage.Korean),
            playMagnifierStateFor(UiLanguage.Korean, true),
            boardSizeSubjectFor(UiLanguage.Korean),
            boardSizeToggleLabelFor(UiLanguage.Korean, true),
            boardSizeToggleLabelFor(UiLanguage.Korean, false),
        ).forEach { text -> assertTrue("한국어가 아닌 문구: $text", text.containsHangul()) }
    }
}
