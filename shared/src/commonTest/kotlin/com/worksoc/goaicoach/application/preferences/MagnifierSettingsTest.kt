package com.worksoc.goaicoach.application.preferences

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [MagnifierSettings]의 계약(백로그 #85).
 *
 * ⚠️ **이 그물의 핵심은 "기본값이 실제로 더 넓게 보이는가"** 다. 사용자 지시가 *"영역을 1.2배로
 * 키우고 확대 비율은 조금 낮춰라"* 였는데, 둘을 따로 만지면 **서로 상쇄해 아무 변화가 없을 수
 * 있다**(보이는 칸 수 = 창 ÷ 배율). 그래서 값 자체가 아니라 **비(比)** 를 단언한다.
 */
class MagnifierSettingsTest {

    private val previousSize = 1.0f
    private val previousZoom = 2.0f

    /** 이전 동작 대비 보이는 칸 수가 실제로 늘어야 한다 — 이것이 이 항목의 목적 전부다. */
    @Test
    fun theDefaultShowsMoreBoardThanBefore() {
        val before = previousSize / previousZoom
        val after = MagnifierSettings.defaultSizeScale / MagnifierSettings.defaultZoom
        assertTrue(
            after > before,
            "기본값이 이전보다 넓게 보이지 않는다(이전 $before, 지금 $after) — 창과 배율을 " +
                "따로 만지면 서로 상쇄될 수 있다(#85).",
        )
        assertEquals(1.6f, after / before, 0.001f, "지시받은 1.6배가 아니다")
    }

    /** 기본값은 목록 안에 있어야 한다 — 밖에 있으면 순환/선택 UI가 그 값을 표시할 수 없다. */
    @Test
    fun theDefaultsAreSelectableValues() {
        assertTrue(MagnifierSettings.defaultSizeScale in MagnifierSettings.sizeScales)
        assertTrue(MagnifierSettings.defaultZoom in MagnifierSettings.zoomScales)
    }

    /**
     * ⚠️ **배율 1.0이 목록에 있어야 한다** — 사용자가 요청한 *"기본 대국보드판 사이즈 그대로"* 다.
     * 확대가 없어도 손가락 가림이 해소되므로 돋보기로서 뜻이 있다.
     */
    @Test
    fun zoomOffersTheUnmagnifiedOption() {
        assertTrue(1.0f in MagnifierSettings.zoomScales, "'판 크기 그대로'(1.0) 선택지가 없다(#85).")
    }

    /** 저장이 손상되거나 목록에서 값이 빠졌을 때 기본값으로 접는다(#83이 밟은 경로와 같다). */
    @Test
    fun unknownStoredValuesFoldToTheDefault() {
        assertEquals(MagnifierSettings.defaultSizeScale, MagnifierSettings.sanitizeSizeScale(0f))
        assertEquals(MagnifierSettings.defaultSizeScale, MagnifierSettings.sanitizeSizeScale(-3f))
        assertEquals(MagnifierSettings.defaultSizeScale, MagnifierSettings.sanitizeSizeScale(9.9f))
        assertEquals(MagnifierSettings.defaultZoom, MagnifierSettings.sanitizeZoom(2.0f))
        assertEquals(MagnifierSettings.defaultZoom, MagnifierSettings.sanitizeZoom(0f))
    }

    /** 목록 안의 값은 그대로 통과한다 — sanitize가 선택 자체를 무력화하면 안 된다. */
    @Test
    fun listedValuesSurviveSanitising() {
        MagnifierSettings.sizeScales.forEach { assertEquals(it, MagnifierSettings.sanitizeSizeScale(it)) }
        MagnifierSettings.zoomScales.forEach { assertEquals(it, MagnifierSettings.sanitizeZoom(it)) }
    }
}
