package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.analysisFingerprint
import com.worksoc.goaicoach.shared.forcedJsonPositionAnalysis

internal data class EngineSessionCapabilities(
    val supportsDeviceBenchmark: Boolean,
)

/**
 * Application-facing engine session boundary.
 *
 * UI code depends on this contract instead of the low-level [EngineCoreApi].
 * The current implementation delegates to a stateful local process adapter,
 * but a future remote-server engine can implement this interface without
 * exposing process sync, cache isolation, or transport details to Compose.
 */
internal interface EngineSessionClient {
    val capabilities: EngineSessionCapabilities

    fun positionAnalysisCacheStatsText(nowMillis: Long): String

    fun positionAnalysisCacheQualityFor(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
        nowMillis: Long,
    ): PositionAnalysisCacheQuality?

    suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult

    suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStartupResult

    suspend fun analyzePosition(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode = EngineSearchMode.GtpStatefulFast,
    ): AnalysisResult

    suspend fun optimizePositionAnalysisCache(
        plan: PositionAnalysisCacheOptimizationPlan,
    ): PositionAnalysisCacheOptimizationResult

    suspend fun syncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate

    suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate

    suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult

    suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult

    suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate

    suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution

    suspend fun undoMove(): EngineStatus

    suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile
}

