package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.application.analysis.NoopPositionAnalysisCacheStore
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationResult
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheQuality
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheStore
import com.worksoc.goaicoach.application.analysis.TrustedPositionAnalysisCacheProvider
import com.worksoc.goaicoach.application.analysis.cacheQualityFor
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.shared.engine.engineOperationRequest
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

internal class LocalEngineSessionClient(
    private val coreApi: EngineCoreApi,
    override val capabilities: EngineSessionCapabilities = EngineSessionCapabilities(
        supportsDeviceBenchmark = false,
    ),
    private val positionAnalysisCacheStore: PositionAnalysisCacheStore = NoopPositionAnalysisCacheStore,
    private val trustedPositionAnalysisCacheProviders: List<TrustedPositionAnalysisCacheProvider> = emptyList(),
    private val diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
    private val clock: EngineClock = SystemEngineClock,
) : EngineSessionClient {
    private val coreSession = LocalEngineCoreSessionDelegate(
        coreApi = coreApi,
        clock = clock,
    )
    private val positionAnalysisCache = LocalPositionAnalysisCacheCoordinator(
        localStore = positionAnalysisCacheStore,
        trustedProviders = trustedPositionAnalysisCacheProviders,
    )
    private val analysisDiagnostics = EngineAnalysisDiagnosticRecorder(
        diagnosticEventLog = diagnosticEventLog,
    )

    override fun positionAnalysisCacheStatsText(nowMillis: Long): String =
        positionAnalysisCache.statsText(nowMillis)

    override fun positionAnalysisCacheQualityFor(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
        nowMillis: Long,
    ): PositionAnalysisCacheQuality? =
        positionAnalysisCache.qualityFor(
            state = state,
            limit = limit,
            searchMode = searchMode,
            nowMillis = nowMillis,
        )

    override suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult =
        coreSession.startSession(profile, state)

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStartupResult =
        coreSession.startNewGame(profile, boardSize, ruleset)

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
        val context = positionAnalysisCache.contextFor(
            state = state,
            limit = limit,
            searchMode = searchMode,
            cacheLimitOverride = cacheLimitOverride,
        )
        val nowMillis = clock.currentTimeMillis()
        positionAnalysisCache.reusableResultFor(
            context = context,
            readCache = readCache,
            nowMillis = nowMillis,
        )?.let { result -> return result }

        val operationRequest = engineOperationRequest(
            kind = EngineOperationKind.PositionAnalysis,
            state = state,
            sessionGeneration = 0L,
            timeoutPolicy = EngineTimeoutPolicy(
                timeoutMillis = context.effectiveLimit.timeMillis,
                label = "${searchMode.name}:${context.effectiveLimit.visits}v",
            ),
            fallbackPolicy = if (searchMode == EngineSearchMode.JsonPositionAnalysis) {
                EngineFallbackPolicy.CachedAnalysis
            } else {
                EngineFallbackPolicy.None
            },
            backendId = capabilities.backend.label,
        )
        val result = runObservedEngineOperation(
            request = operationRequest,
            diagnosticEventLog = diagnosticEventLog,
            currentTimeMillis = clock::currentTimeMillis,
        ) {
            coreSession.syncAndAnalyzePosition(
                state = state,
                limit = context.effectiveLimit,
            )
        }
        analysisDiagnostics.recordVisitFill(
            state = state,
            requestedVisits = context.cacheLimit.visits,
            rootVisits = result.rootVisits,
            searchMode = searchMode,
        )
        positionAnalysisCache.storeIfEligible(
            context = context,
            result = result,
            nowMillis = nowMillis,
        )
        return result
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
        coreSession.syncAndEstimateGraphScore(state, profile)

    override suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        coreSession.configureSyncAndEstimateGraphScore(state, profile)

    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult =
        coreSession.runAutoAiTurn(
            currentState = currentState,
            playLevel = playLevel,
            currentProfile = currentProfile,
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

    override suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult =
        coreSession.syncAfterHumanMove(
            afterMove = afterMove,
            profile = profile,
            move = move,
            previousReviewCandidates = previousReviewCandidates,
            diagnosticEventLog = diagnosticEventLog,
        )

    override suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate =
        coreSession.estimateScoreForState(
            state = state,
            profile = profile,
            syncFirst = syncFirst,
        )

    override suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution =
        coreSession.resolveEndgameForState(
            state = state,
            profile = profile,
            prePassCandidates = prePassCandidates,
            diagnosticEventLog = diagnosticEventLog,
        )

    override suspend fun undoMove(): EngineStatus =
        coreSession.undoMove()

    override suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile =
        coreSession.runStartupBenchmark(
            restoreState = restoreState,
            nowMillis = nowMillis,
            onProgress = onProgress,
        )
}

@Deprecated(
    message = "Use LocalEngineSessionClient. The old name hid that this implementation is tied to a local EngineCoreApi.",
    replaceWith = ReplaceWith("LocalEngineSessionClient"),
)
internal typealias AdapterEngineSessionClient = LocalEngineSessionClient
