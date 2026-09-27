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
import com.worksoc.goaicoach.shared.policy.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.policy.engineOperationRequest
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    /**
     * **오퍼레이션 락**(refactor backlog #15). 공개 suspend 메서드 하나가 곧 오퍼레이션 하나이고, 그 명령 묶음(동기화 =
     * `newGame` + 수마다 `playMove`, 그 뒤의 분석·추정·종국 판정)은 다른 오퍼레이션의 명령과 섞이지 않는다.
     *
     * 예전에는 섞였다. 5계층의 busy 게이트는 check-then-act이고 **세대로 걸러** 세므로(무르기·새 대국 뒤의 낡은 작업은
     * 안 보인다), 게이트를 지난 두 오퍼레이션이 IO 스레드 둘에서 실제로 겹쳤다. 한 오퍼레이션의 수순 재생 사이에 다른
     * 오퍼레이션의 `newGame`이 끼면 둘 다 남의 판을 분석한다. 2계층의 왕복 락(#14)은 명령 하나만 묶는다. 락 하나가
     * 엔진 하나다 — GTP·analysis 두 프로세스와 2계층의 판 거울(`playedMoves` 등)을 함께 지킨다(분석은 GTP로 맞춘 판을
     * JSON 쿼리로 보낸다). 원격 클라이언트는 따로 만든 인스턴스라 제 락을 가진다.
     *
     * ## 누가 기다리고 누가 포기하는가
     * - **기다린다**([serialized]): 기동·새 대국·AI 착수·착수 뒤 동기화·AI 종국·재동기화 둘·기기 벤치마크(끝의 복원
     *   동기화까지 — 복원 전에 다른 오퍼레이션이 끼면 벤치마크 판 위에서 돈다). 기다리다 취소되면 줄에서 빠지고 돌지 않는다.
     * - **곧바로 포기한다**([serializedOrBusy] → [EngineOperationBusy]): 추천 수 분석([analyzePosition])과 형세 추정
     *   ([estimateScoreForState]). 받는 쪽이 기존의 "잠시 뒤"·"엔진이 바쁘다" 흐름으로 보낸다 — 기다리게 하면 AI가 생각하는
     *   동안 누른 형세가 그 탐색이 끝날 때까지 멈춘다.
     * - **목표마다 따로**: 캐시 최적화([optimizePositionAnalysisCache]) — 목표와 목표 사이에 기다리던 AI 차례가 돈다.
     * - **잡지 않는다**: [forceResetEngine](함정 71 — 멈춘 오퍼레이션을 풀려고 부르는 함수다), [capabilities], 캐시 통계 둘.
     *
     * ## 멈춘 오퍼레이션이 모두를 얼리지 않는 이유 — 오퍼레이션 마감은 따로 두지 않는다(함정 71)
     * 쥔 쪽을 붙잡는 것은 엔진 왕복뿐이고, #14가 왕복마다 마감을 둬 넘으면 **그 호출의** 프로세스를 SIGKILL로 내린다. 쥔
     * 쪽이 가장 오래 쥐는 것은 제 일 + 왕복 마감 하나다(명령 30초, 캡 있는 탐색 캡+20초, 캡 없는 탐색 120초 —
     * `KataGoProtocolCommands`). 그 전에 푸는 길이 둘이다.
     * - 「엔진 다시 시작하기」: [forceResetEngine]은 락 없이 프로세스를 내린다 → 막힌 읽기가 EOF로 풀려 쥔 쪽이 실패하고
     *   락을 놓는다 → 기다리던 쪽이 새 프로세스에서 처음부터 판을 맞춘다.
     * - 취소(무르기·나가기·새 대국 — #74): 취소된 쪽은 답을 기다리지 않고 곧바로 돌아가 락을 놓는다. 답 받기는 2계층의
     *   배수가 그 프로세스의 왕복 락을 쥔 채 맡는다(`KataGoProcessEngineAdapter.roundTrip`) — 같은 프로세스로 가는 다음
     *   명령만 그것을 기다린다.
     * 정당한 오퍼레이션의 길이는 너무 다르다 — 기기 벤치마크는 수십 초, 캡 없는 탐색은 120초, 첫 실행의 [startSession]은
     * 엔진 설치를 기다린다(`DeferredEngineCoreApi` — 그 대기는 끊으면 엔진이 영영 안 뜬다). 락에 마감을 두르면 그중
     * 하나를 반드시 자른다. 예산을 한곳에 모으는 일은 #17이다.
     *
     * ⚠️ **재진입하지 않는다.** 오퍼레이션 안에서 같은 클라이언트의 공개 메서드를 부르면 크게 실패한다([refuseReentry]).
     * AI 차례 안의 분석은 공개 [analyzePosition]이 아니라 [analyzePositionWithCache]를 부른다 — 공개 쪽을 부르면 제 락에
     * 막혀 포기하고, AI는 조용히 `genMove`로 떨어진다.
     * ⚠️ **5계층의 `isEngineBusy`는 이 락이 아니다** — 세대로 거른 장부다. 직렬화의 근거로 쓰지 말 것. 이 락을 들여다보는
     * 눈대중은 [isEngineOperationInFlight]다.
     */
    private val operationLock = Mutex()

    override val isEngineOperationInFlight: Boolean
        get() = operationLock.isLocked

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
        serialized("startSession") { coreSession.startSession(profile, state) }

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
        handicapCount: Int,
        komi: Double,
    ): EngineStartupResult =
        serialized("startNewGame") { coreSession.startNewGame(profile, boardSize, ruleset, handicapCount, komi) }

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
        serialized("syncAndEstimateGraphScore") { coreSession.syncAndEstimateGraphScore(state, profile) }

    override suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        serialized("configureSyncAndEstimateGraphScore") { coreSession.configureSyncAndEstimateGraphScore(state, profile) }

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
            )
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
            )
        }

    override suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate =
        serializedOrBusy("estimateScoreForState") {
            coreSession.estimateScoreForState(
                state = state,
                profile = profile,
                syncFirst = syncFirst,
            )
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
            )
        }

    /** [operationLock]을 기다려 쥐고 [block]을 돈다. 기다리다 취소되면 줄에서 빠지고 [block]은 돌지 않는다. */
    private suspend fun <T> serialized(
        operation: String,
        block: suspend () -> T,
    ): T {
        refuseReentry(operation)
        return operationLock.withLock { holding(operation, block) }
    }

    /** [operationLock]이 비어 있을 때만 쥐고 [block]을 돈다. 누가 쥐고 있으면 기다리지 않고 [EngineOperationBusy]. */
    private suspend fun <T> serializedOrBusy(
        operation: String,
        block: suspend () -> T,
    ): T {
        refuseReentry(operation)
        if (!operationLock.tryLock()) throw EngineOperationBusy(operation)
        try {
            return holding(operation, block)
        } finally {
            operationLock.unlock()
        }
    }

    /** 쥔 동안에는 코루틴 문맥에 표지를 단다 — 안에서 다시 들어오는 호출을 [refuseReentry]가 알아본다. */
    private suspend fun <T> holding(
        operation: String,
        block: suspend () -> T,
    ): T =
        withContext(HeldEngineOperation(owner = this, operation = operation)) { block() }

    /**
     * 이미 이 클라이언트의 오퍼레이션 안이면 **크게 실패한다** — [Mutex]는 재진입을 모른다. 기다리는 쪽은 자기 자신을 영영
     * 기다리고, 포기하는 쪽은 조용히 [EngineOperationBusy]가 된다(AI 차례 안이면 조용히 `genMove`로 떨어진다). 둘 다
     * 테스트 밖에서는 보이지 않는 고장이라, 여기서 드러낸다.
     */
    private suspend fun refuseReentry(operation: String) {
        val held = currentCoroutineContext()[HeldEngineOperation] ?: return
        check(held.owner !== this) {
            "`$operation` was called from inside `${held.operation}` on the same engine session. The operation lock is not " +
                "re-entrant — call the private helper that runs under the held lock instead (refactor backlog #15)."
        }
    }
}

/** 지금 이 코루틴이 [owner]의 오퍼레이션 락을 쥐고 [operation]을 돌고 있다는 표지(refactor backlog #15) — 재진입을 알아본다. */
private class HeldEngineOperation(
    val owner: LocalEngineSessionClient,
    val operation: String,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<HeldEngineOperation>
}
