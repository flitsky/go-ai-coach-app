package com.worksoc.goaicoach.application.consumable

import com.worksoc.goaicoach.application.premium.AllowedVia
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.PremiumStateStorePort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val Now = 1_700_000_000_000L

private class FakeConsumableStore(
    var inventory: ConsumableInventory = ConsumableInventory(),
) : ConsumableStorePort {
    var saveCount = 0

    override fun save(inventory: ConsumableInventory) {
        this.inventory = inventory
        saveCount++
    }

    override fun load(): ConsumableInventory = inventory
}

private class FakePremiumStore(
    var state: PremiumState = PremiumState(),
) : PremiumStateStorePort {
    var saveCount = 0

    override fun save(state: PremiumState) {
        this.state = state
        saveCount++
    }

    override fun load(): PremiumState = state
}

class ConsumableSpendApplicationTest {

    private val evalTicket = ConsumableCatalog.EvalOnce
    private val premiumTicket = ConsumableCatalog.PremiumOnce

    @Test
    fun featureTicketIsSpentWhenTheFeatureIsOtherwiseLocked() {
        val inventory = ConsumableInventory().withGranted(evalTicket.id, 10)

        val decision = decideConsumableSpend(evalTicket, inventory, PremiumState(), Now)

        val spent = assertIs<ConsumableSpendDecision.Spent>(decision)
        assertEquals(9, spent.remaining)
        assertEquals(9, spent.inventory.countOf(evalTicket.id))
        // 기능 1회권은 프리미엄 상태를 건드리지 않는다.
        assertNull(spent.premiumState)
    }

    @Test
    fun activePremiumPassesThroughWithoutBurningStock() {
        val inventory = ConsumableInventory().withGranted(evalTicket.id, 10)

        val decision = decideConsumableSpend(evalTicket, inventory, PremiumState.purchased(), Now)

        // 4.5절 우선순위 규칙 — 프리미엄이 켜져 있는 동안 잔량이 억울하게 닳으면 안 된다.
        assertEquals(ConsumableSpendDecision.AllowedWithoutSpending(AllowedVia.Purchase), decision)
        assertEquals(10, inventory.countOf(evalTicket.id))
    }

    @Test
    fun permanentClaimAlsoPassesThroughWithoutBurningStock() {
        // 1일차 무르기처럼 영구 클레임으로 이미 열린 기능도 재고를 쓰지 않아야 한다.
        val claimed = PremiumState(claimedFeatures = setOf(FeatureId.Eval))
        val inventory = ConsumableInventory().withGranted(evalTicket.id, 10)

        val decision = decideConsumableSpend(evalTicket, inventory, claimed, Now)

        assertEquals(ConsumableSpendDecision.AllowedWithoutSpending(AllowedVia.Claimed), decision)
    }

    @Test
    fun expiredAdGrantNoLongerPassesThroughAndSpendsInstead() {
        val expired = PremiumState.adGranted(Now - PremiumState.AdGrantDurationMillis - 1)
        val inventory = ConsumableInventory().withGranted(evalTicket.id, 1)

        val decision = decideConsumableSpend(evalTicket, inventory, expired, Now)

        assertIs<ConsumableSpendDecision.Spent>(decision)
    }

    @Test
    fun emptyStockReportsOutOfStockSoTheLockedFlowStillApplies() {
        val decision = decideConsumableSpend(evalTicket, ConsumableInventory(), PremiumState(), Now)

        assertEquals(ConsumableSpendDecision.OutOfStock, decision)
    }

    @Test
    fun adSkipTicketTurnsOnPremiumForTheSameHourAsWatchingTheAd() {
        val inventory = ConsumableInventory().withGranted(premiumTicket.id, 10)

        val spent = assertIs<ConsumableSpendDecision.Spent>(
            decideConsumableSpend(premiumTicket, inventory, PremiumState(), Now),
        )

        val premium = checkNotNull(spent.premiumState)
        assertTrue(premium.isActive(Now))
        assertTrue(premium.isActive(Now + PremiumState.AdGrantDurationMillis - 1))
        assertTrue(!premium.isActive(Now + PremiumState.AdGrantDurationMillis))
        assertEquals(9, spent.remaining)
    }

    @Test
    fun adSkipTicketKeepsExistingPermanentClaims() {
        // #4에서 한 번 났던 회귀 — 프리미엄 상태를 통째로 덮어쓰면 영구 클레임이 사라진다.
        val claimed = PremiumState(claimedFeatures = setOf(FeatureId.Undo))
        val inventory = ConsumableInventory().withGranted(premiumTicket.id, 1)

        val spent = assertIs<ConsumableSpendDecision.Spent>(
            decideConsumableSpend(premiumTicket, inventory, claimed, Now),
        )

        assertEquals(setOf(FeatureId.Undo), checkNotNull(spent.premiumState).claimedFeatures)
    }

    @Test
    fun adSkipTicketIsNotSpentWhilePremiumIsAlreadyOn() {
        val active = PremiumState.adGranted(Now)
        val inventory = ConsumableInventory().withGranted(premiumTicket.id, 10)

        val decision = decideConsumableSpend(premiumTicket, inventory, active, Now)

        assertEquals(ConsumableSpendDecision.AllowedWithoutSpending(AllowedVia.AdGrant), decision)
    }

    @Test
    fun grantRunnerPersistsTheNewStock() {
        val store = FakeConsumableStore()

        val granted = runConsumableGrant(evalTicket, amount = 10, consumableStore = store)

        assertEquals(10, granted.countOf(evalTicket.id))
        assertEquals(10, store.inventory.countOf(evalTicket.id))
        assertEquals(1, store.saveCount)
    }

    @Test
    fun spendRunnerPersistsBothStoresOnlyWhenStockIsActuallyBurned() {
        val consumables = FakeConsumableStore(ConsumableInventory().withGranted(premiumTicket.id, 2))
        val premium = FakePremiumStore()

        val decision = runConsumableSpend(premiumTicket, consumables, premium, Now)

        assertIs<ConsumableSpendDecision.Spent>(decision)
        assertEquals(1, consumables.inventory.countOf(premiumTicket.id))
        assertEquals(1, consumables.saveCount)
        // 표가 닳았는데 프리미엄이 안 켜지는 상태가 남으면 안 된다.
        assertTrue(premium.state.isActive(Now))
        assertEquals(1, premium.saveCount)
    }

    @Test
    fun spendRunnerTouchesNoStoreWhenPassingThroughOrOutOfStock() {
        val purchased = FakePremiumStore(PremiumState.purchased())
        val stocked = FakeConsumableStore(ConsumableInventory().withGranted(evalTicket.id, 5))

        assertIs<ConsumableSpendDecision.AllowedWithoutSpending>(
            runConsumableSpend(evalTicket, stocked, purchased, Now),
        )
        assertEquals(0, stocked.saveCount)
        assertEquals(0, purchased.saveCount)

        val empty = FakeConsumableStore()
        val free = FakePremiumStore()
        assertEquals(ConsumableSpendDecision.OutOfStock, runConsumableSpend(evalTicket, empty, free, Now))
        assertEquals(0, empty.saveCount)
        assertEquals(0, free.saveCount)
    }
}
