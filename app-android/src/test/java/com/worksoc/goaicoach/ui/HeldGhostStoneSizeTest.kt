package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.ui.board.HeldGhostStoneRadiusRatio
import com.worksoc.goaicoach.ui.board.StoneRadiusRatio
import com.worksoc.goaicoach.ui.board.heldGhostStoneRadiusRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #196·#197 — 길게 눌러 조준하는 동안(제스처 ③, 설정 「착수 돌 크게」가 켜졌을 때)만 가늠돌이 칸 간격 0.7배로 커지고, 떼고 난 뒤의 돌은 전부 일반 크기다.
 */
class HeldGhostStoneSizeTest {

    @Test
    fun theHeldGhostGrowsFromTheNormalStoneToPointSeven() {
        assertEquals(0.42f, heldGhostStoneRadiusRatio(0f), 1e-6f)
        assertEquals(0.7f, heldGhostStoneRadiusRatio(1f), 1e-6f)
        assertEquals((0.42f + 0.7f) / 2f, heldGhostStoneRadiusRatio(0.5f), 1e-6f)
        assertEquals("넘치는 진행도는 0.7에서 멈춘다", 0.7f, heldGhostStoneRadiusRatio(2f), 1e-6f)
    }

    @Test
    fun theHeldGhostNeverReachesTheNeighbouringIntersection() {
        // 이웃 교차점은 중심에서 1칸 — 사용자 기준 "이웃 교차점을 덮지 않는 한계에 가깝게"(2026-09-30).
        assertTrue(HeldGhostStoneRadiusRatio < 1f)
        assertTrue(HeldGhostStoneRadiusRatio > StoneRadiusRatio)
    }

    private val board by lazy { RepoPaths.uiFile("GoBoard.kt").readContractSource() }

    @Test
    fun onlyTheDragGhostTakesTheHeldSizeAndOnlyAfterTheHoldThreshold() {
        assertEquals(
            "커진 반지름은 끌기 가늠돌 한 곳에서만 읽어야 한다 — 지연 착수·착수 확인·착수 이펙트는 일반 크기다",
            1,
            Regex("""heldGhostStoneRadiusRatio\(""").findAll(board).count(),
        )
        assertEquals(
            "held = true는 임계를 넘긴 ③의 두 자리(시작·따라가기)에서만 세운다 — ①②에서 세우면 짧은 탭마다 큰 돌이 번쩍인다",
            2,
            Regex("""held\s*=\s*true""").findAll(board).count(),
        )
        assertTrue(
            "떼고 난 뒤의 돌(임시 돌·지연 착수)은 일반 반지름 0.42여야 한다",
            Regex("""geometry\.spacing \* 0\.42f""").findAll(board).count() >= 3,
        )
    }

    @Test
    fun theLargeAimingStoneSettingGatesTheGrowth() {
        assertTrue(
            "설정 「착수 돌 크게」(#197)가 꺼져 있으면 커지면 안 된다 — 게이트는 설정과 ③을 함께 본다",
            board.contains("uxOptions.isLargeHeldStoneEnabled && playDrag?.held == true"),
        )
    }
}
