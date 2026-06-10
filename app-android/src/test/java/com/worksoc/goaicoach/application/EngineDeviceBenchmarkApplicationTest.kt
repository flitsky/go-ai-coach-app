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
            samplesPerVisit = 5,
            timeCapMs = 5_000L,
            metrics = listOf(
                EngineBenchmarkMetric(visits = 16, samples = 5, minMs = 1.0, avgMs = 2.0, maxMs = 3.0),
            ),
        )

        assertEquals(
            """
                samplesPerVisit=5
                timeCapMs=5000
                B16: minMs=1.0, avgMs=2.0, maxMs=3.0, samples=5
            """.trimIndent(),
            profile.toSummaryText(),
        )
    }

    @Test
    fun benchmarkProgressShowsKoreanStageAndFraction() {
        val progress = EngineBenchmarkProgress(
            currentVisits = 32,
            currentSample = 2,
            samplesPerVisit = 5,
            completedCalls = 6,
            totalCalls = 15,
        )

        assertEquals("B32 실행시간 확보 중...", progress.stageText)
        assertEquals("샘플 2 / 5", progress.sampleText)
        assertEquals("전체 진행률 6 / 15", progress.progressText)
        assertEquals(0.4f, progress.fraction, 0.000001f)
    }
}