internal class AdapterEngineSessionClient(
    private val coreApi: EngineCoreApi,
    override val capabilities: EngineSessionCapabilities = EngineSessionCapabilities(
        supportsDeviceBenchmark = false,
    ),
    private val positionAnalysisCacheStore: PositionAnalysisCacheStore = NoopPositionAnalysisCacheStore,
    private val trustedPositionAnalysisCacheProviders: List<TrustedPositionAnalysisCacheProvider> = emptyList(),
    private val diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
) : EngineSessionClient {
    override fun positionAnalysisCacheStatsText(nowMillis: Long): String {
        val localStats = positionAnalysisCacheStore.statsText(nowMillis)
        if (trustedPositionAnalysisCacheProviders.isEmpty()) {
            return localStats
        }
        val trustedStats = trustedPositionAnalysisCacheProviders
            .joinToString(";") { provider -> provider.statsText(nowMillis) }
        return "$localStats, trusted={$trustedStats}"
    }

    override fun positionAnalysisCacheQualityFor(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
        nowMillis: Long,
    ): PositionAnalysisCacheQuality? {
        val effectiveLimit = when (searchMode) {
            EngineSearchMode.GtpStatefulFast -> limit
            EngineSearchMode.JsonPositionAnalysis -> limit.forcedJsonPositionAnalysis()
        }
        val key = positionAnalysisCacheKeyFor(
            state = state,
            searchMode = searchMode,
            limit = effectiveLimit,
        )
        return positionAnalysisCacheStore.peek(key, nowMillis)?.quality
            ?: trustedPositionAnalysisCacheProviders
                .bestEntryFor(key = key, nowMillis = nowMillis, reusableOnly = false)
                ?.quality
    }

    override suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult =
        coreApi.startEngineSession(profile, state)

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStartupResult =
        coreApi.startNewEngineGame(profile, boardSize, ruleset)

    override suspend fun analyzePosition(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
    ): AnalysisResult =
        analyzePositionWithCache(
            state = state,
            limit = limit,
            searchMode = searchMode,
        )

    private suspend fun analyzePositionWithCache(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
        readCache: Boolean = true,
        cacheLimitOverride: AnalysisLimit? = null,
    ): AnalysisResult {
        val effectiveLimit = when (searchMode) {
            EngineSearchMode.GtpStatefulFast -> limit
            EngineSearchMode.JsonPositionAnalysis -> limit.forcedJsonPositionAnalysis()
        }
        val cacheLimit = cacheLimitOverride
            ?.forcedJsonPositionAnalysis()
            ?: effectiveLimit
        val nowMillis = System.currentTimeMillis()
        val cacheKey = positionAnalysisCacheKeyFor(
            state = state,
            searchMode = searchMode,
            limit = cacheLimit,
        )
        if (searchMode == EngineSearchMode.JsonPositionAnalysis && readCache) {
            positionAnalysisCacheStore
                .get(cacheKey, nowMillis)
                ?.let { entry -> return entry.result.withCacheHitSummary(entry) }
            trustedPositionAnalysisCacheProviders
                .bestEntryFor(key = cacheKey, nowMillis = nowMillis, reusableOnly = true)
                ?.let { entry -> return entry.result.withCacheHitSummary(entry) }
        }
        coreApi.syncToGameState(state)
        val result = coreApi.analyze(effectiveLimit)
        recordAnalysisDiagnosticEvent(
            state = state,
            requestedVisits = cacheLimit.visits,
            rootVisits = result.rootVisits,
            searchMode = searchMode,
        )
        if (
            searchMode == EngineSearchMode.JsonPositionAnalysis &&
            result.isStorablePositionAnalysisFor(cacheLimit)
        ) {
            val rootVisits = result.rootVisits
            if (rootVisits != null) {
                positionAnalysisCacheStore.put(
                    entry = PositionAnalysisCacheEntry(
                        key = cacheKey,
                        result = result,
                        createdAtMillis = nowMillis,
                        requestedRootVisits = cacheLimit.visits,
                        rootVisits = rootVisits,
                    ),
                    nowMillis = nowMillis,
                )
            }
        }
        return result
    }

    private fun recordAnalysisDiagnosticEvent(
        state: GameState,
        requestedVisits: Int,
        rootVisits: Int?,
        searchMode: EngineSearchMode,
    ) {
        val event = engineVisitFillDiagnosticEvent(
            requestedVisits = requestedVisits,
            rootVisits = rootVisits,
            searchMode = searchMode.name,
            positionFingerprint = state.analysisFingerprint(),
        ) ?: return
        diagnosticEventLog.append(event)
    }

    override suspend fun optimizePositionAnalysisCache(
        plan: PositionAnalysisCacheOptimizationPlan,
    ): PositionAnalysisCacheOptimizationResult {
        val summaries = mutableListOf<String>()
        var analyzedTargets = 0
        var reusableTargets = 0
        var completeTargets = 0
        plan.targets.forEach { target ->
            val result = analyzePositionWithCache(
                state = target.state,
                limit = target.executionLimit,
                searchMode = EngineSearchMode.JsonPositionAnalysis,
                readCache = false,
                cacheLimitOverride = target.cacheLimit,
            )
            val quality = result.cacheQualityFor(target.cacheLimit)
            analyzedTargets += 1
            if (quality.isReusable) {
                reusableTargets += 1
            }
            if (quality.isComplete) {
                completeTargets += 1
            }
            summaries += "M${target.moveNumber} ${target.levelLabel}: ${quality.summaryText()}"
        }
        return PositionAnalysisCacheOptimizationResult(
            requestedTargets = plan.targets.size,
            analyzedTargets = analyzedTargets,
            reusableTargets = reusableTargets,
            completeTargets = completeTargets,
            summaries = summaries,
        )
    }

    override suspend fun syncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        coreApi.syncAndEstimateGraphScore(state, profile)

    override suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        coreApi.configureSyncAndEstimateGraphScore(state, profile)

    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult {
        val aiPlayer = currentState.nextPlayer
        val turnProfile = playLevel.toEngineProfile(currentProfile, searchTimeSettings)
        coreApi.configure(turnProfile)
        coreApi.syncToGameState(currentState)
        val outcome = com.worksoc.goaicoach.match.applyAiTurn(
            engineAdapter = coreApi,
            currentState = currentState,
            aiPlayer = aiPlayer,
            playLevel = playLevel,
            searchTimeSettings = searchTimeSettings,
            searchMode = searchMode,
            isolateSearchCache = isolateSearchCache,
            analysisProvider = { limit ->
                analyzePositionWithCache(
                    state = currentState,
                    limit = limit,
                    searchMode = searchMode,
                )
            },
        )
        val estimate = runCatching {
            coreApi.estimateScore(scoreGraphAnalysisLimit(turnProfile))
        }.getOrNull()
        return AutoAiTurnResult(
            turnOutcome = outcome,
            scoreEstimate = estimate,
            profile = turnProfile,
            playLevel = playLevel,
        )
    }

    override suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult =
        coreApi.syncAfterHumanMove(
            afterMove = afterMove,
            profile = profile,
            move = move,
            previousReviewCandidates = previousReviewCandidates,
        )

    override suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate =
        coreApi.estimateScoreForState(
            state = state,
            profile = profile,
            syncFirst = syncFirst,
        )

    override suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution =
        coreApi.resolveEndgameForState(
            state = state,
            profile = profile,
            prePassCandidates = prePassCandidates,
        )

    override suspend fun undoMove(): EngineStatus =
        coreApi.undoMove()

    override suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile =
        coreApi.runStartupEngineBenchmark(
            restoreState = restoreState,
            nowMillis = nowMillis,
            onProgress = onProgress,
        )
}

private fun List<TrustedPositionAnalysisCacheProvider>.bestEntryFor(
    key: PositionAnalysisCacheKey,
    nowMillis: Long,
    reusableOnly: Boolean,
): PositionAnalysisCacheEntry? =
    bestPositionAnalysisCacheEntry(
        mapNotNull { provider ->
            if (reusableOnly) {
                provider.get(key, nowMillis)
            } else {
                provider.peek(key, nowMillis)
            }
        }.filterNot { entry -> entry.isExpired(nowMillis) },
    )
