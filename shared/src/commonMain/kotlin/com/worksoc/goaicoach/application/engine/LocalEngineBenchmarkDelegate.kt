package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.describe

internal class LocalEngineBenchmarkDelegate(
    private val coreApi: EngineCoreApi,
) {
    suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        samplesPerVisit: Int = EngineBenchmarkDefaultSamplesPerVisit,
        timeCapMs: Long = EngineBenchmarkDefaultTimeCapMs,
        visitsTargets: List<Int> = EngineBenchmarkDefaultVisits,
        benchmarkRuleset: Ruleset = EngineBenchmarkRuleset,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit = {},
    ): EngineBenchmarkProfile {
        require(samplesPerVisit > 0) { "benchmark samplesPerVisit must be positive" }
        require(visitsTargets.isNotEmpty()) { "benchmark visitsTargets must not be empty" }

        val totalCalls = samplesPerVisit * visitsTargets.size
        var completedCalls = 0

        val benchmarkSamplesByVisits = visitsTargets.associateWith { mutableListOf<EngineBenchmarkSample>() }
        var benchmarkPosition = EngineBenchmarkPosition(
            state = GameState.empty(ruleset = benchmarkRuleset),
            name = EngineBenchmarkPositionName,
            moveLabels = emptyList(),
        )

        try {
            onProgress(
                EngineBenchmarkProgress(
                    currentVisits = EngineBenchmarkPositionSeedVisits,
                    currentSample = 0,
                    samplesPerVisit = samplesPerVisit,
                    completedCalls = completedCalls,
                    totalCalls = totalCalls,
                    stageOverride = "벤치마크 포지션 생성 중...",
                ),
            )
            benchmarkPosition = prepareBenchmarkPosition(benchmarkRuleset, timeCapMs)
            (0 until samplesPerVisit).forEach { sampleIndex ->
                visitsTargets.forEach { visits ->
                    val currentSample = sampleIndex + 1
                    onProgress(
                        EngineBenchmarkProgress(
                            currentVisits = visits,
                            currentSample = currentSample,
                            samplesPerVisit = samplesPerVisit,
                            completedCalls = completedCalls,
                            totalCalls = totalCalls,
                        ),
                    )
                    val benchmarkState = benchmarkPosition.state.variantForBenchmarkSample(sampleIndex)
                    coreApi.syncToGameState(benchmarkState)
                    val startNanos = System.nanoTime()
                    val result = coreApi.analyze(benchmarkAnalysisLimit(visits = visits, timeCapMs = timeCapMs))
                    val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000.0).roundMillis()
                    val rootVisits = parseBenchmarkRootVisits(result.summary)
                    val sample = EngineBenchmarkSample(
                        sampleIndex = currentSample,
                        visits = visits,
                        elapsedMs = elapsedMs,
                        engineElapsedMs = parseBenchmarkEngineElapsedMs(result.summary),
                        rootVisits = rootVisits,
                        fillStatus = rootVisits.toBenchmarkFillStatus(visits),
                        positionMoves = benchmarkState.moves.map { move -> move.describe(benchmarkState.boardSize) },
                    )
                    benchmarkSamplesByVisits.getValue(visits) += sample
                    completedCalls += 1
                    onProgress(
                        EngineBenchmarkProgress(
                            currentVisits = visits,
                            currentSample = currentSample,
                            samplesPerVisit = samplesPerVisit,
                            completedCalls = completedCalls,
                            totalCalls = totalCalls,
                            lastRootVisits = sample.rootVisits,
                            lastFillStatus = sample.fillStatus,
                            lastElapsedMs = sample.elapsedMs,
                        ),
                    )
                }
            }
        } finally {
            coreApi.syncToGameState(restoreState)
        }

        val metrics = visitsTargets.map { visits ->
            benchmarkSamplesByVisits.getValue(visits).toBenchmarkMetricFromSamples(visits)
        }

        return EngineBenchmarkProfile(
            createdAtMillis = nowMillis,
            samplesPerVisit = samplesPerVisit,
            timeCapMs = timeCapMs,
            measurementVersion = EngineBenchmarkMeasurementVersion,
            benchmarkPositionName = benchmarkPosition.name,
            benchmarkPositionMoves = benchmarkPosition.moveLabels,
            benchmarkRuleset = benchmarkRuleset,
            metrics = metrics,
        )
    }

    private suspend fun prepareBenchmarkPosition(
        ruleset: Ruleset,
        timeCapMs: Long,
    ): EngineBenchmarkPosition {
        var state = GameState.empty(ruleset = ruleset)
        coreApi.syncToGameState(state)

        while (state.moves.size < EngineBenchmarkPositionMoveCount) {
            val analysis = coreApi.analyze(
                benchmarkAnalysisLimit(
                    visits = EngineBenchmarkPositionSeedVisits,
                    timeCapMs = timeCapMs,
                ),
            )
            val move = analysis.candidates.bestBenchmarkPlayMove() ?: break
            coreApi.playMove(move)
            state = state.play(move)
        }

        return EngineBenchmarkPosition(
            state = state,
            name = EngineBenchmarkPositionName,
            moveLabels = state.moves.map { move -> move.describe(state.boardSize) },
        )
    }
}

private data class EngineBenchmarkPosition(
    val state: GameState,
    val name: String,
    val moveLabels: List<String>,
)

private fun List<CandidateMove>.bestBenchmarkPlayMove(): Move.Play? =
    sortedWith(
        compareBy<CandidateMove> { candidate -> candidate.engineOrder ?: Int.MAX_VALUE }
            .thenBy { candidate -> candidate.pointLoss ?: Double.MAX_VALUE },
    )
        .mapNotNull { candidate -> candidate.move as? Move.Play }
        .firstOrNull()

private fun GameState.variantForBenchmarkSample(sampleIndex: Int): GameState {
    if (sampleIndex <= 0) {
        return this
    }

    var state = this
    var addedMoves = 0
    for (label in BenchmarkVariantMoveLabels) {
        if (addedMoves >= sampleIndex) {
            break
        }
        val coordinate = runCatching {
            BoardCoordinate.fromLabel(label, state.boardSize)
        }.getOrNull() ?: continue
        val move = Move.Play(state.nextPlayer, coordinate)
        state = runCatching { state.play(move) }
            .getOrNull()
            ?.also { addedMoves += 1 }
            ?: state
    }
    return state
}
