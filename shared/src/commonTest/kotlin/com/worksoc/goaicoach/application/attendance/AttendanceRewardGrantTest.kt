package com.worksoc.goaicoach.application.attendance

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableStorePort
import com.worksoc.goaicoach.application.premium.FeatureAccess
import com.worksoc.goaicoach.application.premium.FeatureAccessPolicy
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.PremiumStateStorePort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

private class FakeConsumableStore(initial: ConsumableInventory = ConsumableInventory()) : ConsumableStorePort {
    var stored: ConsumableInventory = initial
        private set

    override fun save(inventory: ConsumableInventory) {
        stored = inventory
    }

    override fun load(): ConsumableInventory = stored
}

private class FakeBotStore(initial: BotCollectionState = BotCollectionState()) : BotCollectionStorePort {
    var stored: BotCollectionState = initial
        private set

    override fun save(state: BotCollectionState) {
        stored = state
    }

    override fun load(): BotCollectionState = stored
}

/** 네 저장소를 한 묶음으로 들고 다니는 테스트 픽스처 — 매 테스트가 같은 배선을 반복하지 않게 한다. */
private class RewardStores(
    initialAttendance: AttendanceState = AttendanceState(),
    initialPremium: PremiumState = PremiumState(),
) {
    val attendance = FakeAttendanceStore(initialAttendance)
    val premium = FakePremiumStore(initialPremium)
    val consumables = FakeConsumableStore()
    val bots = FakeBotStore()

    fun checkInAt(nowEpochMillis: Long): AttendanceCheckInResult =
        runAttendanceCheckIn(AttendanceCheckInRequest(nowEpochMillis = nowEpochMillis), attendance)

    fun grant(state: AttendanceState = attendance.load()): AttendanceRewardGrantResult =
        runAttendanceRewardGrant(
            state = state,
            attendanceStore = attendance,
            premiumStore = premium,
            consumableStore = consumables,
            botStore = bots,
        )

    /** 하루씩 [days]일 연속 출석하며 매일 보상을 지급받는다. */
    fun attendForDays(days: Int) {
        repeat(days) { day ->
            val checkIn = checkInAt(day * MillisPerUtcDay)
            grant(checkIn.state)
        }
    }
}

private val firstCharacter = BotCharacterCatalog.fastBeginnerRoster[0]
private val secondCharacter = BotCharacterCatalog.fastBeginnerRoster[1]

class AttendanceRewardPolicyTest {

    @Test
    fun dayOneGrantsBothUnlimitedUndoAndTheFirstCharacter() {
        // 4.2절 정책표 — 1일차부터 보상이 2개다. "일차 → 보상 1개"로는 표현할 수 없는 지점.
        val rewards = AttendanceRewardPolicy.rewardsFor(1)

        assertEquals(2, rewards.size)
        assertTrue(AttendanceReward.PermanentFeature(FeatureId.Undo) in rewards)
        assertTrue(AttendanceReward.BotCharacterUnlock(firstCharacter) in rewards)
    }

    @Test
    fun daysTwoThroughFourGrantTenConsumablesEach() {
        assertEquals(
            listOf(AttendanceReward.Consumable(ConsumableCatalog.EvalOnce, 10)),
            AttendanceRewardPolicy.rewardsFor(2),
        )
        assertEquals(
            listOf(AttendanceReward.Consumable(ConsumableCatalog.TopMovesOnce, 10)),
            AttendanceRewardPolicy.rewardsFor(3),
        )
        assertEquals(
            listOf(AttendanceReward.Consumable(ConsumableCatalog.PremiumOnce, 10)),
            AttendanceRewardPolicy.rewardsFor(4),
        )
    }

    @Test
    fun dayFiveGrantsTheSecondCharacter() {
        assertEquals(
            listOf(AttendanceReward.BotCharacterUnlock(secondCharacter)),
            AttendanceRewardPolicy.rewardsFor(5),
        )
    }

