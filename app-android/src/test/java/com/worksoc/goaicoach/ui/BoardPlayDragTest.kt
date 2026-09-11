package com.worksoc.goaicoach.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #138 — 가늠돌이 손가락을 **언제부터** 따라가는가.
 *
 * ⚠️ 이 넷이 빠른 탭의 안전장치다. 빠른 탭이 이제 **뗀 자리**(가늠돌 자리)에 놓이므로, 슬롭 안의
 * 떨림까지 따라가면 교차점 경계 근처의 탭이 옆 칸에 놓인다 — #39가 막아 온 회귀가 되살아난다.
 */
class BoardPlayDragTest {

    private val down = Offset(300f, 300f)

    // 8dp @ 3배 밀도 — 흔한 `touchSlop` 값.
    private val slop = 24f

    @Test
    fun jitterInsideTheSlopKeepsTheFirstTouch() {
        val result = followDrag(down, Offset(310f, 305f), slop, following = false)
        assertFalse("슬롭 안의 떨림으로 따라가기 시작했다", result.following)
        assertEquals("슬롭 안에서는 처음 누른 자리에 머물러야 한다", down, result.target)
    }

    @Test
    fun movingPastTheSlopFollowsTheFinger() {
        val finger = Offset(360f, 300f)
        val result = followDrag(down, finger, slop, following = false)
        assertTrue(result.following)
        assertEquals(finger, result.target)
    }

    /** 한 번 따라가기 시작하면 되돌아와도 계속 따라간다 — 처음 자리로 튕기면 더 헷갈린다. */
    @Test
    fun onceFollowingItKeepsFollowingEvenBackInsideTheSlop() {
        val backNearStart = Offset(305f, 300f)
        val result = followDrag(down, backNearStart, slop, following = true)
        assertTrue(result.following)
        assertEquals(backNearStart, result.target)
    }

    /** 경계값 — 정확히 슬롭만큼은 아직 떨림이다(플랫폼의 "넘었다"는 초과다). */
    @Test
    fun exactlyTheSlopIsStillJitter() {
        val result = followDrag(down, Offset(300f + slop, 300f), slop, following = false)
        assertFalse(result.following)
        assertEquals(down, result.target)
    }
}
