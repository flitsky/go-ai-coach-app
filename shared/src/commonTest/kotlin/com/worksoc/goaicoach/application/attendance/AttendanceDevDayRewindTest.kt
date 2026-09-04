package com.worksoc.goaicoach.application.attendance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private class FakeStore(initial: AttendanceState) : AttendanceStorePort {
    var stored: AttendanceState = initial
        private set
    var saveCount: Int = 0
        private set

    override fun save(state: AttendanceState) {
        stored = state
        saveCount++
    }

    override fun load(): AttendanceState = stored
}

/**
 * 개발자 테스트용 하루 되감기([runAttendanceDevDayRewind])의 계약(백로그 #71).
 *
 * ⚠️ **되감기가 "출석일을 올리는 것"이 아니라는 점이 이 계약의 핵심이다.** 올리는 것은
 * [checkIn]의 책임이고, 되감기는 그 판정이 다시 통과하게 **문만 열어 준다.** 여기서 카운트까지
 * 올리면 체크인 경로가 둘이 되어, 한쪽을 고칠 때 다른 쪽이 남는다(#66이 그 결말이다).
 */
class AttendanceDevDayRewindTest {

    private val today = 20_700L
    private val now = today * MillisPerUtcDay

    /** 되감으면 **오늘 체크인 기록만** 지워진다 — 카운트와 수령 기록은 그대로다. */
    @Test
    fun rewindingClearsOnlyTheCheckInDayMarker() {
        val store = FakeStore(
            AttendanceState(attendanceCount = 5, lastCheckInUtcDay = today, claimedTiers = setOf(1, 2, 3)),
        )

        val next = runAttendanceDevDayRewind(store)

        assertNull(next?.lastCheckInUtcDay)
        assertEquals(5, next?.attendanceCount, "되감기가 출석일을 건드렸다 — 증가는 checkIn의 책임이다(#71).")
        assertEquals(setOf(1, 2, 3), next?.claimedTiers, "되감기가 수령 기록을 건드렸다 — 그러면 이미 받은 보상이 다시 나온다.")
    }

    /** 되감은 **뒤에** 체크인하면 하루가 진행된다 — 이 둘이 합쳐져 "하루 진행" 버튼이 된다. */
    @Test
    fun aRewindFollowedByACheckInAdvancesExactlyOneDay() {
        val store = FakeStore(AttendanceState(attendanceCount = 5, lastCheckInUtcDay = today))

        runAttendanceDevDayRewind(store)
        val checkIn = store.load().checkIn(now)

        val checkedIn = assertIs<AttendanceCheckInResult.CheckedIn>(checkIn, "되감았는데도 이미 체크인한 것으로 판정됐다.")
        assertEquals(6, checkedIn.state.attendanceCount)
        assertEquals(6, checkedIn.rewardTier)
        assertEquals(today, checkedIn.state.lastCheckInUtcDay, "같은 날짜로 다시 기록돼야 한다 — 되감기는 시간을 읽지 않는다.")
    }

    /**
     * ⚠️ **되감기는 시간을 읽지 않는다.** `shared` commonMain에는 bare `System.`이 금지돼 있고
     * (iOS 타깃), 전역 시계 오프셋은 프리미엄 만료·타이머까지 오염시킨다. 그래서 "어제로
     * 만든다"가 아니라 "표시만 지운다"다 — 같은 `now`로 다시 체크인해도 통과하는지가 그 증거다.
     */
    @Test
    fun repeatedRewindsAdvanceDayAfterDayWithoutMovingTheClock() {
        val store = FakeStore(AttendanceState(attendanceCount = 1, lastCheckInUtcDay = today))

        repeat(6) {
            runAttendanceDevDayRewind(store)
            store.save(store.load().checkIn(now).state)
        }

        assertEquals(7, store.load().attendanceCount, "같은 시각으로 여섯 번 되감아 7일차에 닿지 못했다(#71).")
    }

    /** 이미 되감긴 상태에서는 아무것도 하지 않는다 — 두 번 눌러도 하루가 두 번 가지 않는다. */
    @Test
    fun rewindingTwiceInARowIsANoOp() {
        val store = FakeStore(AttendanceState(attendanceCount = 3, lastCheckInUtcDay = today))

        runAttendanceDevDayRewind(store)
        val second = runAttendanceDevDayRewind(store)

        assertNull(second, "이미 되감긴 상태인데 또 저장했다 — 버튼 연타가 하루를 여러 번 태운다.")
        assertEquals(1, store.saveCount)
    }
}
