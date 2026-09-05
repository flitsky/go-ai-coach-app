package com.worksoc.goaicoach.application.attendance

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import com.worksoc.goaicoach.application.botcharacter.TopCharacterAttendanceTier
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.PremiumOnceMaxStock
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
    /** 픽스처를 통해 만든 저장소에 시작 상태를 심는다(#68 테스트) — 생성자 인자를 못 쓰는 자리용. */
    fun seed(state: BotCollectionState) {
        save(state)
    }

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

// 출석으로 열리는 캐릭터는 3단계(도장생 반상, 4일차)와 5단계(관장 천원, 28일차) 둘이다 — 1단계는 기본 제공이고
// 2·4단계는 광고 조각, 5단계는 유료다(7장 재확정본, #16). 카탈로그가 단일 출처이므로 여기서도
// 인덱스가 아니라 카탈로그 조회로 가져와, 표가 또 바뀌면 이 픽스처가 자동으로 따라가게 한다.
// ⚠️ 4일차 → 7일차로 옮겼다(#55). 회차를 회차 상수로 부르는 이유는, 다음에 또 옮길 때
// 이 줄이 조용히 빈 리스트를 `single()`하며 초기화 단계에서 터지지 않게 하기 위함이다.
private val attendanceCharacter = BotCharacterCatalog.forAttendanceTier(WeeklyRewardCycleTier).single()
private val shardCharacters = BotCharacterCatalog.shardPathCharacters()
private val defaultCharacter = BotCharacterCatalog.fastBeginnerRoster.first()

class AttendanceRewardPolicyTest {

    @Test
    fun undoArrivesOnDayThreeAfterTheUserHasFeltItIsPaid() {
        // ⚠️ **1일차가 아니라 3일차다**(#55, 2026-08-31 확정표). 무르기는 1회권이 아예 없어서
        // 1·2일차에 쓰려면 광고를 봐야 한다 — 그 이틀을 겪은 뒤에 줘야 "공짜로 받았다"가 아니라
        // "값나가는 걸 받았다"가 된다. 순서를 앞으로 당기면 그 학습이 통째로 사라진다.
        assertEquals(
            listOf(AttendanceReward.Consumable(ConsumableCatalog.EvalOnce, 30)),
            AttendanceRewardPolicy.rewardsFor(1),
        )
        assertEquals(
            listOf(AttendanceReward.PermanentFeature(FeatureId.Undo)),
            AttendanceRewardPolicy.rewardsFor(UndoUnlimitedRewardTier),
        )
        assertEquals(3, UndoUnlimitedRewardTier)
    }

    @Test
    fun everyDayOfTheWeekMatchesTheConfirmedTable() {
        // 2026-08-31 확정표 전수. 지급량과 **순서**가 조용히 바뀌지 않게 그대로 고정한다.
        assertEquals(
            listOf(AttendanceReward.Consumable(ConsumableCatalog.EvalOnce, 30)),
            AttendanceRewardPolicy.rewardsFor(1),
        )
        assertEquals(
            listOf(AttendanceReward.Consumable(ConsumableCatalog.TopMovesOnce, 30)),
            AttendanceRewardPolicy.rewardsFor(2),
        )
        assertEquals(
            listOf(AttendanceReward.PermanentFeature(FeatureId.Undo)),
            AttendanceRewardPolicy.rewardsFor(3),
        )
        assertEquals(
            listOf(AttendanceReward.Consumable(ConsumableCatalog.PremiumOnce, 3)),
            AttendanceRewardPolicy.rewardsFor(4),
        )
        // 5·6일차 조각은 "캐릭터는 광고를 봐야 모인다"를 알리는 맛보기다.
        assertEquals(
            listOf(AttendanceReward.BotCharacterShards(shardCharacters[0], 1)),
            AttendanceRewardPolicy.rewardsFor(5),
        )
        assertEquals(
            listOf(AttendanceReward.BotCharacterShards(shardCharacters[1], 1)),
            AttendanceRewardPolicy.rewardsFor(6),
        )
        // ⚠️ 7일차는 **캐릭터뿐이다** — 소모품을 얹지 않는다(확정표).
        assertEquals(
            listOf(AttendanceReward.BotCharacterUnlock(attendanceCharacter)),
            AttendanceRewardPolicy.rewardsFor(7),
        )
        // 조각 경로 캐릭터가 누구이고 **어느 순서인지**도 고정한다 — 5·6일차가 이 순서에 기댄다.
        assertEquals(listOf("fast_beginner_2", "fast_beginner_4"), shardCharacters.map { it.id.raw })
    }

