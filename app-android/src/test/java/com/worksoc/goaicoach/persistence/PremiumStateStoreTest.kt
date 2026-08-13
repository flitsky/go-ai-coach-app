package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.premium.PremiumSource
import com.worksoc.goaicoach.application.premium.PremiumState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PremiumStateStoreTest {
    @Test
    fun encodeDecodeRoundTripsDefaultState() {
        val decoded = PremiumStateCodec.decode(PremiumStateCodec.encode(PremiumState()))

        assertEquals(PremiumState(), decoded)
    }

    @Test
    fun encodeDecodeRoundTripsAdGrantedState() {
        val original = PremiumState.adGranted(nowMillis = 1_000_000L)

        val decoded = PremiumStateCodec.decode(PremiumStateCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun encodeDecodeRoundTripsPurchasedState() {
        val original = PremiumState.purchased()

        val decoded = PremiumStateCodec.decode(PremiumStateCodec.encode(original))

        assertEquals(original, decoded)
        assertEquals(PremiumSource.Purchase, decoded?.source)
    }

    @Test
    fun decodeMalformedJsonReturnsNull() {
        assertNull(PremiumStateCodec.decode("not json"))
    }

    @Test
    fun encodeDecodeRoundTripsUndoClaimedFlag() {
        val original = PremiumState(isUndoClaimed = true)

        val decoded = PremiumStateCodec.decode(PremiumStateCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun decodeMissingUndoClaimedKeyDefaultsToFalse() {
        // isUndoClaimed 필드 추가 이전에 저장된 JSON(이 키 자체가 없음)을 하위 호환으로
        // 읽어야 한다 — preferences-autosave 계열과 동일하게 optBoolean 기본값에 의존한다.
        val legacyJson = """{"source":"None","adGrantStartedAtMillis":null}"""

        val decoded = PremiumStateCodec.decode(legacyJson)

        assertEquals(PremiumState(isUndoClaimed = false), decoded)
    }
}
