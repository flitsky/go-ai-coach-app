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
    val rootMinVisits: Int? = null,
    val rootMaxVisits: Int? = null,
    val rootAvgVisits: Double? = null,
    val fillOk: Int = 0,
    val fillShort: Int = 0,
    val fillUnknown: Int = 0,
    val sampleDetails: List<EngineBenchmarkSample> = emptyList(),
)

internal data class EngineBenchmarkSample(
    val sampleIndex: Int,
    val visits: Int,
    val elapsedMs: Double,
    val engineElapsedMs: Long?,
    val rootVisits: Int?,
    val fillStatus: String,
)

internal data class EngineBenchmarkProfile(
    val createdAtMillis: Long,
    val samplesPerVisit: Int,
    val timeCapMs: Long,
    val measurementVersion: Int = EngineBenchmarkMeasurementVersion,
    val metrics: List<EngineBenchmarkMetric>,
) {
    fun toSummaryText(): String =
        buildString {
            appendLine("measurementVersion=$measurementVersion")
            appendLine("samplesPerVisit=$samplesPerVisit")
            appendLine("timeCapMs=$timeCapMs")
            metrics.sortedBy { metric -> metric.visits }.forEach { metric ->
                appendLine(
                    "B${metric.visits}: minMs=${metric.minMs}, avgMs=${metric.avgMs}, maxMs=${metric.maxMs}, samples=${metric.samples}, root=${metric.rootSummaryText()}, fill=${metric.fillSummaryText()}",
                )
            }
        }.trim()
}

internal data class EngineBenchmarkProgress(
    val currentVisits: Int,
    val currentSample: Int,
    val samplesPerVisit: Int,
    val completedCalls: Int,
    val totalCalls: Int,
    val stageOverride: String? = null,
    val lastRootVisits: Int? = null,
    val lastFillStatus: String? = null,
    val lastElapsedMs: Double? = null,
) {
    val fraction: Float
        get() = if (totalCalls <= 0) {
            0f
        } else {
            (completedCalls.toFloat() / totalCalls).coerceIn(0f, 1f)
        }

    val stageText: String
        get() = stageOverride ?: "B$currentVisits 실행시간 확보 중..."

    val sampleText: String
        get() = "샘플 $currentSample / $samplesPerVisit"

    val progressText: String
        get() = "전체 진행률 $completedCalls / $totalCalls"

    val lastResultText: String?
        get() = lastFillStatus?.let { fill ->
            "직전 결과 root=${lastRootVisits ?: "none"}, elapsed=${lastElapsedMs ?: "none"}ms, fill=$fill"
        }
}