    @Test
    fun shardCharactersCannotBeFinishedByAttendanceAloneOnPurpose() {
        // ⚠️ **의도된 설계다(2026-08-31 사용자 확정).** 예전에는 7일차마다 조각이 나와 광고 없이도
        // 언젠가 닿을 수 있었는데, 지금은 5·6일차에 **한 개씩**이 전부다. "반복 회차에 조각을
        // 얹어 느린 무광고 경로를 두자"는 제안은 기각됐다 — 그 시점이면 광고로 이미 확보한
        // 사용자가 많고, 간격이 벌어져 경로 구실을 못 한다. 되살리려면 그 결정부터 뒤집을 것.
        val fromAttendance = (1..100)
            .flatMap(AttendanceRewardPolicy::rewardsFor)
            .filterIsInstance<AttendanceReward.BotCharacterShards>()

        shardCharacters.forEach { character ->
            val granted = fromAttendance.filter { it.character == character }.sumOf { it.amount }
            val required = (character.unlockSource as BotUnlockSource.AdShards).required
            assertEquals(1, granted, "${character.id.raw}: 출석으로 나오는 조각은 1개뿐이어야 한다")
            assertTrue(granted < required, "${character.id.raw}")
        }
    }

    /**
     * **획득 사실이 결과에 실려야 한다**(백로그 #68). 축전 팝업(#69)이 `granted`가 아니라
     * [AttendanceRewardGrantResult.acquiredCharacters]로 구동되는 근거다.
     *
     * ⚠️ 즉시 해금 회차(7일차)에서 `granted`만 보면 *"캐릭터 획득"* 이라는 **보상 줄**은 있지만
     * 그것이 **이번에 실제로 일어났는지**는 알 수 없다 — 이미 보유해도 같은 줄이 남았었다.
     */
    @Test
    fun anImmediateUnlockTierReportsTheCharacterItActuallyGranted() {
        val stores = RewardStores()

        val result = stores.grant(
            AttendanceState(
                attendanceCount = WeeklyRewardCycleTier,
                claimedTiers = (1 until WeeklyRewardCycleTier).toSet(),
            ),
        )

        assertEquals(listOf(attendanceCharacter), result.acquiredCharacters)
        assertTrue(result.didAcquireCharacter)
        assertTrue(stores.bots.stored.isClaimed(attendanceCharacter.id))
    }

