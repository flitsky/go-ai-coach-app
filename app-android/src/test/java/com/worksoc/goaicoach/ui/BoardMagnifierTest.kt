package com.worksoc.goaicoach.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #39 — 돋보기 말풍선의 자리와 배율.
 *
 * ⚠️ **이 항목에서 실제로 틀리기 쉬운 곳이 여기다.** 제스처는 실기에서 합성 이벤트로 밟아
 * 볼 수 있지만, "말풍선이 캔버스를 넘지 않는가 · 뒤집힘이 드래그 중에 튀지 않는가 · 작은 판에서
 * 배율이 1배 밑으로 내려가지 않는가"는 눈으로 봐서는 놓친다.
 */
class BoardMagnifierTest {

    // 330dp 보드를 3배 밀도로 그린 캔버스. 19줄이면 칸 간격이 52.1px 남짓이다.
    private val canvas = Size(990f, 990f)
    private val spacing19 = 990f / 19f
    private val spacing9 = 990f / 9f
    private val gap = 84f // 28dp @ 3x

    @Test
    fun theBubbleSitsAboveTheFingerWhenThereIsRoom() {
        val touch = Offset(500f, 700f)
        val placement = magnifierPlacement(touch, canvas, spacing19, gap, below = false)

        assertTrue("손가락 위에 떠야 한다", placement.center.y < touch.y)
        // 원 아래 끝이 손가락에서 적어도 간격만큼 떨어져 있어야 손끝이 가리지 않는다.
        assertTrue(
            "손가락과 원이 너무 가깝다",
            touch.y - (placement.center.y + placement.radius) >= gap - 0.01f,
        )
    }

    /** 상변을 누르면 위쪽에 원이 안 들어간다 — 그 줄이야말로 확대가 필요한 자리다. */
    @Test
    fun theBubbleFlipsBelowNearTheTopEdge() {
        assertTrue(magnifierPrefersBelow(Offset(500f, 60f), canvas, spacing19, gap))
        assertFalse(magnifierPrefersBelow(Offset(500f, 900f), canvas, spacing19, gap))

        val placement = magnifierPlacement(Offset(500f, 60f), canvas, spacing19, gap, below = true)
        assertTrue("아래로 뒤집혀야 한다", placement.center.y > 60f)
    }

    /**
     * ⚠️ **드래그 중에 방향이 바뀌면 안 된다.** 방향을 호출부가 넘기므로, 같은 값을 주면 손가락이
     * 경계선을 넘어가도 같은 쪽에 머문다 — 이 테스트가 그 계약을 고정한다.
     */
    @Test
    fun thePinnedSideIsHonouredEvenWhereTheOppositeWouldBeChosen() {
        // 위쪽으로는 들어갈 자리가 없는 지점인데도, 위로 고정하면 위에 (가둬져서) 머문다.
        val high = Offset(500f, 60f)
        assertTrue("이 지점은 원래 아래를 고른다", magnifierPrefersBelow(high, canvas, spacing19, gap))

        val pinnedAbove = magnifierPlacement(high, canvas, spacing19, gap, below = false)
        assertFalse(pinnedAbove.below)
        assertTrue("캔버스 밖으로 나가면 안 된다", pinnedAbove.center.y - pinnedAbove.radius >= -0.01f)

        // 반대로, 아래를 고르는 지점에서 아래로 고정해도 그대로다.
        val low = Offset(500f, 900f)
        val pinnedBelow = magnifierPlacement(low, canvas, spacing19, gap, below = true)
        assertTrue(pinnedBelow.below)
    }

    @Test
    fun theBubbleNeverLeavesTheCanvasSideways() {
        listOf(0f, 5f, 495f, 985f, 990f).forEach { x ->
            val placement = magnifierPlacement(Offset(x, 700f), canvas, spacing19, gap, below = false)
            assertTrue(
                "x=$x 에서 왼쪽으로 삐져나갔다",
                placement.center.x - placement.radius >= -0.01f,
            )
            assertTrue(
                "x=$x 에서 오른쪽으로 삐져나갔다",
                placement.center.x + placement.radius <= canvas.width + 0.01f,
            )
        }
    }

    /**
     * ⚠️ **배율이 1배 밑으로 내려가면 돋보기가 축소경이 된다.** "5×5 셀"을 글자대로 지키면
     * 9줄 판에서 정확히 그 일이 벌어진다(5칸이 판의 절반을 넘는다) — 그래서 배율을 지키고
     * 칸 수를 양보하기로 했다. 그 결정을 여기서 고정한다.
     */
    @Test
    fun theScaleStaysAtTwoOnEveryBoardSizeEvenWhenFewerCellsFit() {
        listOf(spacing9, spacing19).forEach { spacing ->
            val placement = magnifierPlacement(Offset(500f, 700f), canvas, spacing, gap, below = false)
            assertEquals("배율이 흔들렸다", 2f, placement.scale, 0.001f)
            assertTrue("배율이 1배 이하로 내려갔다", placement.scale > 1f)
        }
    }

    /**
     * 큰 판일수록 확대창에 더 많은 칸이 들어간다 — 정밀도가 가장 필요한 19줄에서 이웃까지
     * 보이는지가 이 연출의 목적이다.
     */
    @Test
    fun aDenserBoardShowsMoreCellsInsideTheBubble() {
        fun visibleCells(spacing: Float): Float {
            val placement = magnifierPlacement(Offset(500f, 700f), canvas, spacing, gap, below = false)
            return placement.radius * 2f / (spacing * placement.scale)
        }

        val on19 = visibleCells(spacing19)
        val on9 = visibleCells(spacing9)
        assertTrue("19줄이 9줄보다 많이 보여야 한다 (19줄=$on19, 9줄=$on9)", on19 > on9)
        assertTrue("19줄에서 목표 교차점과 양옆이 보여야 한다 ($on19)", on19 >= 2.5f)
    }

    /** 말풍선이 판을 다 덮으면 무엇을 확대했는지 알 수 없다 — 지름 상한이 그것을 막는다. */
    @Test
    fun theBubbleNeverCoversMoreThanAThirdOfTheBoard() {
        val placement = magnifierPlacement(Offset(500f, 700f), canvas, spacing9, gap, below = false)
        assertTrue(
            "지름이 판의 1/3을 넘었다 (${placement.radius * 2})",
            placement.radius * 2f <= canvas.minDimension * 0.34f,
        )
    }
}