internal suspend fun EngineAdapter.runStartupEngineBenchmark(
    currentState: GameState,
    nowMillis: Long,
    samplesPerVisit: Int = EngineBenchmarkDefaultSamplesPerVisit,
    timeCapMs: Long = EngineBenchmarkDefaultTimeCapMs,
    visitsTargets: List<Int> = EngineBenchmarkDefaultVisits,
    onProgress: suspend (EngineBenchmarkProgress) -> Unit = {},
): EngineBenchmarkProfile {
    require(samplesPerVisit > 0) { "benchmark samplesPerVisit must be positive" }
    require(visitsTargets.isNotEmpty()) { "benchmark visitsTargets must not be empty" }

    val totalCalls = samplesPerVisit * visitsTargets.size
    var completedCalls = 0

    val benchmarkSamplesByVisits = visitsTargets.associateWith { mutableListOf<EngineBenchmarkSample>() }

    try {
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
                syncToGameState(benchmarkStateForSample(sampleIndex, currentState.ruleset))
                val startNanos = System.nanoTime()
                val result = analyze(benchmarkAnalysisLimit(visits = visits, timeCapMs = timeCapMs))
                val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000.0).roundMillis()
                val rootVisits = parseBenchmarkRootVisits(result.summary)
                val sample = EngineBenchmarkSample(
                    sampleIndex = currentSample,
                    visits = visits,
                    elapsedMs = elapsedMs,
                    engineElapsedMs = parseBenchmarkEngineElapsedMs(result.summary),
                    rootVisits = rootVisits,
                    fillStatus = rootVisits.toBenchmarkFillStatus(visits),
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
        syncToGameState(currentState)
    }

    val metrics = visitsTargets.map { visits ->
        benchmarkSamplesByVisits.getValue(visits).toBenchmarkMetricFromSamples(visits)
    }

    return EngineBenchmarkProfile(
        createdAtMillis = nowMillis,
        samplesPerVisit = samplesPerVisit,
        timeCapMs = timeCapMs,
        measurementVersion = EngineBenchmarkMeasurementVersion,
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

internal fun List<EngineBenchmarkSample>.toBenchmarkMetricFromSamples(visits: Int): EngineBenchmarkMetric {
    require(isNotEmpty()) { "benchmark samples must not be empty" }
    val rootSamples = mapNotNull { sample -> sample.rootVisits }
    return EngineBenchmarkMetric(
        visits = visits,
        samples = size,
        minMs = minOf { sample -> sample.elapsedMs }.roundMillis(),
        maxMs = maxOf { sample -> sample.elapsedMs }.roundMillis(),
        avgMs = map { sample -> sample.elapsedMs }.average().roundMillis(),
        rootMinVisits = rootSamples.minOrNull(),
        rootMaxVisits = rootSamples.maxOrNull(),
        rootAvgVisits = rootSamples.takeIf { it.isNotEmpty() }?.average()?.roundMillis(),
        fillOk = count { sample -> sample.fillStatus == FillOk },
        fillShort = count { sample -> sample.fillStatus == FillShort },
        fillUnknown = count { sample -> sample.fillStatus == FillUnknown },
        sampleDetails = sortedBy { sample -> sample.sampleIndex },
    )
}

internal fun parseBenchmarkRootVisits(summary: String): Int? =
    VisitDiagnosticsRegex
        .find(summary)
        ?.groups
        ?.get(VisitDiagnosticsRootGroup)
        ?.value
        ?.takeUnless { value -> value == "none" }
        ?.toIntOrNull()

internal fun parseBenchmarkEngineElapsedMs(summary: String): Long? =
    VisitDiagnosticsRegex
        .find(summary)
        ?.groups
        ?.get(VisitDiagnosticsElapsedGroup)
        ?.value
        ?.toLongOrNull()

private fun Int?.toBenchmarkFillStatus(requestVisits: Int): String =
    when {
        this == null -> FillUnknown
        this < requestVisits -> FillShort
        else -> FillOk
    }

internal fun EngineBenchmarkMetric.rootSummaryText(): String =
    if (rootMinVisits == null || rootMaxVisits == null || rootAvgVisits == null) {
        "none"
    } else {
        "min=$rootMinVisits, avg=$rootAvgVisits, max=$rootMaxVisits"
    }

internal fun EngineBenchmarkMetric.fillSummaryText(): String =
    "OK=$fillOk, SHORT=$fillShort, UNKNOWN=$fillUnknown"

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

internal val EngineBenchmarkDefaultVisits = listOf(16, 32, 64)
internal const val EngineBenchmarkDefaultSamplesPerVisit = 5
internal const val EngineBenchmarkDefaultTimeCapMs = 5_000L
internal const val EngineBenchmarkMeasurementVersion = 3

private const val FillOk = "OK"
private const val FillShort = "SHORT"
private const val FillUnknown = "UNKNOWN"
private const val VisitDiagnosticsRootGroup = 2
private const val VisitDiagnosticsElapsedGroup = 3
private val VisitDiagnosticsRegex =
    Regex("""Visit diagnostics: request=(\d+), root=(\d+|none), elapsedMs=(\d+), timeCapMs=([^,]+), fill=([A-Z]+)\.""")

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