    /**
     * ⚠️ **유령 보상**(#68에서 닫았다). 이미 보유한 캐릭터 회차는 축전도, Claim 목록의 줄도
     * 남기지 않아야 한다 — 예전에는 `grant`가 무조건 `true`를 돌려줘 **팝업만 뜨고 아무 일도
     * 일어나지 않았다.**
     *
     * ⚠️ **오늘 이 상태는 도달 불가다**(출석 해금 캐릭터는 출석 외 획득 경로가 없다). 그래서
     * 이 테스트는 **미래를 위한 그물**이다 — 구매(#74)나 개발자 도구(#70)가 캐릭터를 심을 수
     * 있게 되는 순간 도달 가능해진다.
     * ⚠️ 그때 **7·28일차는 캐릭터가 유일한 보상이라 그 회차가 통째로 빈손이 된다** — 대체 보상은
     * 별도 사용자 결정이므로(`GOOGLE_PLAY_LAUNCH_PLAN.md` C-2 상세) 여기서 정하지 않는다.
     */
    @Test
    fun aCharacterTierAlreadyOwnedAnnouncesNothingAndCelebratesNothing() {
        val stores = RewardStores()
        stores.bots.seed(BotCollectionState().withClaimed(attendanceCharacter.id))

        val result = stores.grant(
            AttendanceState(
                attendanceCount = WeeklyRewardCycleTier,
                claimedTiers = (1 until WeeklyRewardCycleTier).toSet(),
            ),
        )

        assertEquals(emptyList(), result.acquiredCharacters, "이미 가진 캐릭터를 축하하고 있다(유령 보상).")
        assertTrue(
            result.grantedRewards.none { it is AttendanceReward.BotCharacterUnlock },
            "이미 가진 캐릭터가 Claim 목록에 남아 있다 — 팝업만 뜨고 아무 일도 일어나지 않는 줄이다.",
        )
    }

    /**
     * **조각이 마지막 한 개를 채워 캐릭터가 된 순간**도 획득이다(#68). 이 판정은 5계층
     * (`BotCharacterShardGrant.unlocked`)에만 있었고 결과에서 버려지고 있었다 — `granted`에는
     * *"조각 1개"* 로만 남아, 화면이 그 사실을 알 방법이 없었다.
     */
    @Test
    fun aShardTierThatCompletesTheSetReportsTheCharacterAsAcquired() {
        val shardCharacter = shardCharacters.first { character ->
            (character.unlockSource as? BotUnlockSource.AdShards)?.required != null
        }
        val required = (shardCharacter.unlockSource as BotUnlockSource.AdShards).required
        val stores = RewardStores()
        // 한 개만 남겨 둔다 — 5일차 조각 1개가 그 마지막 한 개가 된다.
        stores.bots.seed(BotCollectionState(adShards = mapOf(shardCharacter.id to required - 1)))

        val result = stores.grant(AttendanceState(attendanceCount = 5, claimedTiers = (1..4).toSet()))

        assertEquals(listOf(shardCharacter), result.acquiredCharacters)
        assertTrue(stores.bots.stored.isClaimed(shardCharacter.id))
    }

    /** 반대로 **진행도만 오른 회차는 획득이 아니다** — 축전을 띄우면 안 된다. */
    @Test
    fun aShardTierThatOnlyAdvancesProgressAcquiresNothing() {
        val stores = RewardStores()

        val result = stores.grant(AttendanceState(attendanceCount = 5, claimedTiers = (1..4).toSet()))

        assertTrue(result.grantedRewards.any { it is AttendanceReward.BotCharacterShards }, "조각은 지급됐어야 한다.")
        assertEquals(emptyList(), result.acquiredCharacters, "진행도만 올랐는데 획득으로 보고했다.")
        assertFalse(result.didAcquireCharacter)
    }

    /**
     * ⚠️ **밀린 회차가 한 번에 지급되면 캐릭터를 둘 이상 동시에 획득할 수 있다**(7일차·28일차).
     * 축전 팝업이 그 경우를 어떻게 보일지는 #69의 결정 사항이고, 여기서는 **결과가 둘 다 싣는지**만
     * 고정한다 — 하나만 실으면 그 결정을 내릴 재료 자체가 없어진다.
     */
    @Test
    fun catchingUpAcrossBothCharacterTiersReportsBothInGrantOrder() {
        val stores = RewardStores()

        val result = stores.grant(AttendanceState(attendanceCount = TopCharacterAttendanceTier))

        val expected = BotCharacterCatalog.forAttendanceTier(WeeklyRewardCycleTier) +
            BotCharacterCatalog.forAttendanceTier(TopCharacterAttendanceTier)
        assertEquals(expected, result.acquiredCharacters)
    }

