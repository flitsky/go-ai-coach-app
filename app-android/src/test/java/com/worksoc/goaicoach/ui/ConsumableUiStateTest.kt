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

    /**
     * 백로그 #44의 결함 그 자체. 이 결함은 **탭 순서**에서만 드러난다 — 켜기 하나, 끄기 하나를
     * 따로 검사하면 둘 다 통과한다. 그래서 세 번의 탭을 순서대로 적어 고정한다.
     */
    @Test
    fun turningAOneShotOffAndOnAgainWithinTheSameMoveDoesNotChargeTwice() {
        val move = 21

        // 탭 1 — 켠다(여기서 한 장을 낸다).
        var ledger = OneShotLedger().mark(FeatureId.Eval, move)
        assertTrue(FeatureId.Eval in ledger.visible)

        // 탭 2 — 끈다. 표시는 사라져도 **값을 치렀다는 사실은 남아야 한다.**
        ledger = ledger.hide(FeatureId.Eval)
        assertFalse(FeatureId.Eval in ledger.visible)
        assertEquals(move, ledger.paidAtMove[FeatureId.Eval])

        // 탭 3 — 다시 켠다. 같은 수순이므로 호출부가 무료로 통과시킬 수 있어야 한다.
        assertTrue(ConsumableUiState(paidAtMove = ledger.paidAtMove).isPaidForMove(FeatureId.Eval, move))
        ledger = ledger.mark(FeatureId.Eval, move)
        assertTrue(FeatureId.Eval in ledger.visible)
    }

    /** 표 한 장의 유효 범위는 한 수다 — 다음 수에서는 다시 값을 받아야 한다. */
    @Test
    fun aPaidOneShotStopsBeingFreeOnceTheNextMoveIsPlayed() {
        val ledger = OneShotLedger().mark(FeatureId.Eval, 21)
        val state = ConsumableUiState(paidAtMove = ledger.paidAtMove)

        assertTrue(state.isPaidForMove(FeatureId.Eval, 21))
        // 만료 처리가 아직 돌지 않은 창에서도 수순이 다르면 무료로 통과해선 안 된다.
        assertFalse(state.isPaidForMove(FeatureId.Eval, 22))
        assertFalse(state.isPaidForMove(FeatureId.TopMoves, 21))

        val (expired, expiredIds) = ledger.expireAtMove(22)
        assertEquals(setOf(FeatureId.Eval), expiredIds)
        assertTrue(expired.paidAtMove.isEmpty())
    }

    /** 만료는 **이번 수에서 만료된 것만** 돌려줘야 한다 — 아니면 방금 켠 다른 기능까지 꺼진다. */
    @Test
    fun expiryReportsOnlyTheFeaturesThatActuallyExpired() {
        val ledger = OneShotLedger()
            .mark(FeatureId.Eval, 21)
            .hide(FeatureId.Eval)
            .mark(FeatureId.TopMoves, 22)

        val (next, expiredIds) = ledger.expireAtMove(22)

        assertEquals(setOf(FeatureId.Eval), expiredIds)
        assertEquals(mapOf(FeatureId.TopMoves to 22), next.paidAtMove)
        // 꺼 둔 기록도 원장과 함께 정리돼야 한다 — 남으면 다음에 켰을 때 보이지 않는다.
        assertTrue(next.hidden.isEmpty())
        assertTrue(FeatureId.TopMoves in next.visible)
    }
}
