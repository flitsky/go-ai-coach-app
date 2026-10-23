package com.worksoc.goaicoach.application.consumable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val T0 = 1_700_000_000_000L
private const val Hour = 60L * 60 * 1000

private class MemoryConsumableStore(var inventory: ConsumableInventory = ConsumableInventory()) : ConsumableStorePort {
    override fun save(inventory: ConsumableInventory) {
        this.inventory = inventory
    }

    override fun load(): ConsumableInventory = inventory
}

private class MemoryAdSkipTicketAdStore(var last: Long? = null) : AdSkipTicketAdStorePort {
    override fun lastEarnedAtMillis(): Long? = last

    override fun markEarned(atMillis: Long) {
        last = atMillis
    }
}

/** 백로그 #283 — 광고 1편 = 광고 스킵권 1장, 하루 한 번(받은 때부터 24시간 쿨타임), 상한이면 광고를 띄우지 않는다. */
class AdSkipTicketAdGrantTest {
    private val ticket = ConsumableCatalog.PremiumOnce

    @Test
    fun neverEarnedIsReady() {
        assertEquals(AdSkipTicketAdAvailability.Ready, adSkipTicketAdAvailability(ConsumableInventory(), null, T0))
    }

    @Test
    fun theCooldownRunsADayFromTheMomentItWasEarned() {
        val inventory = ConsumableInventory()
        assertEquals(AdSkipTicketAdAvailability.Cooldown(24 * Hour), adSkipTicketAdAvailability(inventory, T0, T0))
        assertEquals(AdSkipTicketAdAvailability.Cooldown(1), adSkipTicketAdAvailability(inventory, T0, T0 + 24 * Hour - 1))
        assertEquals(AdSkipTicketAdAvailability.Ready, adSkipTicketAdAvailability(inventory, T0, T0 + 24 * Hour))
    }

    @Test
    fun atMaxStockComesBeforeTheCooldown() {
        val full = ConsumableInventory().withGranted(ticket.id, PremiumOnceMaxStock)
        assertEquals(AdSkipTicketAdAvailability.AtMaxStock, adSkipTicketAdAvailability(full, null, T0))
        assertEquals(AdSkipTicketAdAvailability.AtMaxStock, adSkipTicketAdAvailability(full, T0, T0 + Hour))
    }

    @Test
    fun aClockTurnedBackDoesNotLockTheButton() {
        // 마지막으로 받은 때가 미래면(시계를 되감았다) 막지 않는다 — 정직한 사용자가 갇히지 않게.
        assertEquals(AdSkipTicketAdAvailability.Ready, adSkipTicketAdAvailability(ConsumableInventory(), T0 + Hour, T0))
    }

    @Test
    fun aGrantAddsOneTicketAndStartsTheCooldown() {
        val consumables = MemoryConsumableStore(ConsumableInventory().withGranted(ticket.id, 3))
        val ads = MemoryAdSkipTicketAdStore()

        val granted = assertNotNull(runAdSkipTicketAdGrant(consumables, ads, T0))

        assertEquals(4, granted.countOf(ticket.id))
        assertEquals(4, consumables.inventory.countOf(ticket.id))
        assertEquals(T0, ads.last)
        // 같은 날 두 번째는 받지 못하고, 아무것도 쓰지 않는다.
        assertNull(runAdSkipTicketAdGrant(consumables, ads, T0 + Hour))
        assertEquals(4, consumables.inventory.countOf(ticket.id))
        assertEquals(T0, ads.last)
    }

    @Test
    fun aStockThatFilledUpWhileTheAdPlayedGetsNothingAndNoCooldown() {
        // 광고가 떠 있는 동안 출석 보상이 상한까지 채웠다 — 광고를 본 뒤의 판정이 막고, 쿨타임도 돌리지 않는다.
        val consumables = MemoryConsumableStore(ConsumableInventory().withGranted(ticket.id, PremiumOnceMaxStock))
        val ads = MemoryAdSkipTicketAdStore()

        assertNull(runAdSkipTicketAdGrant(consumables, ads, T0))
        assertEquals(PremiumOnceMaxStock, consumables.inventory.countOf(ticket.id))
        assertNull(ads.last)
    }
}