    @Test
    fun shardsAlreadyCompletedAreNotAnnouncedAgain() {
        // 이미 다 모은 캐릭터의 조각 줄은 팝업에 적지 않는다 — 의미 없는 줄이 남는다.
        // ⚠️ 관측 지점이 7일차 → **5일차**로 옮겨졌다(확정표에서 조각이 5·6일차로 갔다).
        val botStore = FakeBotStore(
            shardCharacters.fold(BotCollectionState()) { state, character -> state.withClaimed(character.id) },
        )

        val result = runAttendanceRewardGrant(
            state = AttendanceState(attendanceCount = 6, claimedTiers = (1..4).toSet()),
            attendanceStore = FakeAttendanceStore(),
            premiumStore = FakePremiumStore(),
            consumableStore = FakeConsumableStore(),
            botStore = botStore,
        )

        assertTrue(result.grantedRewards.none { it is AttendanceReward.BotCharacterShards })
    }

    @Test
    fun weeklyRepeatsSkipTheCharacterTiers() {
        // ⚠️ **여기가 이번 개편에서 가장 조용히 깨질 뻔한 곳이다.** 예전 구현은
        // `contentTier = if (tier > 7) 7 else tier`로 접어서 14·21·35…가 7일차 내용을 그대로 썼다.
        // 지금 7일차는 **캐릭터뿐**이라 그대로 접으면 14일차에 이미 가진 캐릭터만 나오고
        // 소모품이 통째로 사라진다. 그래서 캐릭터 회차(7·28)와 반복 회차를 분리했다.
        val repeatBundle = listOf(
            AttendanceReward.Consumable(ConsumableCatalog.EvalOnce, 50),
            AttendanceReward.Consumable(ConsumableCatalog.TopMovesOnce, 50),
            AttendanceReward.Consumable(ConsumableCatalog.PremiumOnce, 3),
        )

        listOf(14, 21, 35, 42).forEach { tier ->
            assertTrue(isRewardedTier(tier), "tier $tier must be a rewarded tier")
            assertEquals(repeatBundle, AttendanceRewardPolicy.rewardsFor(tier), "tier $tier")
        }
        // 캐릭터가 걸린 두 회차는 소모품 없이 캐릭터만 준다.
        assertEquals(
            listOf(AttendanceReward.BotCharacterUnlock(attendanceCharacter)),
            AttendanceRewardPolicy.rewardsFor(7),
        )
        assertEquals(
            listOf(AttendanceReward.BotCharacterUnlock(BotCharacterCatalog.forAttendanceTier(28).single())),
            AttendanceRewardPolicy.rewardsFor(28),
        )
        // 반복 회차는 캐릭터를 다시 주지 않는다.
        assertTrue(repeatBundle.none { it is AttendanceReward.BotCharacterUnlock })
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
    fun theSkipTicketIsTheStockThatOverflowsNow() {
        // ⚠️ 넘치는 종류가 **바뀌었다.** 예전 표는 한 주에 형세를 110개(30+30+50) 줘서 상한 99를
        // 넘겼지만, 지금 첫 주 형세는 1일차 30개뿐이라 넘치지 않는다. 대신 광고 스킵권 상한이
        // 9로 낮아지면서 4일차 3 + 14일차 3 + 21일차 3 = **정확히 9**가 되고, 35일차부터
        // 매 반복마다 넘친다 — "쌓지 말고 쓰라"는 의도된 갈증이다.
        val throughThreeWeeks = (1..21)
            .flatMap(AttendanceRewardPolicy::rewardsFor)
            .filterIsInstance<AttendanceReward.Consumable>()
            .filter { it.item == ConsumableCatalog.PremiumOnce }
            .sumOf { it.amount }

        assertEquals(PremiumOnceMaxStock, throughThreeWeeks)
        // 그 다음 반복은 통째로 버려진다 — 이 사실을 팝업이 감춰선 안 된다(#55 ⓑ).
        val fullStock = ConsumableInventory().withGranted(ConsumableCatalog.PremiumOnce.id, PremiumOnceMaxStock)
        assertEquals(0, fullStock.grantableAmount(ConsumableCatalog.PremiumOnce.id, 3))
        assertTrue(fullStock.isAtMaxStock(ConsumableCatalog.PremiumOnce.id))
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
    fun firstEverCheckInGrantsEvalTicketsAndNoCharacter() {
        val stores = RewardStores()
        val checkIn = stores.checkInAt(0L)

        val result = stores.grant(checkIn.state)

        assertTrue(result.didGrant)
        assertEquals(listOf(1), result.granted.map { it.tier })
        assertEquals(30, stores.consumables.stored.countOf(ConsumableCatalog.EvalOnce.id))
        // ⚠️ 무르기는 아직이다 — 3일차에 온다(#55). 첫날에 주면 "유료임을 겪는" 이틀이 사라진다.
        assertTrue(stores.premium.stored.claimedFeatures.isEmpty())
        assertFalse(stores.attendance.stored.isTierClaimed(UndoUnlimitedRewardTier))
        // 1단계는 기본 제공이라 지급 기록이 남지 않는다 — 그래도 고를 수는 있다.
        assertFalse(stores.bots.stored.isClaimed(defaultCharacter.id))
        assertTrue(stores.bots.stored.isAvailable(defaultCharacter))
    }

    @Test
    fun grantedUndoResolvesAsAllowedForAFreeUser() {
        val stores = RewardStores()
        // 3일차까지 출석해야 무르기가 열린다(#55).
        stores.attendForDays(UndoUnlimitedRewardTier)

        val access = FeatureAccessPolicy.resolve(FeatureId.Undo, stores.premium.stored, nowMillis = 0L)
        assertIs<FeatureAccess.Allowed>(access)
    }

    @Test
    fun consumableDaysStockTheInventoryPerTheTable() {
        val stores = RewardStores()

        stores.attendForDays(4)

        // 1일차 형세30, 2일차 추천30, 3일차 무르기(소모품 아님), 4일차 스킵권3.
        assertEquals(30, stores.consumables.stored.countOf(ConsumableCatalog.EvalOnce.id))
        assertEquals(30, stores.consumables.stored.countOf(ConsumableCatalog.TopMovesOnce.id))
        assertEquals(3, stores.consumables.stored.countOf(ConsumableCatalog.PremiumOnce.id))
    }

    @Test
    fun theFirstWeekStaysWellUnderEveryCap() {
        val stores = RewardStores()

        stores.attendForDays(7)

        // 확정표의 첫 주는 형세 30 / 추천 30 / 스킵 3뿐이다 — 어느 것도 상한에 닿지 않는다.
        // 갈증은 반복 회차에서 온다(위 `theSkipTicketIsTheStockThatOverflowsNow` 참고).
        assertEquals(30, stores.consumables.stored.countOf(ConsumableCatalog.EvalOnce.id))
        assertEquals(30, stores.consumables.stored.countOf(ConsumableCatalog.TopMovesOnce.id))
        assertEquals(3, stores.consumables.stored.countOf(ConsumableCatalog.PremiumOnce.id))
    }

    @Test
    fun theAttendanceCharacterArrivesOnDaySevenAndNotBefore() {
        val stores = RewardStores()

        // ⚠️ 4일차 → 7일차로 옮겼다(#55). 1~6일차 행은 소모품·기능만 남기기 위함이다.
        stores.attendForDays(6)
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
        // 형세는 1일차 30뿐이고, 스킵권은 4일차 3, 조각은 5일차 1개다.
        assertEquals(30, stores.consumables.stored.countOf(ConsumableCatalog.EvalOnce.id))
        assertEquals(3, stores.consumables.stored.countOf(ConsumableCatalog.PremiumOnce.id))
        // ⚠️ 캐릭터는 7일차라 아직 오지 않는다.
        assertFalse(stores.bots.stored.isClaimed(attendanceCharacter.id))
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

        stores.attendForDays(UndoUnlimitedRewardTier)

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