    @Test
    fun rewardedTiersWithUndecidedContentStayEmpty() {
        // 6·7일차와 14/21/28일차는 보상 회차이지만 내용이 아직 미확정이다(4.2절).
        listOf(6, 7, 14, 21, 28).forEach { tier ->
            assertTrue(isRewardedTier(tier), "tier $tier must still be a rewarded tier")
            assertTrue(AttendanceRewardPolicy.rewardsFor(tier).isEmpty(), "tier $tier must have no content yet")
        }
    }

    @Test
    fun nonRewardedTiersHaveNoRewards() {
        listOf(0, -1, 8, 9, 13).forEach { tier ->
            assertTrue(AttendanceRewardPolicy.rewardsFor(tier).isEmpty(), "tier $tier must have no rewards")
        }
    }

    @Test
    fun pendingTiersListsEveryUnclaimedTierWithContentInOrder() {
        val state = AttendanceState(attendanceCount = 5, claimedTiers = setOf(1, 3))

        assertEquals(listOf(2, 4, 5), AttendanceRewardPolicy.pendingTiers(state).map { it.tier })
    }

    @Test
    fun pendingTiersSkipsTiersWhoseContentIsStillUndecided() {
        // 7일차까지 출석했지만 6·7일차는 콘텐츠가 없어 목록에 뜨지 않는다.
        val state = AttendanceState(attendanceCount = 7, claimedTiers = setOf(1, 2, 3, 4, 5))

        assertTrue(AttendanceRewardPolicy.pendingTiers(state).isEmpty())
    }
}

class AttendanceRewardGrantTest {

    @Test
    fun firstEverCheckInGrantsUndoAndTheFirstCharacterInOneCall() {
        val stores = RewardStores()
        val checkIn = stores.checkInAt(0L)

        val result = stores.grant(checkIn.state)

        assertTrue(result.didGrant)
        assertEquals(listOf(1), result.granted.map { it.tier })
        assertEquals(2, result.grantedRewards.size)
        assertTrue(stores.attendance.stored.isTierClaimed(UndoUnlimitedRewardTier))
        assertEquals(setOf(FeatureId.Undo), stores.premium.stored.claimedFeatures)
        assertTrue(stores.bots.stored.isClaimed(firstCharacter.id))
    }

    @Test
    fun grantedUndoResolvesAsAllowedForAFreeUser() {
        val stores = RewardStores()
        stores.grant(stores.checkInAt(0L).state)

        val access = FeatureAccessPolicy.resolve(FeatureId.Undo, stores.premium.stored, nowMillis = 0L)
        assertIs<FeatureAccess.Allowed>(access)
    }

    @Test
    fun consumableDaysStockTheInventoryTenAtATime() {
        val stores = RewardStores()

        stores.attendForDays(4)

        assertEquals(10, stores.consumables.stored.countOf(ConsumableCatalog.EvalOnce.id))
        assertEquals(10, stores.consumables.stored.countOf(ConsumableCatalog.TopMovesOnce.id))
        assertEquals(10, stores.consumables.stored.countOf(ConsumableCatalog.PremiumOnce.id))
    }

    @Test
    fun theSecondCharacterArrivesOnDayFiveAndNotBefore() {
        val stores = RewardStores()

        stores.attendForDays(4)
        assertFalse(stores.bots.stored.isClaimed(secondCharacter.id))

        stores.attendForDays(1)
        assertTrue(stores.bots.stored.isClaimed(secondCharacter.id))
    }

    @Test
    fun rewardIsNotGrantedAgainOnLaterVisits() {
        val stores = RewardStores()
        stores.grant(stores.checkInAt(0L).state)

        val day2 = stores.checkInAt(MillisPerUtcDay)
        val second = stores.grant(day2.state)

        // 2일차에는 2일차 보상만 새로 나온다 — 1일차 보상이 다시 지급되지 않는다.
        assertEquals(listOf(2), second.granted.map { it.tier })
        assertEquals(setOf(1, 2), stores.attendance.stored.claimedTiers)
        assertEquals(10, stores.consumables.stored.countOf(ConsumableCatalog.EvalOnce.id))
    }

