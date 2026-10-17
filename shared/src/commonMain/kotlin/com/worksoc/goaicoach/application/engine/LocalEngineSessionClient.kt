package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.analysis.NoopPositionAnalysisCacheStore
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationResult
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheQuality
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheStore
import com.worksoc.goaicoach.application.analysis.TrustedPositionAnalysisCacheProvider
import com.worksoc.goaicoach.application.analysis.cacheQualityFor
import com.worksoc.goaicoach.application.contract.PositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.enginecontract.analysisSearchTimeoutMillis
import com.worksoc.goaicoach.shared.policy.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.policy.engineOperationRequest
import kotlin.random.Random

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
     * 지금의 **세션 세대**(`GameSessionRuntimeState.sessionGeneration` — 무르기마다 바뀐다)를
     * 읽는 공급자(refactor backlog #18). 이 클라이언트가 직접 만드는 `position_analysis`
     * 요청의 세대이고, 진단 로그의 `operationId`(`position_analysis:g<세대>:…`)와
     * `sessionGeneration` 문맥이 이 값을 싣는다.
     *
     * ⚠️ **예전에는 여기서 `0L`을 박았다** — 그래서 모든 `position_analysis` 로그가 `g0`으로 찍혀,
     * 같은 판의 다른 오퍼레이션(5계층이 제 세대를 넣는 `auto_ai_turn` 등)과 대조할 수 없었다.
     *
     * ⚠️ **기본값을 두지 않는다.** 빠뜨리면 조용히 `g0`으로 돌아가는 것이 바로 그 결함이었다.
     *
     * ⚠️ **로그에만 쓰인다** — 이 요청은 [runObservedEngineOperation]에만 넘어가고 결과 폐기 판정에는
     * 쓰이지 않는다(폐기는 5계층이 제 요청에 제 세대를 넣어 따로 한다). 그래서 이 값이 바뀌어도
     * 사용자에게 보이는 동작은 바뀌지 않는다.
     *
     * ⚠️ [capabilitiesProvider]와 같은 조건이다 — **싸고, 막히지 않고, 아무 스레드에서나 안전해야
     * 한다.** 분석 한 번마다 부른다.
     */
    private val currentSessionGeneration: () -> Long,
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
    /** 급수 캐릭터가 사람 정책에서 수를 뽑는 주사위(백로그 #215) — 테스트가 고정한다. */
    private val random: Random = Random.Default,
) : EngineSessionClient {
    /**
     * ⚠️ **읽을 때마다 새로 묻는다 — 어딘가에 담아두지 말 것.** 답은 시간이 지나면서 바뀐다
     * (엔진이 준비되는 순간). 한 번 읽어 `remember`나 필드에 넣으면 그 자리에서 다시 굳는다.
     */
    override val capabilities: EngineSessionCapabilities
        get() = capabilitiesProvider()

    /** 오퍼레이션 단위 직렬화·재진입 거부·배경 조각 양보([LocalEngineOperationLock], refactor backlog #15). */
    private val operationLock = LocalEngineOperationLock()

    /** 분석 계열 호출이 판을 두고 간 자리([AnalysisBoard], backlog #218) — [operationLock] 안에서만 읽고 쓴다. */
    private val boardLeftByAnalysis = AnalysisBoardTracker()

    override val isEngineOperationInFlight: Boolean
        get() = operationLock.isInFlight

    private val coreSession = LocalEngineCoreSessionDelegate(
        coreApi = coreApi,
        clock = clock,
        random = random,
        swapBoardTracker = boardLeftByAnalysis,
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
        serialized("startSession") { coreSession.startSession(profile, state).alsoBoardIsTheGames() }

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
        handicapCount: Int,
        komi: Double,
    ): EngineStartupResult =
        serialized("startNewGame") {
            coreSession.startNewGame(profile, boardSize, ruleset, handicapCount, komi).alsoBoardIsTheGames()
        }

    override suspend fun analyzePosition(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
    ): AnalysisResult =
        serializedOrBusy("analyzePosition") {
            analyzePositionWithCache(
                state = state,
                limit = limit,
                searchMode = searchMode,
            )
        }

    /**
     * 캐시를 거친 분석 — **락을 잡지 않는다.** 이미 쥔 오퍼레이션 안에서만 부른다: 공개 [analyzePosition]·
     * [optimizePositionAnalysisCache]의 목표 하나·[runAutoAiTurn] 안의 분석.
     */
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
            sessionGeneration = currentSessionGeneration(),
            // 진단의 timeoutMillis는 analyze()가 실제로 기다리는 마감이다(캡 + 20초) — 캡이 아니다(refactor backlog #17).
            timeoutPolicy = EngineTimeoutPolicy(
                timeoutMillis = context.effectiveLimit.analysisSearchTimeoutMillis(),
                label = "${searchMode.name}:${context.effectiveLimit.visits}v",
                slowThresholdMillis = context.effectiveLimit.timeMillis,
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
            // 맞추기 **전에** 모른다고 적는다 — 수순을 다시 두다 끊기면(취소·마감) 판은 어느 국면도 아니다([boardLeftByAnalysis]).
            boardLeftByAnalysis.current = AnalysisBoard.Unknown
            coreSession.syncAndAnalyzePosition(
                state = state,
                limit = context.effectiveLimit,
            ).also { boardLeftByAnalysis.current = AnalysisBoard.At(state) }
        }
        analysisDiagnostics.recordVisitFill(
            state = state,
            requestedVisits = context.cacheLimit.visits,
            rootVisits = result.rootVisits,
            searchMode = searchMode,
        )
        analysisDiagnostics.recordAnalysisFallback(
            state = state,
            fallback = result.fallback,
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
            // 목표마다 따로 쥔다 — 목표와 목표 사이에 기다리던 AI 차례·재동기화가 돈다(대국 후 최적화가 대국을 막지 않게).
            val result = serialized("optimizePositionAnalysisCache") {
                analyzePositionWithCache(
                    state = target.state,
                    limit = target.executionLimit,
                    searchMode = EngineSearchMode.JsonPositionAnalysis,
                    readCache = false,
                    cacheLimitOverride = target.cacheLimit,
                )
            }
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
        serialized("syncAndEstimateGraphScore") { coreSession.syncAndEstimateGraphScore(state, profile).alsoBoardIsTheGames() }

    override suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        serialized("configureSyncAndEstimateGraphScore") {
            coreSession.configureSyncAndEstimateGraphScore(state, profile).alsoBoardIsTheGames()
        }

    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult =
        serialized("runAutoAiTurn") {
            coreSession.runAutoAiTurn(
                currentState = currentState,
                playLevel = playLevel,
                currentProfile = currentProfile,
                searchTimeSettings = searchTimeSettings,
                searchMode = searchMode,
                isolateSearchCache = isolateSearchCache,
                // ⚠️ 공개 analyzePosition이 아니다 — 그쪽은 이 차례가 쥔 락에 막혀 포기하고, AI는 genMove로 떨어진다.
                analysisProvider = { limit ->
                    analyzePositionWithCache(
                        state = currentState,
                        limit = limit,
                        searchMode = searchMode,
                    )
                },
            ).alsoBoardIsTheGames()
        }

    override suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult =
        serialized("syncAfterHumanMove") {
            coreSession.syncAfterHumanMove(
                afterMove = afterMove,
                profile = profile,
                move = move,
                previousReviewCandidates = previousReviewCandidates,
                diagnosticEventLog = diagnosticEventLog,
            ).alsoBoardIsTheGames()
        }

    override suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate =
        serializedOrBusy("estimateScoreForState") {
            // 누른 형세 보기는 주 모델이 답한다(백로그 #215 보강 ②) — 갈아 올렸으면 새 프로세스의 판은 비어 있다.
            val swapped = coreSession.bringMainNetwork()
            // `syncFirst = false`는 "판이 이미 이 국면"이라는 가정이다 — 분석 계열이 판을 다른 데 두고 갔으면 거둔다([boardLeftByAnalysis]).
            val syncNow = syncFirst || swapped || boardLeftByAnalysis.isElsewhereThan(state)
            if (syncNow) boardLeftByAnalysis.current = AnalysisBoard.Unknown
            coreSession.estimateScoreForState(
                state = state,
                profile = profile,
                syncFirst = syncNow,
            ).also { if (syncNow) boardLeftByAnalysis.current = AnalysisBoard.At(state) }
        }

    override suspend fun remeasureGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        backgroundPiece("remeasureGraphScore") {
            // 지나간 국면으로 판을 옮긴다 — 분석 계열과 같은 표시를 남겨, 돌아온 대국의 형세 보기가 판부터 맞추게 한다.
            boardLeftByAnalysis.current = AnalysisBoard.Unknown
            coreSession.remeasureGraphScore(state, profile).also { boardLeftByAnalysis.current = AnalysisBoard.At(state) }
        }

    override suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution =
        serialized("resolveEndgameForState") {
            coreSession.resolveEndgameForState(
                state = state,
                profile = profile,
                prePassCandidates = prePassCandidates,
                diagnosticEventLog = diagnosticEventLog,
            )
        }

    /**
     * ⚠️ **[operationLock]을 절대 잡지 않는다**(함정 71). 락을 쥔 채 멈춘 오퍼레이션을 풀려고 부르는 함수다 — 기다리면
     * 멈춘 쪽이 제 마감까지(길면 2분) 모두를 얼린다. 메인 스레드에서 불리므로 서스펜드하지도 막히지도 않는다.
     */
    override fun forceResetEngine() = coreApi.forceReset()

    override suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile =
        serialized("runStartupBenchmark") {
            coreSession.runStartupBenchmark(
                restoreState = restoreState,
                nowMillis = nowMillis,
                onProgress = onProgress,
            ).alsoBoardIsTheGames()
        }

    /**
     * 대국 계열 오퍼레이션이 판을 맞추고 **끝났다** — 판은 다시 대국의 것이다([boardLeftByAnalysis]).
     * 실패·취소로 끝난 오퍼레이션은 부르지 않는다: 판을 맞췄다는 보장이 없으므로 앞선 표시를 그대로 둔다.
     */
    private fun <T> T.alsoBoardIsTheGames(): T = also { boardLeftByAnalysis.current = AnalysisBoard.None }

    private suspend fun <T> serialized(operation: String, block: suspend () -> T): T = operationLock.serialized(operation, block)

    private suspend fun <T> serializedOrBusy(operation: String, block: suspend () -> T): T = operationLock.serializedOrBusy(operation, block)

    private suspend fun <T> backgroundPiece(operation: String, block: suspend () -> T): T = operationLock.backgroundPiece(operation, block)
}
