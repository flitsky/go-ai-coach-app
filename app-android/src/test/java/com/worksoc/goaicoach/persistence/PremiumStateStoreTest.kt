package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.premium.FeatureId
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
    fun encodeDecodeRoundTripsClaimedFeatures() {
        // 다중 원소로 검증 — Set이라 순서 무관하게 라운드트립돼야 한다.
        val original = PremiumState(claimedFeatures = setOf(FeatureId.Undo, FeatureId.Eval))

        val decoded = PremiumStateCodec.decode(PremiumStateCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun decodeLegacyIsUndoClaimedBooleanMigratesToClaimedFeatures() {
        // claimedFeatures 배열 도입 이전엔 isUndoClaimed 단일 불리언으로 저장됐다 — 그
        // 구버전 JSON을 claimedFeatures = {Undo}로 마이그레이션해야 한다.
        val legacyJson = """{"source":"None","adGrantStartedAtMillis":null,"isUndoClaimed":true}"""

        val decoded = PremiumStateCodec.decode(legacyJson)

        assertEquals(PremiumState(claimedFeatures = setOf(FeatureId.Undo)), decoded)
    }

    @Test
    fun decodeMissingClaimedFeaturesKeyDefaultsToEmptySet() {
        // claimedFeatures 필드 추가 이전(그리고 isUndoClaimed도 없는) JSON을 하위 호환으로
        // 읽어야 한다 — preferences-autosave 계열과 동일하게 optBoolean 기본값에 의존한다.
        val legacyJson = """{"source":"None","adGrantStartedAtMillis":null}"""

        val decoded = PremiumStateCodec.decode(legacyJson)

        assertEquals(PremiumState(claimedFeatures = emptySet()), decoded)
    }
}
