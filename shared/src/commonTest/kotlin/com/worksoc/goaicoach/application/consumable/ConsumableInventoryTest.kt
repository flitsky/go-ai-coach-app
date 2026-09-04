package com.worksoc.goaicoach.application.consumable

import com.worksoc.goaicoach.application.premium.FeatureId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ConsumableInventoryTest {

    private val evalId = ConsumableCatalog.EvalOnce.id

    @Test
    fun emptyInventoryReportsNothing() {
        val inventory = ConsumableInventory()

        assertEquals(0, inventory.countOf(evalId))
        assertFalse(inventory.has(evalId))
    }

    @Test
    fun grantingAddsUpAndStopsAtThePerItemCap() {
        val ten = ConsumableInventory().withGranted(evalId, 10)
        assertEquals(10, ten.countOf(evalId))

        val twenty = ten.withGranted(evalId, 10)
        assertEquals(20, twenty.countOf(evalId))

        // 출석 주기가 반복돼도 무한히 쌓이지 않는다.
        val capped = twenty.withGranted(evalId, 1_000)
        assertEquals(ConsumableInventory.MaxPerItem, capped.countOf(evalId))
    }

    @Test
    fun grantingIgnoresNonPositiveAmountsAndSurvivesIntOverflow() {
        val inventory = ConsumableInventory().withGranted(evalId, 10)

        assertSame(inventory, inventory.withGranted(evalId, 0))
        assertSame(inventory, inventory.withGranted(evalId, -5))
        // Int 덧셈이었다면 음수로 넘쳤을 값 — 상한으로 막혀야 한다.
        assertEquals(ConsumableInventory.MaxPerItem, inventory.withGranted(evalId, Int.MAX_VALUE).countOf(evalId))
    }

    @Test
    fun consumingDecrementsByOneAndClearsTheKeyAtZero() {
        val two = ConsumableInventory().withGranted(evalId, 2)

        val one = two.withConsumed(evalId)
        assertEquals(1, one.countOf(evalId))

        val none = one.withConsumed(evalId)
        assertEquals(0, none.countOf(evalId))
        // 다 쓴 종류는 키째 사라져 "한 번도 받은 적 없음"과 같은 정규형이 된다.
        assertFalse(evalId in none.counts)
        assertEquals(ConsumableInventory(), none)
    }

    @Test
    fun consumingNeverGoesBelowZero() {
        val empty = ConsumableInventory()
        assertSame(empty, empty.withConsumed(evalId))

        val one = ConsumableInventory().withGranted(evalId, 1)
        assertEquals(0, one.withConsumed(evalId, 5).countOf(evalId))
        assertSame(one, one.withConsumed(evalId, 0))
    }

    @Test
    fun itemsAreTrackedIndependently() {
        val inventory = ConsumableInventory()
            .withGranted(ConsumableCatalog.EvalOnce.id, 10)
            .withGranted(ConsumableCatalog.TopMovesOnce.id, 3)
            .withConsumed(ConsumableCatalog.EvalOnce.id)

        assertEquals(9, inventory.countOf(ConsumableCatalog.EvalOnce.id))
        assertEquals(3, inventory.countOf(ConsumableCatalog.TopMovesOnce.id))
        assertEquals(0, inventory.countOf(ConsumableCatalog.PremiumOnce.id))
    }

    @Test
    fun unknownItemIdsFromNewerVersionsStaySpendable() {
        // 상위 버전에서 받은 뒤 다운그레이드한 재고 — 버리지 않고 그대로 다룬다.
        val future = ConsumableItemId("some_future_item")
        val inventory = ConsumableInventory(counts = mapOf(future to 4))

        assertNull(ConsumableCatalog.byId(future))
        assertEquals(3, inventory.withConsumed(future).countOf(future))
    }
}

class ConsumableCatalogTest {

    @Test
    fun catalogIdsAreUniqueAndStable() {
        val ids = ConsumableCatalog.all.map { it.id.raw }

        assertEquals(ids.size, ids.toSet().size)
        // 저장 스키마의 JSON 키라 바뀌면 기존 잔량이 유실된다 — 값 자체를 고정한다.
        assertEquals(listOf("eval_once", "top_moves_once", "premium_once"), ids)
    }

    @Test
    fun featureTicketsMapToTheirFeatureAndAdSkipMapsToNone() {
        assertEquals(ConsumableCatalog.EvalOnce, ConsumableCatalog.forFeature(FeatureId.Eval))
        assertEquals(ConsumableCatalog.TopMovesOnce, ConsumableCatalog.forFeature(FeatureId.TopMoves))
        // 무르기는 3일차 출석 보상의 영구 클레임으로 풀리고, 기보 리뷰는 보상 대상이 아니다(4.2절).
        assertNull(ConsumableCatalog.forFeature(FeatureId.Undo))
        assertNull(ConsumableCatalog.forFeature(FeatureId.MoveReview))
        assertTrue(ConsumableCatalog.PremiumOnce.effect is ConsumableEffect.PremiumGrant)
    }

    @Test
    fun byRawIdResolvesKnownKeysOnly() {
        assertEquals(ConsumableCatalog.PremiumOnce, ConsumableCatalog.byRawId("premium_once"))
        assertNull(ConsumableCatalog.byRawId("no_such_item"))
        assertNull(ConsumableCatalog.byRawId(""))
    }
}
