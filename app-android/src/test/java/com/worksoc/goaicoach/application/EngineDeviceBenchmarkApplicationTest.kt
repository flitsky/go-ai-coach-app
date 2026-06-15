package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.*
import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.CandidateMove
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
import org.junit.Assert.assertTrue
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
                measurementVersion=5
                samplesPerVisit=5
                timeCapMs=5000
                benchmarkPosition=b16-best-3-variants
                benchmarkRuleset=Territory
                benchmarkPositionMoves=none
                B16: minMs=1.0, avgMs=2.0, maxMs=3.0, samples=5, root=none, fill=OK=0, SHORT=0, UNKNOWN=0
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
    fun benchmarkDisplayPlansKeepUiTextPolicyOutOfCompose() {
        val progress = EngineBenchmarkProgress(
            currentVisits = 64,
            currentSample = 3,
            samplesPerVisit = 5,
            completedCalls = 13,
            totalCalls = 15,
        )
        val profile = EngineBenchmarkProfile(
            createdAtMillis = 123L,
            samplesPerVisit = 5,
            timeCapMs = 5_000L,
            metrics = listOf(
                EngineBenchmarkMetric(visits = 16, samples = 5, minMs = 1.0, avgMs = 2.0, maxMs = 3.0),
            ),
        )

        assertEquals(
            "Engine benchmark waiting for startup settle delay.",
            engineBenchmarkWaitingDisplayPlan().candidateText,
        )
        assertEquals(
            "Engine benchmark running: B16/B32/B64, 5 samples each.",
            engineBenchmarkRunningDisplayPlan(samplesPerVisit = 5).candidateText,
        )
        assertEquals("B64 실행시간 확보 중...", progress.toEngineBenchmarkDisplayPlan().engineMessage)
        assertEquals(
            "Engine benchmark running: 전체 진행률 13 / 15, 샘플 3 / 5.",
            progress.toEngineBenchmarkDisplayPlan().candidateText,
        )
        assertEquals(
            "Engine benchmark saved to /tmp/benchmark.json.",
            engineBenchmarkCompletedDisplayPlan(profile, "/tmp/benchmark.json").engineMessage,
        )
        assertEquals(
            "Engine benchmark failed: timeout",
            engineBenchmarkFailureDisplayPlan(IllegalStateException("timeout")).engineMessage,
        )
    }

    @Test
    fun benchmarkUiStateTracksProgressAndResult() {
        val initial = EngineBenchmarkUiState.initial(
            benchmarkText = "no benchmark",
            profile = null,
        )

        val waiting = initial.startWaitingForEngineSettle()
        val profile = EngineBenchmarkProfile(
            createdAtMillis = 123L,
            samplesPerVisit = 5,
            timeCapMs = 5_000L,
            metrics = listOf(
                EngineBenchmarkMetric(visits = 16, samples = 5, minMs = 1.0, avgMs = 2.0, maxMs = 3.0),
            ),
        )
        val completed = waiting.completeWithProfile(
            benchmarkText = "stored benchmark",
            profile = profile,
        )
        val confirmed = completed.clearConfirmedResult()

        assertEquals(true, waiting.isRunning)
        assertEquals("엔진 안정화 대기 중...", waiting.progress?.stageText)
        assertEquals(false, completed.isRunning)
        assertEquals("stored benchmark", completed.benchmarkText)
        assertEquals(mapOf(16 to 2.0), completed.searchTimeBenchmarkAverages)
        assertEquals(profile, completed.resultToConfirm)
        assertEquals(null, confirmed.resultToConfirm)
    }

    @Test
    fun parsesVisitDiagnosticsFromEngineSummary() {
        val summary = "KataGo JSON analysis with 32 visits / 5000ms. " +
            "Visit diagnostics: request=32, root=34, elapsedMs=123, timeCapMs=5000, fill=OK."

        assertEquals(34, parseBenchmarkRootVisits(summary))
        assertEquals(123L, parseBenchmarkEngineElapsedMs(summary))
    }

    @Test
    fun sampleMetricsSummarizeRootVisitsAndFillCounts() {
        val metric = listOf(
            EngineBenchmarkSample(
                sampleIndex = 1,
                visits = 32,
                elapsedMs = 10.0,
                engineElapsedMs = 8L,
                rootVisits = 33,
                fillStatus = "OK",
            ),
            EngineBenchmarkSample(
                sampleIndex = 2,
                visits = 32,
                elapsedMs = 20.0,
                engineElapsedMs = 18L,
                rootVisits = 20,
                fillStatus = "SHORT",
            ),
            EngineBenchmarkSample(
                sampleIndex = 3,
                visits = 32,
                elapsedMs = 30.0,
                engineElapsedMs = null,
                rootVisits = null,
                fillStatus = "UNKNOWN",
            ),
        ).toBenchmarkMetricFromSamples(visits = 32)

        assertEquals(10.0, metric.minMs, 0.000001)
        assertEquals(20.0, metric.avgMs, 0.000001)
        assertEquals(30.0, metric.maxMs, 0.000001)
        assertEquals(20, metric.rootMinVisits)
        assertEquals(33, metric.rootMaxVisits)
        assertEquals(26.5, metric.rootAvgVisits!!, 0.000001)
        assertEquals(1, metric.fillOk)
        assertEquals(1, metric.fillShort)
        assertEquals(1, metric.fillUnknown)
    }

    @Test
    fun startupBenchmarkInterleavesVisitTargetsBySampleRound() = runBlocking {
        val engine = RecordingBenchmarkEngineAdapter()
        val benchmarkDelegate = LocalEngineBenchmarkDelegate(engine)

        val profile = benchmarkDelegate.runStartupBenchmark(
            restoreState = GameState.empty(ruleset = Ruleset.Japanese),
            nowMillis = 123L,
            samplesPerVisit = 2,
            timeCapMs = 5_000L,
            visitsTargets = listOf(16, 32, 64),
        )

        assertEquals(listOf(16, 16, 16), engine.analyzeVisits.take(3))
        assertEquals(listOf(16, 32, 64, 16, 32, 64), engine.analyzeVisits.drop(3))
        assertEquals(listOf(16, 32, 64), profile.metrics.map { metric -> metric.visits })
        assertEquals(listOf(2, 2, 2), profile.metrics.map { metric -> metric.samples })
        assertEquals(listOf("Black E5", "White C5", "Black G6"), profile.benchmarkPositionMoves)
        assertEquals(
            listOf("Black E5", "White C5", "Black G6", "White A9"),
            profile.metrics.first { metric -> metric.visits == 16 }.sampleDetails[1].positionMoves,
        )
    }

    @Test
    fun startupBenchmarkUsesFixedRulesetAndRestoresUserStateAfterMeasurement() = runBlocking {
        val engine = RecordingBenchmarkEngineAdapter()
        val benchmarkDelegate = LocalEngineBenchmarkDelegate(engine)
        val restoreState = GameState.empty(ruleset = Ruleset.Chinese)
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))

        val profile = benchmarkDelegate.runStartupBenchmark(
            restoreState = restoreState,
            nowMillis = 123L,
            samplesPerVisit = 1,
            timeCapMs = 5_000L,
            visitsTargets = listOf(16),
        )

        assertEquals(Ruleset.Japanese, profile.benchmarkRuleset)
        assertEquals(Ruleset.Chinese, engine.newGameRulesets.last())
        assertEquals(restoreState.moves, engine.state.moves)
        assertEquals(Ruleset.Chinese, engine.state.ruleset)
        assertEquals(true, engine.newGameRulesets.dropLast(1).all { ruleset -> ruleset == Ruleset.Japanese })
    }

    @Test
    fun startupBenchmarkEffectRunnerDelegatesToEngineSessionClient() = runBlocking {
        val engine = RecordingBenchmarkEngineAdapter()
        val client = LocalEngineSessionClient(engine)
        val progressEvents = mutableListOf<EngineBenchmarkProgress>()
        val restoreState = GameState.empty(ruleset = Ruleset.Chinese)
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))

        val profile = client.runStartupBenchmarkEffect(
            effect = GameSessionEffect.RunStartupBenchmark,
            context = StartupBenchmarkExecutionContext(
                restoreState = restoreState,
                nowMillis = 456L,
            ),
            onProgress = { progress -> progressEvents += progress },
        )

        assertEquals(456L, profile.createdAtMillis)
        assertEquals(listOf(16, 32, 64), profile.metrics.map { metric -> metric.visits })
        assertEquals(true, progressEvents.isNotEmpty())
        assertEquals(restoreState.moves, engine.state.moves)
        assertEquals(Ruleset.Chinese, engine.state.ruleset)
    }

    @Test
    fun startupBenchmarkWorkflowResultWrapsSuccessAndFailure() = runBlocking {
        val restoreState = GameState.empty(ruleset = Ruleset.Chinese)

        val success = LocalEngineSessionClient(RecordingBenchmarkEngineAdapter())
            .runStartupBenchmarkWorkflowResult(
                effect = GameSessionEffect.RunStartupBenchmark,
                context = StartupBenchmarkExecutionContext(
                    restoreState = restoreState,
                    nowMillis = 456L,
                ),
            )
        val failure = LocalEngineSessionClient(
            RecordingBenchmarkEngineAdapter(analyzeError = IllegalStateException("benchmark failed")),
        ).runStartupBenchmarkWorkflowResult(
            effect = GameSessionEffect.RunStartupBenchmark,
            context = StartupBenchmarkExecutionContext(
                restoreState = restoreState,
                nowMillis = 456L,
            ),
        )

        assertTrue(success is StartupBenchmarkWorkflowResult.Success)
        assertEquals(456L, (success as StartupBenchmarkWorkflowResult.Success).profile.createdAtMillis)
        assertTrue(failure is StartupBenchmarkWorkflowResult.Failure)
        assertEquals("benchmark failed", (failure as StartupBenchmarkWorkflowResult.Failure).error.message)
    }
}