    @Test
    fun reopeningWithinTheSameDayDoesNotGrantTwice() {
        val stores = RewardStores()
        stores.grant(stores.checkInAt(0L).state)

        val reopen = stores.checkInAt(1_000L)
        assertIs<AttendanceCheckInResult.AlreadyCheckedInToday>(reopen)

        val second = stores.grant(reopen.state)
        assertFalse(second.didGrant)
        assertTrue(second.granted.isEmpty())
    }

    @Test
    fun nothingIsGrantedBeforeTheFirstCheckIn() {
        val stores = RewardStores()

        val result = stores.grant(AttendanceState())

        assertFalse(result.didGrant)
        assertEquals(emptySet(), stores.premium.stored.claimedFeatures)
        assertEquals(BotCollectionState(), stores.bots.stored)
        assertEquals(ConsumableInventory(), stores.consumables.stored)
    }

    @Test
    fun everyMissedTierIsRepairedInOneLaterLaunch() {
        // 지급 경로가 며칠간 실패해 claimedTiers가 비어 있는 상태 — 밀린 일차를 한 번에 복구한다.
        val stores = RewardStores(AttendanceState(attendanceCount = 5, lastCheckInUtcDay = 4L))

        val result = stores.grant()

        assertEquals(listOf(1, 2, 3, 4, 5), result.granted.map { it.tier })
        assertEquals(setOf(FeatureId.Undo), stores.premium.stored.claimedFeatures)
        assertEquals(10, stores.consumables.stored.countOf(ConsumableCatalog.EvalOnce.id))
        assertTrue(stores.bots.stored.isClaimed(firstCharacter.id))
        assertTrue(stores.bots.stored.isClaimed(secondCharacter.id))
        assertEquals(setOf(1, 2, 3, 4, 5), stores.attendance.stored.claimedTiers)
    }

    @Test
    fun undecidedTiersAreNotMarkedClaimedSoTheirRewardsSurviveALaterDecision() {
        // 6·7일차를 지나쳤어도 claimedTiers에 들어가면 안 된다 — 나중에 콘텐츠가 정해졌을 때
        // 그 사이 지나간 사용자가 영영 못 받는 일이 없어야 한다.
        val stores = RewardStores(AttendanceState(attendanceCount = 7, lastCheckInUtcDay = 6L))

        stores.grant()

        assertEquals(setOf(1, 2, 3, 4, 5), stores.attendance.stored.claimedTiers)
        assertFalse(stores.attendance.stored.isTierClaimed(6))
        assertFalse(stores.attendance.stored.isTierClaimed(7))
    }

    @Test
    fun userWhoAlreadyClaimedUndoInGameKeepsASingleLedgerEntry() {
        val stores = RewardStores(initialPremium = PremiumState(claimedFeatures = setOf(FeatureId.Undo)))
        val checkIn = stores.checkInAt(0L)

        val result = stores.grant(checkIn.state)

        assertTrue(result.didGrant)
        assertEquals(setOf(FeatureId.Undo), stores.premium.stored.claimedFeatures)
        assertTrue(stores.attendance.stored.isTierClaimed(UndoUnlimitedRewardTier))
    }

    @Test
    fun regrantingAnAlreadyOwnedCharacterIsIdempotent() {
        val stores = RewardStores()
        stores.grant(stores.checkInAt(0L).state)
        val afterFirst = stores.bots.stored

        // 지급 기록만 지워진 채 다시 돌아도(복구 경로) 컬렉션이 중복으로 부풀지 않는다.
        stores.grant(AttendanceState(attendanceCount = 1, lastCheckInUtcDay = 0L))

        assertEquals(afterFirst, stores.bots.stored)
    }
}
