package com.worksoc.goaicoach.application.attendance

import com.worksoc.goaicoach.application.premium.FeatureAccess
import com.worksoc.goaicoach.application.premium.FeatureAccessPolicy
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.PremiumStateStorePort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeAttendanceStore(initial: AttendanceState = AttendanceState()) : AttendanceStorePort {
    var stored: AttendanceState = initial
        private set

    override fun save(state: AttendanceState) {
        stored = state
    }

    override fun load(): AttendanceState = stored
}

private class FakePremiumStore(initial: PremiumState = PremiumState()) : PremiumStateStorePort {
    var stored: PremiumState = initial
        private set

    override fun save(state: PremiumState) {
        stored = state
    }

    override fun load(): PremiumState = stored
}

class AttendanceRewardGrantTest {
    @Test
    fun firstEverCheckInGrantsUnlimitedUndoWithoutAnyUserConfirmation() {
        val attendanceStore = FakeAttendanceStore()
        val premiumStore = FakePremiumStore()
        val checkIn = runAttendanceCheckIn(AttendanceCheckInRequest(nowEpochMillis = 0L), attendanceStore)

        val result = runAttendanceRewardGrant(checkIn.state, attendanceStore, premiumStore)

        assertIs<AttendanceRewardGrantResult.Granted>(result)
        assertEquals(UndoUnlimitedRewardTier, result.tier)
        assertTrue(attendanceStore.stored.isTierClaimed(UndoUnlimitedRewardTier))
        assertEquals(setOf(FeatureId.Undo), premiumStore.stored.claimedFeatures)
    }

    @Test
    fun grantedUndoResolvesAsAllowedForAFreeUser() {
        val attendanceStore = FakeAttendanceStore()
        val premiumStore = FakePremiumStore()
        val checkIn = runAttendanceCheckIn(AttendanceCheckInRequest(nowEpochMillis = 0L), attendanceStore)

        runAttendanceRewardGrant(checkIn.state, attendanceStore, premiumStore)

        val access = FeatureAccessPolicy.resolve(FeatureId.Undo, premiumStore.stored, nowMillis = 0L)
        assertIs<FeatureAccess.Allowed>(access)
    }

    @Test
    fun rewardIsNotGrantedAgainOnLaterVisits() {
        val attendanceStore = FakeAttendanceStore()
        val premiumStore = FakePremiumStore()
        val day1 = runAttendanceCheckIn(AttendanceCheckInRequest(nowEpochMillis = 0L), attendanceStore)
        runAttendanceRewardGrant(day1.state, attendanceStore, premiumStore)

        val day2 = runAttendanceCheckIn(
            AttendanceCheckInRequest(nowEpochMillis = MillisPerUtcDay),
            attendanceStore,
        )
        val second = runAttendanceRewardGrant(day2.state, attendanceStore, premiumStore)

        assertIs<AttendanceRewardGrantResult.NothingToGrant>(second)
        assertEquals(2, attendanceStore.stored.attendanceCount)
        assertEquals(setOf(UndoUnlimitedRewardTier), attendanceStore.stored.claimedTiers)
    }

    @Test
    fun reopeningWithinTheSameDayDoesNotGrantTwice() {
        val attendanceStore = FakeAttendanceStore()
        val premiumStore = FakePremiumStore()
        val first = runAttendanceCheckIn(AttendanceCheckInRequest(nowEpochMillis = 0L), attendanceStore)
        runAttendanceRewardGrant(first.state, attendanceStore, premiumStore)

        val reopen = runAttendanceCheckIn(AttendanceCheckInRequest(nowEpochMillis = 1_000L), attendanceStore)
        assertIs<AttendanceCheckInResult.AlreadyCheckedInToday>(reopen)
        val second = runAttendanceRewardGrant(reopen.state, attendanceStore, premiumStore)

        assertIs<AttendanceRewardGrantResult.NothingToGrant>(second)
    }

    @Test
    fun nothingIsGrantedBeforeTheFirstCheckIn() {
        val attendanceStore = FakeAttendanceStore()
        val premiumStore = FakePremiumStore()

        val result = runAttendanceRewardGrant(AttendanceState(), attendanceStore, premiumStore)

        assertIs<AttendanceRewardGrantResult.NothingToGrant>(result)
        assertEquals(emptySet(), premiumStore.stored.claimedFeatures)
    }

    @Test
    fun aMissedGrantIsRepairedOnALaterLaunch() {
        // 최초 실행 때 지급 경로가 실패해 claimedTiers가 비어 있는 상태 — 다음 실행에서 복구된다.
        val attendanceStore = FakeAttendanceStore(
            AttendanceState(attendanceCount = 3, lastCheckInUtcDay = 2L),
        )
        val premiumStore = FakePremiumStore()

        val result = runAttendanceRewardGrant(attendanceStore.load(), attendanceStore, premiumStore)

        assertIs<AttendanceRewardGrantResult.Granted>(result)
        assertEquals(setOf(FeatureId.Undo), premiumStore.stored.claimedFeatures)
        assertTrue(attendanceStore.stored.isTierClaimed(UndoUnlimitedRewardTier))
    }

    @Test
    fun userWhoAlreadyClaimedUndoInGameKeepsASingleLedgerEntry() {
        val attendanceStore = FakeAttendanceStore()
        val premiumStore = FakePremiumStore(PremiumState(claimedFeatures = setOf(FeatureId.Undo)))
        val checkIn = runAttendanceCheckIn(AttendanceCheckInRequest(nowEpochMillis = 0L), attendanceStore)

        val result = runAttendanceRewardGrant(checkIn.state, attendanceStore, premiumStore)

        assertIs<AttendanceRewardGrantResult.Granted>(result)
        assertEquals(setOf(FeatureId.Undo), premiumStore.stored.claimedFeatures)
        assertTrue(attendanceStore.stored.isTierClaimed(UndoUnlimitedRewardTier))
    }
}
