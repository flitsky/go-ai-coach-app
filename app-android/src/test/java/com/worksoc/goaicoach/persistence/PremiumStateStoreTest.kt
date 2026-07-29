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
        val original = PremiumState.adGranted(sessionGeneration = 7L, nowMillis = 1_000_000L)

        val decoded = PremiumStateCodec.decode(PremiumStateCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun encodeDecodeRoundTripsPendingAdGrantWithNullSession() {
        val original = PremiumState.adGranted(sessionGeneration = null, nowMillis = 1_000_000L)

        val decoded = PremiumStateCodec.decode(PremiumStateCodec.encode(original))

        assertEquals(original, decoded)
        assertNull(decoded?.adGrantSessionGeneration)
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
}
