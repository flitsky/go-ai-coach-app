package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableItemId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConsumableInventoryCodecTest {

    private val evalId = ConsumableCatalog.EvalOnce.id

    @Test
    fun encodeThenDecodeRoundTripsEveryStockedItem() {
        val inventory = ConsumableInventory()
            .withGranted(ConsumableCatalog.EvalOnce.id, 10)
            .withGranted(ConsumableCatalog.TopMovesOnce.id, 3)
            .withGranted(ConsumableCatalog.PremiumOnce.id, 1)

        val decoded = ConsumableInventoryCodec.decode(ConsumableInventoryCodec.encode(inventory))

        assertEquals(inventory, decoded)
    }

    @Test
    fun emptyInventoryRoundTripsAsEmpty() {
        val decoded = ConsumableInventoryCodec.decode(ConsumableInventoryCodec.encode(ConsumableInventory()))

        assertEquals(ConsumableInventory(), decoded)
    }

    @Test
    fun unknownSchemaVersionAndMalformedJsonFallBackToNull() {
        val futureSchema = JSONObject().put("schema", 99).put("counts", JSONObject()).toString()

        assertNull(ConsumableInventoryCodec.decode(futureSchema))
        assertNull(ConsumableInventoryCodec.decode("not json at all"))
        assertNull(ConsumableInventoryCodec.decode(""))
    }

    @Test
    fun unknownItemKeysFromNewerVersionsSurviveARoundTrip() {
        // 상위 버전에서 받은 재고를 가진 채 다운그레이드해도 조용히 지워지지 않아야 한다.
        val future = ConsumableItemId("some_future_item")
        val inventory = ConsumableInventory(counts = mapOf(evalId to 2, future to 7))

        val decoded = ConsumableInventoryCodec.decode(ConsumableInventoryCodec.encode(inventory))

        assertNotNull(decoded)
        assertEquals(7, decoded!!.countOf(future))
        assertEquals(2, decoded.countOf(evalId))
    }

    @Test
    fun nonPositiveAndNonNumericCountsAreDroppedOnDecode() {
        val raw = JSONObject()
            .put("schema", 1)
            .put(
                "counts",
                JSONObject()
                    .put("eval_once", 0)
                    .put("top_moves_once", -3)
                    .put("premium_once", "twelve")
                    .put("some_future_item", 4),
            )
            .toString()

        val decoded = ConsumableInventoryCodec.decode(raw)

        assertEquals(ConsumableInventory(counts = mapOf(ConsumableItemId("some_future_item") to 4)), decoded)
    }
}
