package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.describe

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
    val positionMoves: List<String> = emptyList(),
)

internal data class EngineBenchmarkProfile(
    val createdAtMillis: Long,
    val samplesPerVisit: Int,
    val timeCapMs: Long,
    val measurementVersion: Int = EngineBenchmarkMeasurementVersion,
    val benchmarkPositionName: String = EngineBenchmarkPositionName,
    val benchmarkPositionMoves: List<String> = emptyList(),
    val benchmarkRuleset: Ruleset = EngineBenchmarkRuleset,
    val metrics: List<EngineBenchmarkMetric>,
) {
    fun toSummaryText(): String =
        buildString {
            appendLine("measurementVersion=$measurementVersion")
            appendLine("samplesPerVisit=$samplesPerVisit")
            appendLine("timeCapMs=$timeCapMs")
            appendLine("benchmarkPosition=$benchmarkPositionName")
            appendLine("benchmarkRuleset=${benchmarkRuleset.scoringLabel}")
            appendLine("benchmarkPositionMoves=${benchmarkPositionMoves.ifEmpty { listOf("none") }.joinToString(", ")}")
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
        get() = if (currentSample <= 0) {
            "준비 중"
        } else {
            "샘플 $currentSample / $samplesPerVisit"
        }

    val progressText: String
        get() = "전체 진행률 $completedCalls / $totalCalls"

    val lastResultText: String?
        get() = lastFillStatus?.let { fill ->
            "직전 결과 root=${lastRootVisits ?: "none"}, elapsed=${lastElapsedMs ?: "none"}ms, fill=$fill"
        }
}

internal data class EngineBenchmarkUiState(
    val benchmarkText: String,
    val searchTimeBenchmarkAverages: Map<Int, Double>,
    val progress: EngineBenchmarkProgress? = null,
    val resultToConfirm: EngineBenchmarkProfile? = null,
) {
    val isRunning: Boolean
        get() = progress != null

    fun clearResult(): EngineBenchmarkUiState =
        copy(resultToConfirm = null)

    fun startWaitingForEngineSettle(): EngineBenchmarkUiState =
        copy(
            resultToConfirm = null,
            progress = EngineBenchmarkProgress(
                currentVisits = EngineBenchmarkDefaultVisits.first(),
                currentSample = 1,
                samplesPerVisit = EngineBenchmarkDefaultSamplesPerVisit,
                completedCalls = 0,
                totalCalls = EngineBenchmarkDefaultVisits.size * EngineBenchmarkDefaultSamplesPerVisit,
                stageOverride = "엔진 안정화 대기 중...",
            ),
        )

    fun updateProgress(progress: EngineBenchmarkProgress): EngineBenchmarkUiState =
        copy(progress = progress)

    fun applyStoredProfile(
        benchmarkText: String,
        profile: EngineBenchmarkProfile?,
    ): EngineBenchmarkUiState =
        copy(
            benchmarkText = benchmarkText,
            searchTimeBenchmarkAverages = profile?.averageMillisByVisits().orEmpty(),
        )

    fun completeWithProfile(
        benchmarkText: String,
        profile: EngineBenchmarkProfile,
    ): EngineBenchmarkUiState =
        copy(
            benchmarkText = benchmarkText,
            searchTimeBenchmarkAverages = profile.averageMillisByVisits(),
            progress = null,
            resultToConfirm = profile,
        )

    fun failWithoutProfile(): EngineBenchmarkUiState =
        copy(progress = null)

    fun showResult(profile: EngineBenchmarkProfile): EngineBenchmarkUiState =
        copy(resultToConfirm = profile)

    fun clearConfirmedResult(): EngineBenchmarkUiState =
        copy(resultToConfirm = null)

    companion object {
        fun initial(
            benchmarkText: String,
            profile: EngineBenchmarkProfile?,
        ): EngineBenchmarkUiState =
            EngineBenchmarkUiState(
                benchmarkText = benchmarkText,
                searchTimeBenchmarkAverages = profile?.averageMillisByVisits().orEmpty(),
            )
    }
}

private data class EngineBenchmarkPosition(
    val state: GameState,
    val name: String,
    val moveLabels: List<String>,
)

internal suspend fun EngineCoreApi.runStartupEngineBenchmark(
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
                syncToGameState(benchmarkState)
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
        syncToGameState(restoreState)
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

private suspend fun EngineCoreApi.prepareBenchmarkPosition(
    ruleset: Ruleset,
    timeCapMs: Long,
): EngineBenchmarkPosition {
    var state = GameState.empty(ruleset = ruleset)
    syncToGameState(state)

    while (state.moves.size < EngineBenchmarkPositionMoveCount) {
        val analysis = analyze(
            benchmarkAnalysisLimit(
                visits = EngineBenchmarkPositionSeedVisits,
                timeCapMs = timeCapMs,
            ),
        )
        val move = analysis.candidates.bestBenchmarkPlayMove() ?: break
        playMove(move)
        state = state.play(move)
    }

    return EngineBenchmarkPosition(
        state = state,
        name = EngineBenchmarkPositionName,
        moveLabels = state.moves.map { move -> move.describe(state.boardSize) },
    )
}

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

internal fun EngineBenchmarkProfile.averageMillisByVisits(): Map<Int, Double> =
    metrics.associate { metric -> metric.visits to metric.avgMs }

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

private fun Double.roundMillis(): Double =
    kotlin.math.round(this * 1_000.0) / 1_000.0

internal val EngineBenchmarkDefaultVisits = listOf(16, 32, 64)
internal val EngineBenchmarkRuleset = Ruleset.Japanese
internal const val EngineBenchmarkDefaultSamplesPerVisit = 5
internal const val EngineBenchmarkDefaultTimeCapMs = 5_000L
internal const val EngineBenchmarkMeasurementVersion = 5
internal const val EngineBenchmarkPositionName = "b16-best-3-variants"
private const val EngineBenchmarkPositionMoveCount = 3
private const val EngineBenchmarkPositionSeedVisits = 16

private const val FillOk = "OK"
private const val FillShort = "SHORT"
private const val FillUnknown = "UNKNOWN"
private const val VisitDiagnosticsRootGroup = 2
private const val VisitDiagnosticsElapsedGroup = 3
private val VisitDiagnosticsRegex =
    Regex("""Visit diagnostics: request=(\d+), root=(\d+|none), elapsedMs=(\d+), timeCapMs=([^,]+), fill=([A-Z]+)\.""")

private val BenchmarkVariantMoveLabels = listOf(
    "A9", "J1", "A1", "J9", "E1",
    "E9", "A5", "J5", "C3", "G7",
    "C7", "G3", "B8", "H2", "B2",
)
