package com.worksoc.goaicoach.application

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineDeviceBenchmarkApplicationTest {
    @Test
    fun summarizesBenchmarkSamplesWithMinMaxAverage() {
        val metric = listOf(10.0, 20.0, 30.0).toBenchmarkMetric(visits = 32)

        assertEquals(32, metric.visits)
        assertEquals(3, metric.samples)
        assertEquals(10.0, metric.minMs, 0.000001)
        assertEquals(20.0, metric.avgMs, 0.000001)
        assertEquals(30.0, metric.maxMs, 0.000001)
    }

    @Test
    fun benchmarkProfileSummaryUsesCompactPerVisitLines() {
        val profile = EngineBenchmarkProfile(
            createdAtMillis = 123L,
            samplesPerVisit = 10,
            timeCapMs = 5_000L,
            metrics = listOf(
                EngineBenchmarkMetric(visits = 16, samples = 10, minMs = 1.0, avgMs = 2.0, maxMs = 3.0),
            ),
        )

        assertEquals(
            """
                samplesPerVisit=10
                timeCapMs=5000
                B16: minMs=1.0, avgMs=2.0, maxMs=3.0, samples=10
            """.trimIndent(),
            profile.toSummaryText(),
        )
    }
}
