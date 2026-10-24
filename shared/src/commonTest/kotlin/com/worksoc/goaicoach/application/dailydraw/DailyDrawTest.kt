package com.worksoc.goaicoach.application.dailydraw

import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import com.worksoc.goaicoach.application.botcharacter.TopCharacterAttendanceTier
import com.worksoc.goaicoach.application.botcharacter.WeeklyRewardCycleTier
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableStorePort
import com.worksoc.goaicoach.application.consumable.PremiumOnceMaxStock
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val T0 = 1_700_000_000_000L
private const val Hour = 60L * 60 * 1000

private class MemoryConsumables(var inventory: ConsumableInventory = ConsumableInventory()) : ConsumableStorePort {
    override fun save(inventory: ConsumableInventory) {
        this.inventory = inventory
    }

    override fun load(): ConsumableInventory = inventory
}

private class MemoryCollection(var state: BotCollectionState = BotCollectionState()) : BotCollectionStorePort {
    override fun save(state: BotCollectionState) {
        this.state = state
    }

    override fun load(): BotCollectionState = state
}

private class MemoryDraws(var last: Long? = null) : DailyDrawStorePort {
    override fun lastDrawnAtMillis(): Long? = last

    override fun markDrawn(atMillis: Long) {
        last = atMillis
    }
}

/** 언제나 [value]를 내는 난수 — 뽑기의 칸을 정해 놓고 지급을 본다. */
private class FixedRandom(private val value: Int) : Random() {
    override fun nextBits(bitCount: Int): Int = 0

    override fun nextInt(until: Int): Int = value.coerceIn(0, until - 1)
}

/** 백로그 #283 · #289 — 실험실의 1일 1회 뽑기(사용자 2026-10-23 · 10-24). */
class DailyDrawTest {
    private val shardPath: List<BotCharacter> = BotCharacterCatalog.all
        .filter { it.unlockSource is BotUnlockSource.AdShards }
        .sortedBy { it.tierWithinGroup }

    private val seniorPupil: BotCharacter = BotCharacterCatalog.forAttendanceTier(WeeklyRewardCycleTier).single()
    private val master: BotCharacter = BotCharacterCatalog.forAttendanceTier(TopCharacterAttendanceTier).single()
    private val allShardPathOwned: BotCollectionState
        get() = shardPath.fold(BotCollectionState()) { state, c -> state.withClaimed(c.id) }

    /** #289(사용자 2026-10-24) — 30 · 30 · 30 · 7 · 2 · 1. */
    @Test
    fun theOddsAreThirtyThirtyThirtySevenTwoOne() {
        val odds = dailyDrawOdds(ConsumableInventory(), BotCollectionState())
        assertEquals(
            mapOf(
                DailyDrawPrizeKind.CharacterShard to 30,
                DailyDrawPrizeKind.Tickets to 30,
                DailyDrawPrizeKind.AdSkipTicket to 30,
                DailyDrawPrizeKind.Character to 7,
                DailyDrawPrizeKind.Day7Character to 2,
                DailyDrawPrizeKind.Day28Character to 1,
            ),
            odds,
        )
        assertEquals(100, odds.values.sum())
        assertEquals(seniorPupil, dailyDrawPrizeCharacter(DailyDrawPrizeKind.Day7Character, BotCollectionState()))
        assertEquals(master, dailyDrawPrizeCharacter(DailyDrawPrizeKind.Day28Character, BotCollectionState()))
    }

    @Test
    fun charactersGoToTheLowestUnownedShardPathCharacterOnly() {
        assertTrue(shardPath.size >= 2, "조각 길 캐릭터가 둘이라는 전제(2 · 4단계)가 바뀌었다.")
        assertEquals(shardPath[0], dailyDrawCharacterTarget(BotCollectionState()))
        val lowestOwned = BotCollectionState().withClaimed(shardPath[0].id)
        assertEquals(shardPath[1], dailyDrawCharacterTarget(lowestOwned))
        // 출석 캐릭터는 광고 캐릭터 · 조각의 대상이 아니다(따로 2 · 1%) — 조각 길을 다 가지면 대상이 없다.
        assertNull(dailyDrawCharacterTarget(allShardPathOwned))
    }

