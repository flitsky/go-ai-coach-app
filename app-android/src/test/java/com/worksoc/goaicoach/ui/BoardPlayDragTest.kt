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

    // ── 조준점 분리 (#154) ─────────────────────────────────────────────────────

    /**
     * ⚠️ **탭은 띄우지 않는다.** 슬롭 안의 떨림까지 띄우면 **누른 곳과 다른 데 놓인다** —
     * #39가 *"살짝 미끄러진 탭이 엉뚱한 곳에 놓인다"* 며 막았던 바로 그 사고다.
     * 맨 아랫줄을 그냥 탭해서 놓을 수 있는 것도 이 성질 덕분이다(#154의 열화 수용 조건).
     */
    @Test
    fun aTapIsNeverLifted() {
        val down = Offset(100f, 100f)
        val follow = followDrag(down = down, current = Offset(102f, 102f), touchSlop = 10f, following = false, liftPx = 50f)

        assertEquals("탭인데 조준점이 손가락과 다르다 — 누른 곳과 다른 데 놓인다.", down, follow.target)
        assertEquals(down, follow.finger)
    }

    /** 따라가기 시작하면 조준점만 **위로** 올라가고, 손가락은 그대로 남는다. */
    @Test
    fun onceFollowingTheAimRisesAboveTheFingerButTheFingerStays() {
        val current = Offset(100f, 300f)
        val follow = followDrag(down = Offset(100f, 100f), current = current, touchSlop = 10f, following = true, liftPx = 50f)

        assertEquals("조준점이 손가락보다 위로 뜨지 않는다(#154).", Offset(100f, 250f), follow.target)
        assertEquals("손가락 자리가 함께 올라갔다 — 확대창이 손가락을 떠난다(#154).", current, follow.finger)
    }

    /** ⚠️ 가로는 건드리지 않는다 — 옆 줄로 새면 조준이 더 어려워진다. */
    @Test
    fun theLiftIsVerticalOnly() {
        val follow = followDrag(down = Offset(100f, 100f), current = Offset(240f, 300f), touchSlop = 10f, following = true, liftPx = 50f)

        assertEquals(240f, follow.target.x)
    }

    /** 띄움을 안 주면 예전과 정확히 같다 — 되돌리기가 한 줄이어야 한다(#145 패턴). */
    @Test
    fun withoutALiftTheBehaviourIsExactlyWhatItWasBefore() {
        val current = Offset(240f, 300f)
        val follow = followDrag(down = Offset(100f, 100f), current = current, touchSlop = 10f, following = true)

        assertEquals(current, follow.target)
        assertEquals(current, follow.finger)
    }

    /**
     * ⚠️ **띄움 폭은 dp가 아니라 칸이다**(#154). 같은 28dp가 판 크기에 따라 0.45~1.94칸으로 벌어져,
     * 촘촘한 판에서는 두 칸 가까이 건너뛰고 성긴 판에서는 손가락을 못 벗어난다.
     */
    @Test
    fun theLiftIsMeasuredInCellsSoItReadsTheSameOnEveryBoardSize() {
        assertEquals(1f, PlayDragLiftCells)
    }
}
