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

/**
 * 아직 아무 것도 확인되지 않았을 때의 정직한 답. **"모른다"는 곧 "아직 못 한다"** 이므로
 * 기기 벤치마크는 꺼진 쪽이 기본값이다 — 없는 능력을 있다고 답하는 쪽이 더 나쁘다.
 */
private val UnverifiedLocalCapabilities = EngineSessionCapabilities(
    supportsDeviceBenchmark = false,
)

class LocalEngineSessionClient(
    private val coreApi: EngineCoreApi,
    /**
     * ⚠️ **값이 아니라 공급자다**(백로그 #101 ②단계).
     *
     * 예전에는 값이었고, 그래도 됐다 — `MainActivity`가 부트스트랩이 **끝난 뒤에** 이 클라이언트를
     * 만들었으니 `supportsDeviceBenchmark`(= 실제로 로컬 프로세스가 떴는가)를 이미 알고 있었다.
     * #101에서 그 순서가 뒤집힌다: 클라이언트를 **먼저** 만들고 엔진은 뒤따라 준비된다.
     * 그 시점에 값을 하나 골라 박으면 **영원히 그 값이다** — 어느 쪽으로 틀려도 대가가 있다.
     * `false`로 박으면 로컬 엔진이 떠도 벤치마크가 **영영 막히고**, `true`로 박으면 스텁으로
     * 폴백했을 때 없는 기능을 **열어준다**(`createEngineBootstrap`은 에셋이 없으면 스텁을 준다).
     *
     * 그래서 물어볼 때마다 다시 묻는다. ⚠️ **싸고, 막히지 않고, 아무 스레드에서나 안전해야 한다**
     * — 분석 한 번마다 [capabilities]를 읽는다(`backendId`).
     */
    private val capabilitiesProvider: () -> EngineSessionCapabilities = { UnverifiedLocalCapabilities },
    private val positionAnalysisCacheStore: PositionAnalysisCacheStore = NoopPositionAnalysisCacheStore,
    private val trustedPositionAnalysisCacheProviders: List<TrustedPositionAnalysisCacheProvider> = emptyList(),
    private val diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
    private val clock: EngineClock = SystemEngineClock,
) : EngineSessionClient {
    /**
     * ⚠️ **읽을 때마다 새로 묻는다 — 어딘가에 담아두지 말 것.** 답은 시간이 지나면서 바뀐다
     * (엔진이 준비되는 순간). 한 번 읽어 `remember`나 필드에 넣으면 그 자리에서 다시 굳는다.
     */
    override val capabilities: EngineSessionCapabilities
        get() = capabilitiesProvider()

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
        handicapCount: Int,
        komi: Double,
    ): EngineStartupResult =
        coreSession.startNewGame(profile, boardSize, ruleset, handicapCount, komi)

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

    override fun forceResetEngine() = coreApi.forceReset()

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