    /** 90% 묶음은 남은 것끼리 90%를 나누고, 캐릭터 셋은 가졌어도 7 · 2 · 1로 남는다(대체 지급, #289). */
    @Test
    fun theCommonShareIsSplitAmongWhatCanBeGivenWhileCharacterPrizesStayFixed() {
        val skipFull = ConsumableInventory().withGranted(ConsumableCatalog.PremiumOnce.id, PremiumOnceMaxStock)
        val everythingOwned = allShardPathOwned.withClaimed(seniorPupil.id).withClaimed(master.id)
        val characters = mapOf(
            DailyDrawPrizeKind.Character to 7,
            DailyDrawPrizeKind.Day7Character to 2,
            DailyDrawPrizeKind.Day28Character to 1,
        )
        assertEquals(
            mapOf(DailyDrawPrizeKind.Tickets to 45, DailyDrawPrizeKind.AdSkipTicket to 45) + characters,
            dailyDrawOdds(ConsumableInventory(), everythingOwned),
        )
        assertEquals(mapOf(DailyDrawPrizeKind.Tickets to 90) + characters, dailyDrawOdds(skipFull, allShardPathOwned))
        assertEquals(
            mapOf(DailyDrawPrizeKind.CharacterShard to 45, DailyDrawPrizeKind.Tickets to 45) + characters,
            dailyDrawOdds(skipFull, BotCollectionState()),
        )
    }

    @Test
    fun theRollLandsInTheStackedWeights() {
        val odds = dailyDrawOdds(ConsumableInventory(), BotCollectionState())
        assertEquals(DailyDrawPrizeKind.CharacterShard, pickDailyDrawPrize(odds, 0))
        assertEquals(DailyDrawPrizeKind.CharacterShard, pickDailyDrawPrize(odds, 29))
        assertEquals(DailyDrawPrizeKind.Tickets, pickDailyDrawPrize(odds, 30))
        assertEquals(DailyDrawPrizeKind.AdSkipTicket, pickDailyDrawPrize(odds, 89))
        assertEquals(DailyDrawPrizeKind.Character, pickDailyDrawPrize(odds, 90))
        assertEquals(DailyDrawPrizeKind.Character, pickDailyDrawPrize(odds, 96))
        assertEquals(DailyDrawPrizeKind.Day7Character, pickDailyDrawPrize(odds, 97))
        assertEquals(DailyDrawPrizeKind.Day7Character, pickDailyDrawPrize(odds, 98))
        assertEquals(DailyDrawPrizeKind.Day28Character, pickDailyDrawPrize(odds, 99))
    }

    @Test
    fun theCooldownRunsADayFromTheDraw() {
        assertEquals(DailyDrawAvailability.Ready, dailyDrawAvailability(null, T0))
        assertEquals(DailyDrawAvailability.Cooldown(24 * Hour), dailyDrawAvailability(T0, T0))
        assertEquals(DailyDrawAvailability.Cooldown(1), dailyDrawAvailability(T0, T0 + 24 * Hour - 1))
        assertEquals(DailyDrawAvailability.Ready, dailyDrawAvailability(T0, T0 + 24 * Hour))
        // 시계를 되감아 마지막 뽑기가 미래면 막지 않는다.
        assertEquals(DailyDrawAvailability.Ready, dailyDrawAvailability(T0 + Hour, T0))
    }

    @Test
    fun eachPrizeIsGrantedAndTheCooldownStarts() {
        // 1회권: 형세 보기 · 추천 수 각 10장.
        run {
            val consumables = MemoryConsumables()
            val draws = MemoryDraws()
            val result = assertNotNull(runDailyDraw(consumables, MemoryCollection(), draws, T0, FixedRandom(30)))
            assertEquals(DailyDrawPrizeKind.Tickets, result.kind)
            assertEquals(10, consumables.inventory.countOf(ConsumableCatalog.EvalOnce.id))
            assertEquals(10, consumables.inventory.countOf(ConsumableCatalog.TopMovesOnce.id))
            assertEquals(T0, draws.last)
            // 같은 날 두 번째는 없다 — 아무것도 쓰지 않는다.
            assertNull(runDailyDraw(consumables, MemoryCollection(), draws, T0 + Hour, FixedRandom(30)))
            assertEquals(10, consumables.inventory.countOf(ConsumableCatalog.EvalOnce.id))
        }
        // 광고 스킵권 1장.
        run {
            val consumables = MemoryConsumables()
            val result = assertNotNull(runDailyDraw(consumables, MemoryCollection(), MemoryDraws(), T0, FixedRandom(60)))
            assertEquals(DailyDrawPrizeKind.AdSkipTicket, result.kind)
            assertEquals(1, consumables.inventory.countOf(ConsumableCatalog.PremiumOnce.id))
        }
        // 조각 1개 — 가장 낮은 미보유 조각 길 캐릭터에게.
        run {
            val collection = MemoryCollection()
            val result = assertNotNull(runDailyDraw(MemoryConsumables(), collection, MemoryDraws(), T0, FixedRandom(0)))
            assertEquals(DailyDrawPrizeKind.CharacterShard, result.kind)
            assertEquals(shardPath[0], result.character)
            assertEquals(1, result.shards)
            assertEquals(1, collection.state.shardsFor(shardPath[0].id))
        }
        // 광고 캐릭터 · 반상 · 천원 — 그 캐릭터를 곧바로 영구 획득.
        for ((roll, expected) in listOf(95 to shardPath[0], 97 to seniorPupil, 99 to master)) {
            val collection = MemoryCollection()
            val result = assertNotNull(runDailyDraw(MemoryConsumables(), collection, MemoryDraws(), T0, FixedRandom(roll)))
            assertEquals(expected, result.character)
            assertTrue(result.unlocked)
            assertFalse(result.substituted)
            assertTrue(collection.state.isClaimed(expected.id))
        }
    }

