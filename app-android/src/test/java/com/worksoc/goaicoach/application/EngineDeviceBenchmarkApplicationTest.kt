package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveResult
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.runBlocking
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
                measurementVersion=2
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

    @Test
    fun startupBenchmarkInterleavesVisitTargetsBySampleRound() = runBlocking {
        val engine = RecordingBenchmarkEngineAdapter()

        val profile = engine.runStartupEngineBenchmark(
            currentState = GameState.empty(ruleset = Ruleset.Japanese),
            nowMillis = 123L,
            samplesPerVisit = 2,
            timeCapMs = 5_000L,
            visitsTargets = listOf(16, 32, 64),
        )

        assertEquals(listOf(16, 32, 64, 16, 32, 64), engine.analyzeVisits)
        assertEquals(listOf(16, 32, 64), profile.metrics.map { metric -> metric.visits })
        assertEquals(listOf(2, 2, 2), profile.metrics.map { metric -> metric.samples })
    }
}

private class RecordingBenchmarkEngineAdapter : EngineAdapter {
    val analyzeVisits = mutableListOf<Int>()

    override suspend fun initialize(profile: EngineProfile): EngineStatus =
        EngineStatus.ready("initialized")

    override suspend fun configure(profile: EngineProfile): EngineStatus =
        EngineStatus.ready("configured")

    override suspend fun newGame(
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStatus =
        EngineStatus.ready("new game")

    override suspend fun playMove(move: Move): EngineStatus =
        EngineStatus.ready("played")

    override suspend fun genMove(player: StoneColor): MoveResult =
        MoveResult(
            status = EngineStatus.ready("generated"),
            move = Move.Pass(player),
            summary = "generated",
        )

    override suspend fun undoMove(): EngineStatus =
        EngineStatus.ready("undone")

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult {
        analyzeVisits += limit.visits
        return AnalysisResult(
            status = EngineStatus.ready("analyzed"),
            candidates = emptyList(),
            summary = "analyzed",
        )
    }

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate =
        ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = 0.0,
            whiteWinRate = 0.5,
            summary = "estimated",
        )

    override suspend fun deadStones(): DeadStonesResult =
        DeadStonesResult(
            status = EngineStatus.ready("dead stones"),
            coordinates = emptyList(),
            summary = "dead stones",
        )

    override suspend fun scoreFinal(): FinalScoreResult =
        FinalScoreResult(
            status = EngineStatus.ready("final"),
            rawScore = "B+0.5",
            summary = "final",
        )

    override suspend fun stop(): EngineStatus =
        EngineStatus.stopped("stopped")
}
