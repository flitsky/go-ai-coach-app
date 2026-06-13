package com.worksoc.goaicoach.application

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionAnalysisCachePolicyTest {
    @Test
    fun cacheQualityMarksFullRootVisitsAsComplete() {
        val quality = PositionAnalysisCacheQuality.from(
            rootVisits = 32,
            requestedRootVisits = 32,
        )

        assertTrue(quality.isComplete)
        assertTrue(quality.isReusable)
    }

    @Test
    fun cacheQualityAllowsHalfFilledResultAsReusablePartial() {
        val quality = PositionAnalysisCacheQuality.from(
            rootVisits = 16,
            requestedRootVisits = 32,
        )

        assertFalse(quality.isComplete)
        assertTrue(quality.isReusable)
    }

    @Test
    fun cacheQualityKeepsWeakResultDiagnosticOnly() {
        val quality = PositionAnalysisCacheQuality.from(
            rootVisits = 8,
            requestedRootVisits = 32,
        )

        assertFalse(quality.isComplete)
        assertFalse(quality.isReusable)
        assertTrue(quality.isStorable)
    }
}
