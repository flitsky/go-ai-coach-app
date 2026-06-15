package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.engine.engineOperationRequest
import com.worksoc.goaicoach.application.session.GameSessionEffect

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.application.engine.operation.EngineOperationGate
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleCallbacks
import com.worksoc.goaicoach.application.engine.operation.evaluateEngineBenchmarkGate
import com.worksoc.goaicoach.application.engine.operation.runEngineOperationInScope
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Ruleset
import kotlinx.coroutines.delay

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

internal data class EngineBenchmarkDisplayPlan(
    val engineMessage: String,
    val candidateText: String,
)

internal fun engineBenchmarkWaitingDisplayPlan(): EngineBenchmarkDisplayPlan =
    EngineBenchmarkDisplayPlan(
        engineMessage = "엔진 벤치마크 시작 전 안정화 대기 중입니다.",
        candidateText = "Engine benchmark waiting for startup settle delay.",
    )

internal fun engineBenchmarkRunningDisplayPlan(
    samplesPerVisit: Int = EngineBenchmarkDefaultSamplesPerVisit,
): EngineBenchmarkDisplayPlan =
    EngineBenchmarkDisplayPlan(
        engineMessage = "최초 실행환경에서 최적 플레이를 위해 벤치마크 테스트가 진행중입니다.",
        candidateText = "Engine benchmark running: B16/B32/B64, $samplesPerVisit samples each.",
    )

internal fun EngineBenchmarkProgress.toEngineBenchmarkDisplayPlan(): EngineBenchmarkDisplayPlan =
    EngineBenchmarkDisplayPlan(
        engineMessage = stageText,
        candidateText = "Engine benchmark running: $progressText, $sampleText.",
    )

internal fun engineBenchmarkCompletedDisplayPlan(
    profile: EngineBenchmarkProfile,
    storePath: String,
): EngineBenchmarkDisplayPlan =
    EngineBenchmarkDisplayPlan(
        engineMessage = "Engine benchmark saved to $storePath.",
        candidateText = "Engine benchmark complete.\n${profile.toSummaryText()}",
    )

internal fun engineBenchmarkFailureDisplayPlan(error: Throwable): EngineBenchmarkDisplayPlan =
    EngineBenchmarkDisplayPlan(
        engineMessage = "Engine benchmark failed: ${error.message ?: "unknown error"}",
        candidateText = "Engine benchmark failed. The app will continue with built-in defaults.",
    )

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

internal data class StartupBenchmarkExecutionContext(
    val restoreState: GameState,
    val nowMillis: Long,
)

internal sealed class StartupBenchmarkWorkflowResult {
    data class Success(val profile: EngineBenchmarkProfile) : StartupBenchmarkWorkflowResult()
    data class Failure(val error: Throwable) : StartupBenchmarkWorkflowResult()
}

internal data class EngineBenchmarkRunRequest(
    val engineClient: EngineSessionClient,
    val store: EngineBenchmarkStorePort,
    val state: GameState,
    val sessionGeneration: Long,
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    val benchmarkUiState: EngineBenchmarkUiState,
    val diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
    val lifecycleCallbacks: EngineOperationLifecycleCallbacks = EngineOperationLifecycleCallbacks(),
    val nowMillis: () -> Long = System::currentTimeMillis,
    val delayMillis: suspend (Long) -> Unit = { millis -> delay(millis) },
    val runEngineWork: suspend (suspend () -> StartupBenchmarkWorkflowResult) -> StartupBenchmarkWorkflowResult =
        { block -> runEngineIo { block() } },
    val onBlocked: (String) -> Unit = {},
    val onBenchmarkUiState: (EngineBenchmarkUiState) -> Unit = {},
    val onDisplayPlan: (EngineBenchmarkDisplayPlan) -> Unit = {},
    val onProgress: suspend (EngineBenchmarkProgress, EngineBenchmarkDisplayPlan) -> Unit = { _, _ -> },
)

