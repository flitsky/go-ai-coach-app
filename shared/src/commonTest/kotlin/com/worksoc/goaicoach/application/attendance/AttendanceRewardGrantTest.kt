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

// 출석으로 열리는 캐릭터는 3단계(도장생 반상, 4일차) 하나뿐이다 — 1단계는 기본 제공이고
// 2·4단계는 광고 조각, 5단계는 유료다(7장 재확정본, #16). 카탈로그가 단일 출처이므로 여기서도
// 인덱스가 아니라 카탈로그 조회로 가져와, 표가 또 바뀌면 이 픽스처가 자동으로 따라가게 한다.
private val attendanceCharacter = BotCharacterCatalog.forAttendanceTier(4).single()
private val defaultCharacter = BotCharacterCatalog.fastBeginnerRoster.first()

class AttendanceRewardPolicyTest {

    @Test
    fun dayOneGrantsUnlimitedUndoOnlyNowThatTheFirstCharacterIsFree() {
        // #16 전에는 1일차가 무르기 + 첫 캐릭터 2개였다. 1단계가 기본 제공으로 바뀌면서
        // 정책표에 코드를 더하지 않고도 캐릭터 중복 지급이 사라졌다(#19가 기대던 선행 조건).
        val rewards = AttendanceRewardPolicy.rewardsFor(1)

        assertEquals(listOf(AttendanceReward.PermanentFeature(FeatureId.Undo)), rewards)
        assertTrue(rewards.none { it is AttendanceReward.BotCharacterUnlock })
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
        // 4일차에는 소모품과 함께 유일한 출석 캐릭터가 걸린다(#16).
        assertEquals(
            listOf(
                AttendanceReward.Consumable(ConsumableCatalog.PremiumOnce, 10),
                AttendanceReward.BotCharacterUnlock(attendanceCharacter),
            ),
            AttendanceRewardPolicy.rewardsFor(4),
        )
    }

    @Test
    fun dayFiveIsEmptyNowThatItsCharacterMovedToDayFour() {
        // 5일차 캐릭터가 4일차로 옮겨져 이 회차는 콘텐츠가 비었다 — #19가 소모품으로 채운다.
        // 빈 회차는 claimedTiers에 들어가지 않으므로, 그때 지나간 사용자도 나중에 받을 수 있다.
        assertEquals(emptyList(), AttendanceRewardPolicy.rewardsFor(5))
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

        // 5일차는 캐릭터가 4일차로 옮겨가며 콘텐츠가 비어(#16) 대기 목록에 뜨지 않는다.
        // #19가 그 회차를 소모품으로 채우면 다시 [2, 4, 5]가 된다.
        assertEquals(listOf(2, 4), AttendanceRewardPolicy.pendingTiers(state).map { it.tier })
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
    fun firstEverCheckInGrantsUndoAndNoCharacter() {
        val stores = RewardStores()
        val checkIn = stores.checkInAt(0L)

        val result = stores.grant(checkIn.state)

        assertTrue(result.didGrant)
        assertEquals(listOf(1), result.granted.map { it.tier })
        assertEquals(1, result.grantedRewards.size)
        assertTrue(stores.attendance.stored.isTierClaimed(UndoUnlimitedRewardTier))
        assertEquals(setOf(FeatureId.Undo), stores.premium.stored.claimedFeatures)
        // 1단계는 기본 제공이라 지급 기록이 남지 않는다 — 그래도 고를 수는 있다.
        assertFalse(stores.bots.stored.isClaimed(defaultCharacter.id))
        assertTrue(stores.bots.stored.isAvailable(defaultCharacter))
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
    fun theAttendanceCharacterArrivesOnDayFourAndNotBefore() {
        val stores = RewardStores()

        stores.attendForDays(3)
        assertFalse(stores.bots.stored.isClaimed(attendanceCharacter.id))

        stores.attendForDays(1)
        assertTrue(stores.bots.stored.isClaimed(attendanceCharacter.id))
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

        // 5일차는 콘텐츠가 비어 지급 목록에도 claimedTiers에도 들어가지 않는다(#16 이후).
        assertEquals(listOf(1, 2, 3, 4), result.granted.map { it.tier })
        assertEquals(setOf(FeatureId.Undo), stores.premium.stored.claimedFeatures)
        assertEquals(10, stores.consumables.stored.countOf(ConsumableCatalog.EvalOnce.id))
        assertTrue(stores.bots.stored.isClaimed(attendanceCharacter.id))
        assertEquals(setOf(1, 2, 3, 4), stores.attendance.stored.claimedTiers)
    }

    @Test
    fun undecidedTiersAreNotMarkedClaimedSoTheirRewardsSurviveALaterDecision() {
        // 콘텐츠가 없는 회차는 claimedTiers에 들어가면 안 된다 — 나중에 콘텐츠가 정해졌을 때
        // 그 사이 지나간 사용자가 영영 못 받는 일이 없어야 한다. #16 이후로는 6·7일차뿐 아니라
        // **5일차도** 여기 해당한다(캐릭터가 4일차로 옮겨가며 비었다). #19가 채우면 다시 잡힌다.
        val stores = RewardStores(AttendanceState(attendanceCount = 7, lastCheckInUtcDay = 6L))

        stores.grant()

        assertEquals(setOf(1, 2, 3, 4), stores.attendance.stored.claimedTiers)
        assertFalse(stores.attendance.stored.isTierClaimed(5))
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
