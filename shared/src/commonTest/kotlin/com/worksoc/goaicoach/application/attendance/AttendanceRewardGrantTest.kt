package com.worksoc.goaicoach.application.attendance

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
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
private val shardCharacters = BotCharacterCatalog.shardPathCharacters()
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
    fun everyDayOfTheWeekMatchesTheConfirmedTable() {
        // 4.2절 재확정본(2026-08-24) 전수. 지급량이 조용히 바뀌지 않게 표 그대로 고정한다.
        assertEquals(
            listOf(AttendanceReward.PermanentFeature(FeatureId.Undo)),
            AttendanceRewardPolicy.rewardsFor(1),
        )
        listOf(2, 5).forEach { tier ->
            assertEquals(
                listOf(
                    AttendanceReward.Consumable(ConsumableCatalog.EvalOnce, 30),
                    AttendanceReward.Consumable(ConsumableCatalog.TopMovesOnce, 30),
                ),
                AttendanceRewardPolicy.rewardsFor(tier),
                "tier $tier",
            )
        }
        assertEquals(
            listOf(AttendanceReward.Consumable(ConsumableCatalog.PremiumOnce, 3)),
            AttendanceRewardPolicy.rewardsFor(3),
        )
        // 4일차는 소모품 없이 유일한 출석 캐릭터만 걸린다(#16).
        assertEquals(
            listOf(AttendanceReward.BotCharacterUnlock(attendanceCharacter)),
            AttendanceRewardPolicy.rewardsFor(4),
        )
        assertEquals(
            listOf(AttendanceReward.Consumable(ConsumableCatalog.PremiumOnce, 5)),
            AttendanceRewardPolicy.rewardsFor(6),
        )
        assertEquals(
            listOf(
                AttendanceReward.Consumable(ConsumableCatalog.EvalOnce, 50),
                AttendanceReward.Consumable(ConsumableCatalog.TopMovesOnce, 50),
                AttendanceReward.Consumable(ConsumableCatalog.PremiumOnce, 10),
            ) + shardCharacters.map { AttendanceReward.BotCharacterShards(it, 1) },
            AttendanceRewardPolicy.rewardsFor(7),
        )
        // 조각 경로 캐릭터가 누구인지도 함께 고정한다 — 카탈로그에서 파생시켰기 때문에 위 단언만
        // 두면 캐릭터를 늘리거나 줄여도 통과해 버린다.
        assertEquals(listOf("연습생 돌뫼", "사범 묘수"), shardCharacters.map { it.name })
    }

    @Test
    fun theSeventhDayFeedsShardsSoTheAdPathIsNotTheOnlyWay() {
        // 광고가 채워지지 않는 사용자도 결국 조각 캐릭터에 닿을 수 있어야 한다(2026-08-29).
        // 반복 회차에 걸려 있으므로 회차를 거듭할수록 진행도가 쌓인다.
        val botStore = FakeBotStore()
        val target = shardCharacters.first()
        val required = (target.unlockSource as BotUnlockSource.AdShards).required

        repeat(required) { round ->
            val tier = 7 * (round + 1)
            runAttendanceRewardGrant(
                state = AttendanceState(attendanceCount = tier, claimedTiers = (1 until tier).toSet()),
                attendanceStore = FakeAttendanceStore(),
                premiumStore = FakePremiumStore(),
                consumableStore = FakeConsumableStore(),
                botStore = botStore,
            )
        }

        assertTrue(botStore.stored.isClaimed(target.id), "${target.name} must unlock without any ad")
    }

    @Test
    fun shardsAlreadyCompletedAreNotAnnouncedAgain() {
        // 조각은 7일차마다 영원히 반복된다 — 다 모은 캐릭터의 몫까지 팝업에 적으면 그 사용자는
        // 매주 의미 없는 줄을 보게 된다.
        val botStore = FakeBotStore(
            shardCharacters.fold(BotCollectionState()) { state, character -> state.withClaimed(character.id) },
        )

        val result = runAttendanceRewardGrant(
            state = AttendanceState(attendanceCount = 7, claimedTiers = (1..6).toSet()),
            attendanceStore = FakeAttendanceStore(),
            premiumStore = FakePremiumStore(),
            consumableStore = FakeConsumableStore(),
            botStore = botStore,
        )

        assertTrue(result.grantedRewards.none { it is AttendanceReward.BotCharacterShards })
        // 같은 회차의 소모품은 그대로 지급되고 그대로 알린다.
        assertEquals(3, result.grantedRewards.size)
    }

    @Test
    fun multiplesOfSevenRepeatTheSeventhDayExactly() {
        val week = AttendanceRewardPolicy.rewardsFor(7)

        listOf(14, 21, 28).forEach { tier ->
            assertTrue(isRewardedTier(tier), "tier $tier must be a rewarded tier")
            assertEquals(week, AttendanceRewardPolicy.rewardsFor(tier), "tier $tier")
        }
        // 반복 회차가 캐릭터를 **영구 획득**으로 다시 주지는 않는다 — 7일차에 걸린 것은
        // 조각(진행도)뿐이고, 그마저 다 모은 캐릭터는 지급 단계에서 걸러진다.
        assertTrue(week.none { it is AttendanceReward.BotCharacterUnlock })
    }

    @Test
    fun nonRewardedTiersStayEmpty() {
        // 8~13처럼 7의 배수가 아닌 회차는 애초에 보상 회차가 아니다(4.1절).
        listOf(8, 9, 13, 15, 20).forEach { tier ->
            assertFalse(isRewardedTier(tier), "tier $tier")
            assertTrue(AttendanceRewardPolicy.rewardsFor(tier).isEmpty(), "tier $tier")
        }
    }

    @Test
    fun noRewardedTierIsEmptyAnyMore() {
        // #19로 1~7일차가 모두 채워졌다. "빈 회차는 claimedTiers에 안 넣는다"는 안전장치는
        // 코드에 남아 있지만, 지금은 발동할 회차가 없다.
        (1..7).forEach { tier ->
            assertTrue(AttendanceRewardPolicy.rewardsFor(tier).isNotEmpty(), "tier $tier")
        }
    }

    @Test
    fun aFullWeekOverflowsTheEvalStockOnPurpose() {
        // 한 주기 형세 보기 지급량은 30 + 30 + 50 = 110으로 재고 상한 99를 넘는다.
        // 넘치는 만큼 버려지는 것이 의도다(소모 유도, 2026-08-24 확정) — 상한을 올리려면
        // ConsumableInventory.MaxPerItem 한 줄만 고치면 되도록 유지한다.
        val weekly = (1..7)
            .flatMap(AttendanceRewardPolicy::rewardsFor)
            .filterIsInstance<AttendanceReward.Consumable>()
            .filter { it.item == ConsumableCatalog.EvalOnce }
            .sumOf { it.amount }

        assertEquals(110, weekly)
        assertTrue(weekly > ConsumableInventory.MaxPerItem)
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
    fun pendingTiersNowIncludesTheRestOfTheWeek() {
        // #19 전에는 6·7일차가 비어 목록에서 빠졌다. 이제는 둘 다 콘텐츠가 있어 잡힌다.
        val state = AttendanceState(attendanceCount = 7, claimedTiers = setOf(1, 2, 3, 4, 5))

        assertEquals(listOf(6, 7), AttendanceRewardPolicy.pendingTiers(state).map { it.tier })
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
    fun consumableDaysStockTheInventoryPerTheTable() {
        val stores = RewardStores()

        stores.attendForDays(4)

        // 2일차 형세30+추천30, 3일차 스킵권3, 4일차는 캐릭터뿐.
        assertEquals(30, stores.consumables.stored.countOf(ConsumableCatalog.EvalOnce.id))
        assertEquals(30, stores.consumables.stored.countOf(ConsumableCatalog.TopMovesOnce.id))
        assertEquals(3, stores.consumables.stored.countOf(ConsumableCatalog.PremiumOnce.id))
    }

    @Test
    fun aFullWeekClipsTheEvalStockAtTheCap() {
        val stores = RewardStores()

        stores.attendForDays(7)

        // 지급량 110이지만 상한 99에서 잘린다 — 넘치는 11개는 의도적으로 버려진다.
        assertEquals(ConsumableInventory.MaxPerItem, stores.consumables.stored.countOf(ConsumableCatalog.EvalOnce.id))
        assertEquals(ConsumableInventory.MaxPerItem, stores.consumables.stored.countOf(ConsumableCatalog.TopMovesOnce.id))
        // 스킵권은 3 + 5 + 10 = 18로 상한에 닿지 않는다.
        assertEquals(18, stores.consumables.stored.countOf(ConsumableCatalog.PremiumOnce.id))
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
        assertEquals(30, stores.consumables.stored.countOf(ConsumableCatalog.EvalOnce.id))
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
        // 2일차 30 + 5일차 30.
        assertEquals(60, stores.consumables.stored.countOf(ConsumableCatalog.EvalOnce.id))
        assertTrue(stores.bots.stored.isClaimed(attendanceCharacter.id))
        assertEquals(setOf(1, 2, 3, 4, 5), stores.attendance.stored.claimedTiers)
    }

    @Test
    fun tiersWithNoRewardAreNotMarkedClaimed() {
        // 보상 목록이 빈 회차는 claimedTiers에 들어가면 안 된다 — 나중에 콘텐츠가 정해졌을 때
        // 그 사이 지나간 사용자가 영영 못 받는 일이 없어야 한다(#13 구현 결정 3번).
        //
        // #19로 1~7일차가 모두 채워져 "미확정 회차"는 사라졌지만, 이 안전장치 자체는 **7의 배수가
        // 아닌 8~13일차**에서 여전히 관측된다 — 그쪽은 애초에 보상 회차가 아니라 빈 목록이다.
        val stores = RewardStores(AttendanceState(attendanceCount = 13, lastCheckInUtcDay = 12L))

        stores.grant()

        assertEquals((1..7).toSet(), stores.attendance.stored.claimedTiers)
        (8..13).forEach { tier ->
            assertFalse(stores.attendance.stored.isTierClaimed(tier), "tier $tier")
        }
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