internal suspend fun runEngineBenchmarkApplication(request: EngineBenchmarkRunRequest) {
    when (
        val gate = evaluateEngineBenchmarkGate(
            isEngineReady = request.isEngineReady,
            supportsDeviceBenchmark = request.engineClient.capabilities.supportsDeviceBenchmark,
            isEngineBusy = request.isEngineBusy,
            isBenchmarkRunning = request.benchmarkUiState.isRunning,
        )
    ) {
        EngineOperationGate.Allow -> Unit
        EngineOperationGate.NoOp -> return
        is EngineOperationGate.Block -> {
            request.onBlocked(gate.message)
            return
        }
    }

    val clearedState = request.benchmarkUiState.clearResult()
    request.onBenchmarkUiState(clearedState)
    val operation = engineOperationRequest(
        kind = EngineOperationKind.StartupBenchmark,
        state = request.state,
        sessionGeneration = request.sessionGeneration,
        timeoutPolicy = EngineTimeoutPolicy(label = "startup-benchmark"),
        fallbackPolicy = EngineFallbackPolicy.None,
    )

    runEngineOperationInScope(
        request = operation,
        callbacks = request.lifecycleCallbacks,
    ) {
        val waitingState = clearedState.startWaitingForEngineSettle()
        request.onBenchmarkUiState(waitingState)
        request.onDisplayPlan(engineBenchmarkWaitingDisplayPlan())
        request.delayMillis(EngineBenchmarkStartupSettleDelayMillis)

        request.onDisplayPlan(engineBenchmarkRunningDisplayPlan())
        val benchmarkResult = request.runEngineWork {
            request.engineClient.runStartupBenchmarkWorkflowResult(
                effect = GameSessionEffect.RunStartupBenchmark,
                context = StartupBenchmarkExecutionContext(
                    restoreState = request.state,
                    nowMillis = request.nowMillis(),
                ),
                operationRequest = operation,
                diagnosticEventLog = request.diagnosticEventLog,
                onProgress = { progress ->
                    request.onProgress(progress, progress.toEngineBenchmarkDisplayPlan())
                },
            )
        }

        when (benchmarkResult) {
            is StartupBenchmarkWorkflowResult.Success -> {
                val profile = benchmarkResult.profile
                request.store.save(profile)
                request.onBenchmarkUiState(
                    waitingState.completeWithProfile(
                        benchmarkText = request.store.loadText(),
                        profile = profile,
                    ),
                )
                request.onDisplayPlan(
                    engineBenchmarkCompletedDisplayPlan(
                        profile = profile,
                        storePath = request.store.path(),
                    ),
                )
            }

            is StartupBenchmarkWorkflowResult.Failure -> {
                request.onDisplayPlan(engineBenchmarkFailureDisplayPlan(benchmarkResult.error))
                request.onBenchmarkUiState(waitingState.failWithoutProfile())
            }
        }
    }
}

internal suspend fun EngineSessionClient.runStartupBenchmarkEffect(
    effect: GameSessionEffect.RunStartupBenchmark,
    context: StartupBenchmarkExecutionContext,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
    onProgress: suspend (EngineBenchmarkProgress) -> Unit = {},
): EngineBenchmarkProfile {
    return runObservedEngineOperation(
        request = operationRequest ?: engineOperationRequest(
            kind = EngineOperationKind.StartupBenchmark,
            state = context.restoreState,
            sessionGeneration = 0L,
            timeoutPolicy = EngineTimeoutPolicy(label = "startup-benchmark"),
            fallbackPolicy = EngineFallbackPolicy.None,
        ),
        diagnosticEventLog = diagnosticEventLog,
    ) {
        runStartupBenchmark(
            restoreState = context.restoreState,
            nowMillis = context.nowMillis,
            onProgress = onProgress,
        )
    }
}

internal suspend fun EngineSessionClient.runStartupBenchmarkWorkflowResult(
    effect: GameSessionEffect.RunStartupBenchmark,
    context: StartupBenchmarkExecutionContext,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
    onProgress: suspend (EngineBenchmarkProgress) -> Unit = {},
): StartupBenchmarkWorkflowResult =
    runCatching {
        runStartupBenchmarkEffect(
            effect = effect,
            context = context,
            operationRequest = operationRequest,
            diagnosticEventLog = diagnosticEventLog,
            onProgress = onProgress,
        )
    }.fold(
        onSuccess = { profile -> StartupBenchmarkWorkflowResult.Success(profile) },
        onFailure = { error -> StartupBenchmarkWorkflowResult.Failure(error) },
    )

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

internal fun Int?.toBenchmarkFillStatus(requestVisits: Int): String =
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

internal fun benchmarkAnalysisLimit(
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

internal fun Double.roundMillis(): Double =
    kotlin.math.round(this * 1_000.0) / 1_000.0

internal val EngineBenchmarkDefaultVisits = listOf(16, 32, 64)
internal val EngineBenchmarkRuleset = Ruleset.Japanese
internal const val EngineBenchmarkDefaultSamplesPerVisit = 5
internal const val EngineBenchmarkDefaultTimeCapMs = 5_000L
internal const val EngineBenchmarkMeasurementVersion = 5
internal const val EngineBenchmarkStartupSettleDelayMillis = 1_500L
internal const val EngineBenchmarkPositionName = "b16-best-3-variants"
internal const val EngineBenchmarkPositionMoveCount = 3
internal const val EngineBenchmarkPositionSeedVisits = 16

internal const val FillOk = "OK"
internal const val FillShort = "SHORT"
internal const val FillUnknown = "UNKNOWN"
private const val VisitDiagnosticsRootGroup = 2
private const val VisitDiagnosticsElapsedGroup = 3
private val VisitDiagnosticsRegex =
    Regex("""Visit diagnostics: request=(\d+), root=(\d+|none), elapsedMs=(\d+), timeCapMs=([^,]+), fill=([A-Z]+)\.""")

internal val BenchmarkVariantMoveLabels = listOf(
    "A9", "J1", "A1", "J9", "E1",
    "E9", "A5", "J5", "C3", "G7",
    "C7", "G3", "B8", "H2", "B2",
)
