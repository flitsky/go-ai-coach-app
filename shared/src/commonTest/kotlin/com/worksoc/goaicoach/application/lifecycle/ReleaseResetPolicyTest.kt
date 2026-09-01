package com.worksoc.goaicoach.application.lifecycle

import com.worksoc.goaicoach.application.attendance.AttendanceState
import com.worksoc.goaicoach.application.botcharacter.BotCharacterId
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableItemId
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.PremiumState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseResetPolicyTest {

    @Test
    fun aDeviceThatNeverAppliedAResetIsReset() {
        // 마커가 없는 기기 — 비공개 테스트 빌드에서 올라온 경우다. 이번 세대를 적용해야 한다.
        assertTrue(
            ReleaseResetPolicy.shouldReset(
                lastAppliedGeneration = ReleaseResetPolicy.NoResetApplied,
                currentGeneration = 1,
            ),
        )
    }

    @Test
    fun aDeviceThatAlreadyAppliedThisGenerationIsNotResetAgain() {
        // 초기화가 매 실행마다 반복되면 사용자가 켤 때마다 진행이 사라진다 — 가장 나쁜 실패다.
        assertFalse(
            ReleaseResetPolicy.shouldReset(
                lastAppliedGeneration = 1,
                currentGeneration = 1,
            ),
        )
    }

    @Test
    fun raisingTheGenerationSchedulesOneMoreReset() {
        // 다음 초기화를 예약하는 방법이 "상수를 1 올린다" 하나뿐임을 못박는다.
        assertTrue(ReleaseResetPolicy.shouldReset(lastAppliedGeneration = 1, currentGeneration = 2))
        assertFalse(ReleaseResetPolicy.shouldReset(lastAppliedGeneration = 2, currentGeneration = 2))
    }

    @Test
    fun aDeviceFromTheFutureIsNotReset() {
        // 상수를 되돌리는 실수(2 → 1)로 이미 최신인 기기를 밀지 않는다.
        assertFalse(ReleaseResetPolicy.shouldReset(lastAppliedGeneration = 3, currentGeneration = 1))
    }

    @Test
    fun theShippedGenerationIsOneAndTheAbsentMarkerReadsAsZero() {
        // ⚠️ 이 둘은 저장 포맷과 맞물린 값이라 무심코 바꾸면 전 기기가 한 번 더 밀린다.
        // 바꿀 일이 생기면 이 테스트가 먼저 실패해서 의도를 확인하게 만든다.
        assertEquals(1, ReleaseResetPolicy.CurrentResetGeneration)
        assertEquals(0, ReleaseResetPolicy.NoResetApplied)
    }

    // ---- 안내를 누구에게 띄울지 (hasEntitlementData) ----

    @Test
    fun aFreshInstallHasNothingToLoseSoNoNoticeIsShown() {
        // ⚠️ 이 테스트가 막는 사고: **처음 설치한 사람에게 "테스트 기록이 초기화됐다"고 알리는 것.**
        // 초기화 자체는 신규 설치에서도 돌기 때문에(무해하다) 그 값으로 안내하면 이 사고가 난다.
        assertFalse(
            ReleaseResetPolicy.hasEntitlementData(
                attendance = AttendanceState(),
                collection = BotCollectionState(),
                consumables = ConsumableInventory(),
                premium = PremiumState(),
            ),
        )
    }

    @Test
    fun anyOneOfTheFourStoresHavingDataIsEnoughToNotify() {
        // 넷 중 **하나만** 차 있어도 잃은 것이 있다. 넷을 &&로 묶는 실수를 막는다.
        val withAttendance = ReleaseResetPolicy.hasEntitlementData(
            attendance = AttendanceState(attendanceCount = 1),
            collection = BotCollectionState(),
            consumables = ConsumableInventory(),
            premium = PremiumState(),
        )
        val withCharacter = ReleaseResetPolicy.hasEntitlementData(
            attendance = AttendanceState(),
            collection = BotCollectionState(claimedBots = setOf(BotCharacterId("fast_beginner_2"))),
            consumables = ConsumableInventory(),
            premium = PremiumState(),
        )
        val withTickets = ReleaseResetPolicy.hasEntitlementData(
            attendance = AttendanceState(),
            collection = BotCollectionState(),
            consumables = ConsumableInventory(counts = mapOf(ConsumableItemId("eval_once") to 3)),
            premium = PremiumState(),
        )
        val withClaimedFeature = ReleaseResetPolicy.hasEntitlementData(
            attendance = AttendanceState(),
            collection = BotCollectionState(),
            consumables = ConsumableInventory(),
            premium = PremiumState(claimedFeatures = setOf(FeatureId.Undo)),
        )
        assertTrue(withAttendance)
        assertTrue(withCharacter)
        assertTrue(withTickets)
        assertTrue(withClaimedFeature)
    }

    @Test
    fun shardProgressAloneStillCountsAsSomethingLost() {
        // 조각은 캐릭터를 아직 못 연 상태라 놓치기 쉽다 — 광고를 본 노력이므로 잃은 것이 맞다.
        assertTrue(
            ReleaseResetPolicy.hasEntitlementData(
                attendance = AttendanceState(),
                collection = BotCollectionState(
                    adShards = mapOf(BotCharacterId("fast_beginner_4") to 3),
                ),
                consumables = ConsumableInventory(),
                premium = PremiumState(),
            ),
        )
    }
}
