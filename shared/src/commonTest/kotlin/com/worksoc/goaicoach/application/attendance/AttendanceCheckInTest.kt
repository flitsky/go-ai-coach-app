package com.worksoc.goaicoach.application.attendance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val Day0 = 0L
private fun startOfDay(dayIndex: Long): Long = dayIndex * MillisPerUtcDay

class AttendanceCheckInTest {
    @Test
    fun firstEverCheckInStartsCountAtOne() {
        val result = AttendanceState().checkIn(startOfDay(Day0))

        assertTrue(result is AttendanceCheckInResult.CheckedIn)
        assertEquals(1, result.rewardTier)
        assertEquals(1, result.state.attendanceCount)
        assertEquals(Day0, result.state.lastCheckInUtcDay)
    }

    @Test
    fun reopeningWithinTheSameUtcDayDoesNotIncrementCount() {
        val afterFirstVisit = (AttendanceState().checkIn(startOfDay(Day0)) as AttendanceCheckInResult.CheckedIn).state

        val secondVisitSameDay = afterFirstVisit.checkIn(startOfDay(Day0) + MillisPerUtcDay - 1)

        assertTrue(secondVisitSameDay is AttendanceCheckInResult.AlreadyCheckedInToday)
        assertEquals(1, secondVisitSameDay.state.attendanceCount)
    }

    @Test
    fun skippingDaysStillOnlyAdvancesCountByOne() {
        // 연속 출석 요구 없음 — 1일차 방문 후 3일을 건너뛰고 돌아와도 2일차로만 진행한다.
        val afterDay1 = (AttendanceState().checkIn(startOfDay(Day0)) as AttendanceCheckInResult.CheckedIn).state

        val afterGap = afterDay1.checkIn(startOfDay(Day0 + 4))

        assertTrue(afterGap is AttendanceCheckInResult.CheckedIn)
        assertEquals(2, afterGap.rewardTier)
        assertEquals(2, afterGap.state.attendanceCount)
    }

    @Test
    fun consecutiveDaysEachAdvanceCountByOne() {
        var state = AttendanceState()
        for (day in Day0 until Day0 + 5) {
            val result = state.checkIn(startOfDay(day))
            assertTrue(result is AttendanceCheckInResult.CheckedIn)
            state = result.state
        }

        assertEquals(5, state.attendanceCount)
    }

    @Test
    fun rewardTierIsTrueForEveryDayOneThroughSeven() {
        for (tier in 1..7) {
            assertTrue(isRewardedTier(tier), "tier $tier should be rewarded")
        }
    }

    @Test
    fun rewardTierIsFalseBetweenWeeklyMilestonesAfterDaySeven() {
        for (tier in listOf(8, 9, 10, 11, 12, 13, 15, 20)) {
            assertFalse(isRewardedTier(tier), "tier $tier should not be rewarded")
        }
    }

    @Test
    fun rewardTierIsTrueOnWeeklyMilestonesAfterDaySeven() {
        for (tier in listOf(14, 21, 28, 35)) {
            assertTrue(isRewardedTier(tier), "tier $tier should be rewarded")
        }
    }

    @Test
    fun claimedTiersTracksRewardsIndependentlyOfCheckIn() {
        val state = AttendanceState()

        assertFalse(state.isTierClaimed(1))
        val claimed = state.withTierClaimed(1)
        assertTrue(claimed.isTierClaimed(1))
        assertFalse(claimed.isTierClaimed(2))
    }
}
