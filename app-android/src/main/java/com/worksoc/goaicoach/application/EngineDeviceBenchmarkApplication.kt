package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor

internal data class EngineBenchmarkMetric(
    val visits: Int,
    val samples: Int,
    val minMs: Double,
    val maxMs: Double,
    val avgMs: Double,
)

internal data class EngineBenchmarkProfile(
    val createdAtMillis: Long,
    val samplesPerVisit: Int,
    val timeCapMs: Long,
    val metrics: List<EngineBenchmarkMetric>,
) {
    fun toSummaryText(): String =
        buildString {
            appendLine("samplesPerVisit=$samplesPerVisit")
            appendLine("timeCapMs=$timeCapMs")
            metrics.sortedBy { metric -> metric.visits }.forEach { metric ->
                appendLine(
                    "B${metric.visits}: minMs=${metric.minMs}, avgMs=${metric.avgMs}, maxMs=${metric.maxMs}, samples=${metric.samples}",
                )
            }
        }.trim()
}

internal suspend fun EngineAdapter.runStartupEngineBenchmark(
    currentState: GameState,
    nowMillis: Long,
    samplesPerVisit: Int = DefaultBenchmarkSamplesPerVisit,
    timeCapMs: Long = DefaultBenchmarkTimeCapMs,
    visitsTargets: List<Int> = DefaultBenchmarkVisits,
): EngineBenchmarkProfile {
    val metrics = visitsTargets.map { visits ->
        val elapsedSamples = (0 until samplesPerVisit).map { sampleIndex ->
            syncToGameState(benchmarkStateForSample(sampleIndex, currentState.ruleset))
            val startNanos = System.nanoTime()
            analyze(benchmarkAnalysisLimit(visits = visits, timeCapMs = timeCapMs))
            ((System.nanoTime() - startNanos) / 1_000_000.0).roundMillis()
        }
        elapsedSamples.toBenchmarkMetric(visits)
    }
    syncToGameState(currentState)
    return EngineBenchmarkProfile(
        createdAtMillis = nowMillis,
        samplesPerVisit = samplesPerVisit,
        timeCapMs = timeCapMs,
        metrics = metrics,
    )
}

internal fun List<Double>.toBenchmarkMetric(visits: Int): EngineBenchmarkMetric {
    require(isNotEmpty()) { "benchmark samples must not be empty" }
    return EngineBenchmarkMetric(
        visits = visits,
        samples = size,
        minMs = minOrNull()!!.roundMillis(),
        maxMs = maxOrNull()!!.roundMillis(),
        avgMs = average().roundMillis(),
    )
}

private fun benchmarkAnalysisLimit(
    visits: Int,
    timeCapMs: Long,
): AnalysisLimit =
    AnalysisLimit(
        visits = visits,
        timeMillis = timeCapMs,
        candidateCount = 1,
        includePolicy = false,
        refinePolicyMoves = 0,
        minVisitsPerCandidate = 0,
        minTimeMillis = null,
    )

private fun benchmarkStateForSample(
    sampleIndex: Int,
    ruleset: Ruleset,
): GameState {
    val moveCount = ((sampleIndex + 1) * 2).coerceAtMost(BenchmarkMoveLabels.size)
    return BenchmarkMoveLabels
        .take(moveCount)
        .fold(GameState.empty(ruleset = ruleset)) { state, label ->
            state.play(
                Move.Play(
                    player = state.nextPlayer,
                    coordinate = BoardCoordinate.fromLabel(label, state.boardSize),
                ),
            )
        }
}

private fun Double.roundMillis(): Double =
    kotlin.math.round(this * 1_000.0) / 1_000.0

private val DefaultBenchmarkVisits = listOf(16, 32, 64)
private const val DefaultBenchmarkSamplesPerVisit = 10
private const val DefaultBenchmarkTimeCapMs = 5_000L

private val BenchmarkMoveLabels = listOf(
    "E5", "C5",
    "G6", "F3",
    "C6", "D4",
    "B6", "G4",
    "H5", "F6",
    "F7", "F5",
    "E7", "B5",
    "D5", "E4",
    "A5", "G7",
    "H7", "G8",
)
