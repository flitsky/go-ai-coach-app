package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeLimit
import kotlin.math.ceil
import com.worksoc.goaicoach.application.engine.operation.EngineOperationBlockReason

data class EngineBenchmarkMetric(
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

data class EngineBenchmarkSample(
    val sampleIndex: Int,
    val visits: Int,
    val elapsedMs: Double,
    val engineElapsedMs: Long?,
    val rootVisits: Int?,
    val fillStatus: String,
    val positionMoves: List<String> = emptyList(),
)

data class EngineBenchmarkProfile(
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

/**
 * Small, user-facing interpretation of a technical benchmark profile.
 *
 * The profile remains the source of truth for diagnostics and persistence. This
 * summary deliberately carries only the data a screen needs to explain the
 * result and suggest one of the supported search-time choices.
 */
data class EngineBenchmarkResultSummary(
    val representativeVisits: Int?,
    val representativeAverageMs: Double?,
    val recommendedSearchTimeLimit: SearchTimeLimit,
    val confidence: EngineBenchmarkResultConfidence,
) {
    val isCautious: Boolean
        get() = confidence == EngineBenchmarkResultConfidence.Cautious
}

/** Whether the benchmark can be presented as a normal recommendation. */
enum class EngineBenchmarkResultConfidence {
    Confirmed,
    Cautious,
}

/**
 * Produces a stable, conservative recommendation without exposing visit-budget
 * details to the UI. B32 is the representative normal-search budget. When an
 * older or partial profile has no usable B32 sample, use the upper middle
 * available budget so an even-sized set never selects the shorter option.
 */
fun EngineBenchmarkProfile.toResultSummary(): EngineBenchmarkResultSummary {
    val representative = metrics.representativeMetricOrNull()
    val baseRecommendation = representative
        ?.avgMs
        ?.toSupportedSearchTimeLimit()
        ?: SearchTimeLimit.WithinThreeSeconds
    val hasIncompleteFill = metrics.any { metric ->
        metric.fillShort > 0 || metric.fillUnknown > 0
    }
    val usesFallbackMetric = representative?.visits != EngineBenchmarkRepresentativeVisits
    val isCautious = representative == null || usesFallbackMetric || hasIncompleteFill

    return EngineBenchmarkResultSummary(
        representativeVisits = representative?.visits,
        representativeAverageMs = representative?.avgMs,
        recommendedSearchTimeLimit = if (hasIncompleteFill) {
            baseRecommendation.nextLonger()
        } else {
            baseRecommendation
        },
        confidence = if (isCautious) {
            EngineBenchmarkResultConfidence.Cautious
        } else {
            EngineBenchmarkResultConfidence.Confirmed
        },
    )
}

private fun List<EngineBenchmarkMetric>.representativeMetricOrNull(): EngineBenchmarkMetric? {
    val usableMetrics = filter(EngineBenchmarkMetric::hasUsableAverage)
        .sortedBy { metric -> metric.visits }
    return usableMetrics.firstOrNull { metric ->
        metric.visits == EngineBenchmarkRepresentativeVisits
    } ?: usableMetrics.getOrNull(usableMetrics.size / 2)
}

private fun EngineBenchmarkMetric.hasUsableAverage(): Boolean =
    samples > 0 && avgMs.isFinite() && avgMs >= 0.0

private fun Double.toSupportedSearchTimeLimit(): SearchTimeLimit =
    SearchTimeLimit.ceilingFor(
        ceil(this)
            .coerceAtMost(Long.MAX_VALUE.toDouble())
            .toLong()
            .coerceAtLeast(1L),
    )

data class EngineBenchmarkProgress(
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

/**
 * ⚠️ **이 상태가 화면에 닿지 않으면 벤치마크는 통째로 침묵한다** — 2026-09-10에 실제로 그랬다.
 * 진행/결과 팝업이 `ScreenDestination.InGame` 안에서만 그려지고 있었고, 버튼은 설정 화면으로
 * 옮겨져 있어서 **막힘·진행·성공·실패 네 갈래 전부** 아무것도 보이지 않았다.
 * 지금은 셸이 목적지와 무관하게 한 번 그린다(`EngineBenchmarkOverlays`).
 */
data class EngineBenchmarkUiState(
    val benchmarkText: String,
    val progress: EngineBenchmarkProgress? = null,
    val resultToConfirm: EngineBenchmarkProfile? = null,
    /**
     * 게이트가 실행을 막은 이유. ⚠️ **이것이 `null`이 아닌 동안이 유일하게 사용자에게 "왜 안
     * 되는지"가 보이는 구간이다** — 예전에는 이 사유가 `engineMessage`(진단 문자열)로만 흘러가
     * 어디에도 렌더되지 않았다.
     */
    val blockedReason: EngineOperationBlockReason? = null,
) {
    val isRunning: Boolean
        get() = progress != null

    /** ⚠️ 막힘 표시도 함께 지운다 — 새 시도가 앞 시도의 사유를 물려받으면 거짓을 말한다. */
    fun clearResult(): EngineBenchmarkUiState =
        copy(resultToConfirm = null, blockedReason = null)

    fun blockedBy(reason: EngineOperationBlockReason): EngineBenchmarkUiState =
        copy(progress = null, resultToConfirm = null, blockedReason = reason)

    fun clearBlocked(): EngineBenchmarkUiState =
        copy(blockedReason = null)

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

    fun completeWithProfile(
        benchmarkText: String,
        profile: EngineBenchmarkProfile,
    ): EngineBenchmarkUiState =
        copy(
            benchmarkText = benchmarkText,
            progress = null,
            resultToConfirm = profile,
        )

    fun failWithoutProfile(): EngineBenchmarkUiState =
        copy(progress = null)

    /** ⚠️ 결과를 띄울 때도 앞선 막힘 표시를 지운다 — 둘이 동시에 참일 수 없다. */
    fun showResult(profile: EngineBenchmarkProfile): EngineBenchmarkUiState =
        copy(resultToConfirm = profile, blockedReason = null)

    fun clearConfirmedResult(): EngineBenchmarkUiState =
        copy(resultToConfirm = null)

    companion object {
        fun initial(
            benchmarkText: String,
            profile: EngineBenchmarkProfile?,
        ): EngineBenchmarkUiState =
            EngineBenchmarkUiState(
                benchmarkText = benchmarkText,
            )
    }
}

data class StartupBenchmarkExecutionContext(
    val restoreState: GameState,
    val nowMillis: Long,
)

sealed class StartupBenchmarkWorkflowResult {
    data class Success(val profile: EngineBenchmarkProfile) : StartupBenchmarkWorkflowResult()
    data class Failure(val error: Throwable) : StartupBenchmarkWorkflowResult()
}

fun List<Double>.toBenchmarkMetric(visits: Int): EngineBenchmarkMetric {
    require(isNotEmpty()) { "benchmark samples must not be empty" }
    return EngineBenchmarkMetric(
        visits = visits,
        samples = size,
        minMs = min().roundMillis(),
        maxMs = max().roundMillis(),
        avgMs = average().roundMillis(),
    )
}

fun List<EngineBenchmarkSample>.toBenchmarkMetricFromSamples(visits: Int): EngineBenchmarkMetric {
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

fun parseBenchmarkRootVisits(summary: String): Int? =
    VisitDiagnosticsRegex
        .find(summary)
        ?.groups
        ?.get(VisitDiagnosticsRootGroup)
        ?.value
        ?.takeUnless { value -> value == "none" }
        ?.toIntOrNull()

fun parseBenchmarkEngineElapsedMs(summary: String): Long? =
    VisitDiagnosticsRegex
        .find(summary)
        ?.groups
        ?.get(VisitDiagnosticsElapsedGroup)
        ?.value
        ?.toLongOrNull()

fun Int?.toBenchmarkFillStatus(requestVisits: Int): String =
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

fun benchmarkAnalysisLimit(
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

fun Double.roundMillis(): Double =
    kotlin.math.round(this * 1_000.0) / 1_000.0

val EngineBenchmarkDefaultVisits = listOf(16, 32, 64)
internal const val EngineBenchmarkRepresentativeVisits = 32
val EngineBenchmarkRuleset = Ruleset.Japanese
const val EngineBenchmarkDefaultSamplesPerVisit = 5
const val EngineBenchmarkDefaultTimeCapMs = 5_000L
const val EngineBenchmarkMeasurementVersion = 5
const val EngineBenchmarkStartupSettleDelayMillis = 1_500L
const val EngineBenchmarkPositionName = "b16-best-3-variants"
const val EngineBenchmarkPositionMoveCount = 3
const val EngineBenchmarkPositionSeedVisits = 16

internal const val FillOk = "OK"
internal const val FillShort = "SHORT"
internal const val FillUnknown = "UNKNOWN"
private const val VisitDiagnosticsRootGroup = 2
private const val VisitDiagnosticsElapsedGroup = 3
private val VisitDiagnosticsRegex =
    Regex("""Visit diagnostics: request=(\d+), root=(\d+|none), elapsedMs=(\d+), timeCapMs=([^,]+), fill=([A-Z]+)\.""")

val BenchmarkVariantMoveLabels = listOf(
    "A9", "J1", "A1", "J9", "E1",
    "E9", "A5", "J5", "C3", "G7",
    "C7", "G3", "B8", "H2", "B2",
)