private class RecordingBenchmarkEngineAdapter(
    private val analyzeError: Throwable? = null,
) : EngineAdapter {
    val analyzeVisits = mutableListOf<Int>()
    val newGameRulesets = mutableListOf<Ruleset>()
    var state = GameState.empty()
        private set

    override suspend fun initialize(profile: EngineProfile): EngineStatus =
        EngineStatus.ready("initialized")

    override suspend fun configure(profile: EngineProfile): EngineStatus =
        EngineStatus.ready("configured")

    override suspend fun newGame(
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStatus {
        newGameRulesets += ruleset
        state = GameState.empty(boardSize = boardSize, ruleset = ruleset)
        return EngineStatus.ready("new game")
    }

    override suspend fun playMove(move: Move): EngineStatus {
        state = state.play(move)
        return EngineStatus.ready("played")
    }

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
        analyzeError?.let { throw it }
        return AnalysisResult(
            status = EngineStatus.ready("analyzed"),
            candidates = listOfNotNull(state.nextBenchmarkCandidate()),
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

    private fun GameState.nextBenchmarkCandidate(): CandidateMove? {
        val labels = listOf("E5", "C5", "G6", "F3", "C6", "D4")
        val label = labels.getOrNull(moves.size) ?: return null
        return CandidateMove(
            move = Move.Play(nextPlayer, BoardCoordinate.fromLabel(label, boardSize)),
            pointLoss = 0.0,
            engineOrder = 0,
        )
    }
}
