package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCharacterId
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotCollectionCodecTest {

    @Test
    fun encodeThenDecodeRoundTripsClaimedBots() {
        val state = BotCollectionState(
            claimedBots = setOf(BotCharacterId("fast_beginner_1"), BotCharacterId("fast_beginner_4")),
        )

        assertEquals(state, BotCollectionCodec.decode(BotCollectionCodec.encode(state)))
    }

    @Test
    fun encodeThenDecodeRoundTripsEmptyCollection() {
        val decoded = BotCollectionCodec.decode(BotCollectionCodec.encode(BotCollectionState()))

        assertEquals(BotCollectionState(), decoded)
        assertTrue(decoded!!.claimedBots.isEmpty())
    }

    @Test
    fun decodeRejectsUnknownSchemaVersion() {
        val raw = JSONObject(BotCollectionCodec.encode(BotCollectionState()))
            .put("schema", 99)
            .toString()

        assertNull(BotCollectionCodec.decode(raw))
    }

    @Test
    fun decodeRejectsMalformedPayload() {
        assertNull(BotCollectionCodec.decode("not json at all"))
        assertNull(BotCollectionCodec.decode(""))
    }

    @Test
    fun decodeKeepsIdsThatAreNotInTheCatalogAndDropsEmptyOnes() {
        val unknown = BotCharacterId("bot_added_in_a_later_release")
        val raw = JSONObject(
            BotCollectionCodec.encode(BotCollectionState(claimedBots = setOf(unknown))),
        ).put("claimedBots", org.json.JSONArray(listOf(unknown.raw, ""))).toString()

        val decoded = BotCollectionCodec.decode(raw)

        assertNull(BotCharacterCatalog.byId(unknown))
        assertEquals(setOf(unknown), decoded?.claimedBots)
    }

    @Test
    fun decodeTreatsMissingClaimedBotsAsEmpty() {
        val raw = JSONObject().put("schema", 1).toString()

        assertEquals(BotCollectionState(), BotCollectionCodec.decode(raw))
    }

    @Test
    fun shardProgressSurvivesARoundTrip() {
        val bot = BotCharacterId("fast_beginner_2")
        val state = BotCollectionState(
            claimedBots = setOf(BotCharacterId("fast_beginner_1")),
            adShards = mapOf(bot to 3),
        )

        val restored = BotCollectionCodec.decode(BotCollectionCodec.encode(state))

        assertEquals(state, restored)
    }

    @Test
    fun stateSavedBeforeShardsExistedStillLoads() {
        // #11 이전 저장분에는 adShards 키가 없다. 스키마 번호를 올리지 않은 이유가 이것이다 —
        // 올렸다면 decode가 null을 주고 이미 수집한 캐릭터가 통째로 날아간다.
        val legacy = """{"schema":1,"claimedBots":["fast_beginner_1"]}"""

        val restored = BotCollectionCodec.decode(legacy)

        assertEquals(setOf(BotCharacterId("fast_beginner_1")), restored?.claimedBots)
        assertEquals(emptyMap<BotCharacterId, Int>(), restored?.adShards)
    }
}