    /** 이미 가진 캐릭터 상품은 7일 단위 보상(형세 50 · 추천 50 · 스킵 3)으로 바뀐다(#289). */
    @Test
    fun anOwnedCharacterPrizeIsReplacedByTheWeeklyBundle() {
        val consumables = MemoryConsumables()
        val collection = MemoryCollection(BotCollectionState().withClaimed(seniorPupil.id))
        val result = assertNotNull(runDailyDraw(consumables, collection, MemoryDraws(), T0, FixedRandom(97)))
        assertEquals(DailyDrawPrizeKind.Day7Character, result.kind)
        assertTrue(result.substituted)
        assertFalse(result.unlocked)
        assertEquals(seniorPupil, result.character)
        assertEquals(50, consumables.inventory.countOf(ConsumableCatalog.EvalOnce.id))
        assertEquals(50, consumables.inventory.countOf(ConsumableCatalog.TopMovesOnce.id))
        assertEquals(3, consumables.inventory.countOf(ConsumableCatalog.PremiumOnce.id))
        assertEquals(listOf(50, 50, 3), result.substituteGrants.map { it.amount })
    }

    /** 광고 캐릭터를 다 가졌으면 그 7%도 대체 지급이다 — 이때는 가리킬 캐릭터가 없다. 상한에 걸린 스킵권은 0장으로 적힌다. */
    @Test
    fun theAdCharacterPrizeWithNothingLeftIsReplacedAndReportsWhatActuallyArrived() {
        val skipFull = ConsumableInventory().withGranted(ConsumableCatalog.PremiumOnce.id, PremiumOnceMaxStock)
        val consumables = MemoryConsumables(skipFull)
        // 90% 묶음이 1회권 하나뿐(90)이라 90..96이 광고 캐릭터다.
        val result = assertNotNull(runDailyDraw(consumables, MemoryCollection(allShardPathOwned), MemoryDraws(), T0, FixedRandom(90)))
        assertEquals(DailyDrawPrizeKind.Character, result.kind)
        assertTrue(result.substituted)
        assertNull(result.character)
        assertEquals(listOf(50, 50, 0), result.substituteGrants.map { it.amount })
        assertEquals(PremiumOnceMaxStock, consumables.inventory.countOf(ConsumableCatalog.PremiumOnce.id))
    }

    /**
     * ⚠️ 사용자가 짚은 것(#289): 뽑기로 얻은 캐릭터는 **구독과 상관없이 폰에 저장된다** — 해지한 뒤(구독 없음)에도 고를 수 있다.
     * 뽑기는 구독을 모르고 늘 `claimedBots`에 쓴다. 구독의 「모든 캐릭터」는 판정(`isAvailable`)일 뿐이다.
     */
    @Test
    fun aDrawnCharacterStaysOwnedWithoutASubscription() {
        val collection = MemoryCollection()
        assertTrue(collection.state.isAvailable(master, subscriptionActive = true))
        assertFalse(collection.state.isAvailable(master, subscriptionActive = false))
        assertNotNull(runDailyDraw(MemoryConsumables(), collection, MemoryDraws(), T0, FixedRandom(99)))
        assertTrue(collection.state.isAvailable(master, subscriptionActive = false))
    }

    @Test
    fun theLastMissingShardUnlocksTheCharacter() {
        val target = shardPath[0]
        val required = (target.unlockSource as BotUnlockSource.AdShards).required
        val collection = MemoryCollection(BotCollectionState().withAdShards(target, required - 1))
        val result = assertNotNull(runDailyDraw(MemoryConsumables(), collection, MemoryDraws(), T0, FixedRandom(0)))
        assertTrue(result.unlocked)
        assertTrue(collection.state.isClaimed(target.id))
    }
}
