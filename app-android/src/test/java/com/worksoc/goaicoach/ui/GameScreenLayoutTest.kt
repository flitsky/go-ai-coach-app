package com.worksoc.goaicoach.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 백로그 #141 — 대국 화면 배치 갈래. 값은 상태바·내비게이션 바를 뺀 **뷰포트** dp다.
 *
 * ⚠️ 이 표가 사용자 결정 그 자체다(2026-09-12): *"보통 폰·세로로 긴 태블릿은 그대로, 폴드 펼침과
 * 가로 태블릿만 넓은 배치."* 기준 상수를 바꾸면 여기서 어느 기기가 옮겨 가는지 먼저 보인다.
 */
class GameScreenLayoutTest {

    private fun layoutOf(width: Int, height: Int) = gameScreenLayoutFor(width.toFloat(), height.toFloat())

    @Test
    fun ordinaryPhonesKeepThePhoneLayout() {
        assertEquals("Pixel 7급(411×866)", GameScreenLayout.Phone, layoutOf(411, 866))
        assertEquals("작은 폰(360×740)", GameScreenLayout.Phone, layoutOf(360, 740))
        assertEquals("폴드 커버(344×834)", GameScreenLayout.Phone, layoutOf(344, 834))
    }

    /** 폭이 좁으면 짧아도 폰 배치다 — 넘치는 만큼은 #139의 판 맞춤이 받는다. */
    @Test
    fun aNarrowButShortScreenStaysOnThePhoneLayout() {
        assertEquals(GameScreenLayout.Phone, layoutOf(360, 600))
    }

    @Test
    fun theUnfoldedFoldGoesWideInBothOrientations() {
        assertEquals("펼친 세로(690×781)", GameScreenLayout.Wide, layoutOf(690, 781))
        assertEquals("펼친 가로(829×642)", GameScreenLayout.Wide, layoutOf(829, 642))
    }

    @Test
    fun aTallTabletKeepsThePhoneLayoutAndALandscapeTabletGoesWide() {
        assertEquals("세로 태블릿(800×1220)", GameScreenLayout.Phone, layoutOf(800, 1220))
        assertEquals("가로 태블릿(1280×752)", GameScreenLayout.Wide, layoutOf(1280, 752))
    }

    /** 경계: 폰 배치로 판이 가로폭의 90%를 채우면 폰, 못 채우면 넓은 배치. */
    @Test
    fun theBoundaryIsNinetyPercentOfTheWidth() {
        val width = 700
        val boardWidth = width - 32f
        val justEnough = (PhoneLayoutNonBoardHeightDp + boardWidth * PhoneLayoutMinBoardFillRatio).toInt() + 1
        assertEquals(GameScreenLayout.Phone, layoutOf(width, justEnough))
        assertEquals(GameScreenLayout.Wide, layoutOf(width, justEnough - 2))
    }
}
