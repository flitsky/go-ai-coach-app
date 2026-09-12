package com.worksoc.goaicoach.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /** ⚠️ 남는 변이 어디냐로 갈린다 — 세로로 펼치면 위아래(P1), 가로로 돌리면 좌우(L1). */
    @Test
    fun theUnfoldedFoldStacksWhenTallAndUsesColumnsWhenWide() {
        assertEquals("펼친 세로(690×781)", GameScreenLayout.WideStacked, layoutOf(690, 781))
        assertEquals("펼친 가로(829×642)", GameScreenLayout.WideColumns, layoutOf(829, 642))
    }

    @Test
    fun aTallTabletKeepsThePhoneLayoutAndALandscapeTabletUsesColumns() {
        assertEquals("세로 태블릿(800×1220)", GameScreenLayout.Phone, layoutOf(800, 1220))
        assertEquals("가로 태블릿(1280×752)", GameScreenLayout.WideColumns, layoutOf(1280, 752))
    }

    /** 두 넓은 배치는 화면 껍데기(스크롤 없음)를 공유한다 — 그 물음에 한 번에 답하는 값. */
    @Test
    fun bothWideLayoutsAnswerIsWide() {
        assertTrue(GameScreenLayout.WideStacked.isWide && GameScreenLayout.WideColumns.isWide)
        assertFalse(GameScreenLayout.Phone.isWide)
    }

    /** 경계: 폰 배치로 판이 가로폭의 90%를 채우면 폰, 못 채우면 넓은 배치. */
    @Test
    fun theBoundaryIsNinetyPercentOfTheWidth() {
        val width = 700
        val boardWidth = width - 32f
        val justEnough = (PhoneLayoutNonBoardHeightDp + boardWidth * PhoneLayoutMinBoardFillRatio).toInt() + 1
        assertEquals(GameScreenLayout.Phone, layoutOf(width, justEnough))
        assertEquals(GameScreenLayout.WideStacked, layoutOf(width, justEnough - 2))
    }
}
