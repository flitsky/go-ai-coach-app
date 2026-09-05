package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.preferences.MagnifierSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 돋보기 설정 칩 문구의 **손 그물**(백로그 #85).
 *
 * ⚠️ 값 표기는 배율에 따라 갈려야 해서 `fun`이고, 따라서 `UiStringsTest`의 리플렉션 그물에
 * **아예 안 잡힌다**(함정 10번). 번역 누락도 조용히 통과하므로 여기서 직접 본다.
 */
class UiStringsMagnifierTest {

    private val languages = UiLanguage.entries

    /** 선택지마다 표기가 달라야 한다 — 같으면 어느 것이 선택됐는지 화면으로 알 수 없다. */
    @Test
    fun everyOptionReadsDifferentlyInEveryLanguage() {
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            val sizes = MagnifierSettings.sizeScales.map(strings::magnifierSizeOptionLabel)
            val zooms = MagnifierSettings.zoomScales.map(strings::magnifierZoomOptionLabel)
            assertEquals("$language: 창 크기 표기가 겹친다", MagnifierSettings.sizeScales.size, sizes.toSet().size)
            assertEquals("$language: 배율 표기가 겹친다", MagnifierSettings.zoomScales.size, zooms.toSet().size)
            (sizes + zooms).forEach { label ->
                assertTrue("$language: 빈 표기가 있다", label.isNotBlank())
            }
        }
    }

    /**
     * ⚠️ **`1.0`은 숫자가 아니라 말로 적어야 한다.** `×1.0`·`100%`는 *"아무 일도 안 하는 설정"*
     * 으로 읽히는데, 실제로는 **손가락 가림을 해소하는** 유효한 선택지다(#39가 이 기능을 만든 이유).
     */
    @Test
    fun theUnmagnifiedOptionIsNamedNotNumbered() {
        languages.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            val zoomLabel = strings.magnifierZoomOptionLabel(1.0f)
            assertTrue(
                "$language: 배율 1.0 표기가 '$zoomLabel' — 숫자로만 적으면 '아무 효과 없음'으로 읽힌다(#85).",
                !zoomLabel.contains("100") && !zoomLabel.contains("1.0"),
            )
            val sizeLabel = strings.magnifierSizeOptionLabel(1.0f)
            assertTrue(
                "$language: 창 크기 1.0 표기가 '$sizeLabel' — 기준값임이 드러나야 한다(#85).",
                !sizeLabel.contains("1.0"),
            )
        }
    }

    /** 번역이 실제로 갈렸는가 — 한국어 문구가 그대로 복사돼 있으면 안 된다. */
    @Test
    fun nonKoreanCopyDoesNotInheritTheKoreanBaseline() {
        val korean = UiStrings.forLanguage(UiLanguage.Korean)
        languages.filter { it != UiLanguage.Korean }.forEach { language ->
            val other = UiStrings.forLanguage(language)
            assertNotEquals("$language 창 크기 라벨이 한국어와 같다", korean.magnifierWindowSizeLabel, other.magnifierWindowSizeLabel)
            assertNotEquals("$language 배율 라벨이 한국어와 같다", korean.magnifierZoomLabel, other.magnifierZoomLabel)
            assertNotEquals(
                "$language '판 그대로' 표기가 한국어와 같다",
                korean.magnifierZoomOptionLabel(1.0f),
                other.magnifierZoomOptionLabel(1.0f),
            )
        }
    }
}
