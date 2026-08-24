package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.premium.FeatureId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumableUiStateTest {

    private fun stateWith(vararg stocked: Pair<com.worksoc.goaicoach.application.consumable.ConsumableItem, Int>) =
        ConsumableUiState(
            inventory = stocked.fold(ConsumableInventory()) { inventory, (item, count) ->
                inventory.withGranted(item.id, count)
            },
        )

    @Test
    fun ticketIsOfferedOnlyWhileStockRemains() {
        val stocked = stateWith(ConsumableCatalog.EvalOnce to 2)

        assertEquals(ConsumableCatalog.EvalOnce, stocked.ticketFor(FeatureId.Eval))
        assertEquals(2, stocked.countOf(ConsumableCatalog.EvalOnce))
        // 재고가 없으면 1회권 경로를 제안하지 않는다 — 기존 업셀(광고/구매)로 가야 한다.
        assertNull(stocked.ticketFor(FeatureId.TopMoves))
        assertNull(ConsumableUiState().ticketFor(FeatureId.Eval))
    }

    @Test
    fun featuresWithoutAOneShotTicketNeverOfferOne() {
        val stocked = stateWith(ConsumableCatalog.EvalOnce to 5, ConsumableCatalog.PremiumOnce to 5)

        // 무르기는 1일차 영구 클레임으로 풀리고, 기보 리뷰는 보상 대상이 아니다(4.2절).
        assertNull(stocked.ticketFor(FeatureId.Undo))
        assertNull(stocked.ticketFor(FeatureId.MoveReview))
    }

    @Test
    fun oneShotTrackingIsPerFeature() {
        val state = ConsumableUiState(oneShotFeatures = setOf(FeatureId.Eval))

        assertTrue(state.isOneShotActive(FeatureId.Eval))
        assertFalse(state.isOneShotActive(FeatureId.TopMoves))
        assertFalse(ConsumableUiState().isOneShotActive(FeatureId.Eval))
    }
}
