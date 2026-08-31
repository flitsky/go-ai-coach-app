package com.worksoc.goaicoach.application.attendance

import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.PremiumOnceMaxStock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 백로그 #55 — 도장판이 그릴 내용을 정하는 순수 계층. */
class AttendanceBoardTest {

    @Test
    fun theBoardIsAlwaysTenCellsInTwoRows() {
        val board = buildAttendanceBoard(AttendanceState())

        assertEquals(listOf(1, 2, 3, 4, 5, 6), board.daily.map { it.tier })
        assertEquals(listOf(7, 14, 21, 28), board.weekly.map { it.tier })
        assertEquals(10, board.cells.size)
    }

    @Test
    fun cellsSplitIntoStampedClaimableAndUpcoming() {
        // 3일차까지 출석했고 1·2일차만 받아 갔다 — 3일차는 지금 받을 수 있고 나머지는 아직이다.
        val board = buildAttendanceBoard(
            AttendanceState(attendanceCount = 3, claimedTiers = setOf(1, 2)),
        )

        assertEquals(AttendanceCellState.Stamped, board.daily[0].state)
        assertEquals(AttendanceCellState.Stamped, board.daily[1].state)
        assertEquals(AttendanceCellState.Claimable, board.daily[2].state)
        assertEquals(AttendanceCellState.Upcoming, board.daily[3].state)
        assertTrue(board.hasClaimable)
    }

    @Test
    fun everyCellCarriesTheRewardsItWillGive() {
        val board = buildAttendanceBoard(AttendanceState())

        // 판이 정책표를 그대로 들고 있어야 "무엇을 향해 모으는 중인지"가 보인다.
        assertEquals(AttendanceRewardPolicy.rewardsFor(3), board.daily[2].rewards)
        assertEquals(AttendanceRewardPolicy.rewardsFor(28), board.weekly.last().rewards)
        assertTrue(board.weekly.last().rewards.any { it is AttendanceReward.BotCharacterUnlock })
    }

    /**
     * ⚠️ 28일차는 판의 끝이지 **정책의 끝이 아니다.** 35·42…가 계속 나오므로, 판이 다 찍힌 뒤의
     * 회차를 따로 들고 있지 않으면 그 사용자는 받을 것이 있는데도 아무 표시를 못 본다.
     */
    @Test
    fun tiersPastTheBoardAreKeptAside() {
        val board = buildAttendanceBoard(
            AttendanceState(attendanceCount = 35, claimedTiers = (1..28).toSet()),
        )

        assertTrue(board.cells.all { it.state == AttendanceCellState.Stamped })
        assertEquals(listOf(35), board.beyondBoard.map { it.tier })
        assertTrue(board.hasClaimable)
    }

    @Test
    fun aFinishedBoardWithNothingLeftHasNothingToClaim() {
        val board = buildAttendanceBoard(
            AttendanceState(attendanceCount = 28, claimedTiers = (1..28).toSet()),
        )

        assertTrue(board.cells.all { it.state == AttendanceCellState.Stamped })
        assertTrue(board.beyondBoard.isEmpty())
        assertTrue(!board.hasClaimable)
    }

    /**
     * ⚠️ 이 항목이 반드시 같이 고쳐야 했던 결함이다(#55 ⓑ). 상한에 걸리면 `withGranted`가
     * 조용히 버리므로, 화면이 "3개"라고 안내해 놓고 0개가 들어가는 일이 생긴다.
     */
    @Test
    fun aRewardAtItsStockCapReportsThatNothingWillArrive() {
        val full = ConsumableInventory().withGranted(ConsumableCatalog.PremiumOnce.id, PremiumOnceMaxStock)
        val reward = AttendanceReward.Consumable(ConsumableCatalog.PremiumOnce, 3)

        assertEquals(0, grantedAmountOf(reward, full))
        // 여유가 하나뿐이면 하나만 들어간다 — "전부 아니면 전무"가 아니다.
        val nearlyFull = ConsumableInventory().withGranted(ConsumableCatalog.PremiumOnce.id, PremiumOnceMaxStock - 1)
        assertEquals(1, grantedAmountOf(reward, nearlyFull))
        // 여유가 넉넉하면 요청한 만큼 그대로.
        assertEquals(3, grantedAmountOf(reward, ConsumableInventory()))
    }

    /**
     * ⚠️ **화면 하나가 이 사실 위에 서 있다**(#57). 좁은 여섯 칸은 폭이 45dp 남짓이라 보상을
     * **하나만** 그리는데, 확정표상 1~6일차에 보상이 정확히 하나씩이기 때문에 성립한다.
     * 표를 고쳐 그 회차에 보상이 둘 이상 생기면 화면은 조용히 하나만 보여주게 되므로,
     * **그 전에 여기서 먼저 깨져야 한다.**
     */
    @Test
    fun everyDailyCellCarriesExactlyOneReward() {
        buildAttendanceBoard(AttendanceState()).daily.forEach { cell ->
            assertEquals(1, cell.rewards.size, "${cell.tier}일차 보상 수")
        }
    }

    @Test
    fun nonConsumableRewardsHaveNoStockToReportOn() {
        val character = AttendanceRewardPolicy.rewardsFor(7).single()

        assertEquals(null, grantedAmountOf(character, ConsumableInventory()))
    }
}
