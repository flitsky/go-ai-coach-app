package com.worksoc.goaicoach.application.dailydraw

import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableStorePort
import com.worksoc.goaicoach.application.consumable.PremiumOnceMaxStock
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
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

/** 백로그 #283 — 실험실의 1일 1회 뽑기(사용자 2026-10-23). */
class DailyDrawTest {
    private val shardPath: List<BotCharacter> = BotCharacterCatalog.all
        .filter { it.unlockSource is BotUnlockSource.AdShards }
        .sortedBy { it.tierWithinGroup }

    @Test
    fun theOddsAreThirtyThirtyThirtyTen() {
        val odds = dailyDrawOdds(ConsumableInventory(), BotCollectionState())
        assertEquals(
            mapOf(
                DailyDrawPrizeKind.CharacterShard to 30,
                DailyDrawPrizeKind.Tickets to 30,
                DailyDrawPrizeKind.AdSkipTicket to 30,
                DailyDrawPrizeKind.Character to 10,
            ),
            odds,
        )
        assertEquals(100, odds.values.sum())
    }

    @Test
    fun charactersGoToTheLowestUnownedShardPathCharacterOnly() {
        assertTrue(shardPath.size >= 2, "조각 길 캐릭터가 둘이라는 전제(2 · 4단계)가 바뀌었다.")
        assertEquals(shardPath[0], dailyDrawCharacterTarget(BotCollectionState()))
        val lowestOwned = BotCollectionState().withClaimed(shardPath[0].id)
        assertEquals(shardPath[1], dailyDrawCharacterTarget(lowestOwned))
        // 출석 캐릭터는 대상이 아니다 — 조각 길을 다 가지면 대상이 없다.
        val allShardPathOwned = shardPath.fold(BotCollectionState()) { state, c -> state.withClaimed(c.id) }
        assertNull(dailyDrawCharacterTarget(allShardPathOwned))
    }

    @Test
    fun prizesThatCannotBeGivenDropOutOfTheOdds() {
        val allShardPathOwned = shardPath.fold(BotCollectionState()) { state, c -> state.withClaimed(c.id) }
        val skipFull = ConsumableInventory().withGranted(ConsumableCatalog.PremiumOnce.id, PremiumOnceMaxStock)
        assertEquals(
            mapOf(DailyDrawPrizeKind.Tickets to 30, DailyDrawPrizeKind.AdSkipTicket to 30),
            dailyDrawOdds(ConsumableInventory(), allShardPathOwned),
        )
        assertEquals(mapOf(DailyDrawPrizeKind.Tickets to 30), dailyDrawOdds(skipFull, allShardPathOwned))
    }

    @Test
    fun theRollLandsInTheStackedWeights() {
        val odds = dailyDrawOdds(ConsumableInventory(), BotCollectionState())
        assertEquals(DailyDrawPrizeKind.CharacterShard, pickDailyDrawPrize(odds, 0))
        assertEquals(DailyDrawPrizeKind.CharacterShard, pickDailyDrawPrize(odds, 29))
        assertEquals(DailyDrawPrizeKind.Tickets, pickDailyDrawPrize(odds, 30))
        assertEquals(DailyDrawPrizeKind.AdSkipTicket, pickDailyDrawPrize(odds, 89))
        assertEquals(DailyDrawPrizeKind.Character, pickDailyDrawPrize(odds, 90))
        assertEquals(DailyDrawPrizeKind.Character, pickDailyDrawPrize(odds, 99))
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
        // 완전판 — 그 캐릭터를 곧바로 영구 획득.
        run {
            val collection = MemoryCollection()
            val result = assertNotNull(runDailyDraw(MemoryConsumables(), collection, MemoryDraws(), T0, FixedRandom(95)))
            assertEquals(DailyDrawPrizeKind.Character, result.kind)
            assertTrue(result.unlocked)
            assertTrue(collection.state.isClaimed(shardPath[0].id))
        }
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
