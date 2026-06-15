package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.*
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
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

    @Test
    fun trustedOriginCanReplaceLocalEntryWhenRootVisitsAreEqual() {
        val key = PositionAnalysisCacheKey(
            positionFingerprint = "position",
            searchMode = EngineSearchMode.JsonPositionAnalysis,
            limit = AnalysisLimit(visits = 32),
        )
        val local = PositionAnalysisCacheEntry(
            key = key,
            result = AnalysisResult(
                status = EngineStatus.ready("local"),
                candidates = emptyList(),
                summary = "local",
            ),
            createdAtMillis = 10L,
            requestedRootVisits = 32,
            rootVisits = 32,
            origin = PositionAnalysisCacheOrigin.LocalUser,
        )
        val operator = local.copy(
            createdAtMillis = 1L,
            origin = PositionAnalysisCacheOrigin.OperatorTrusted,
        )

        assertTrue(shouldReplacePositionAnalysisCacheEntry(local, operator))
        assertFalse(shouldReplacePositionAnalysisCacheEntry(operator, local))
    }
}
