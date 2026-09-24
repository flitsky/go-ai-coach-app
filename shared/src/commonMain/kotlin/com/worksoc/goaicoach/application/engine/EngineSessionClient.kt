package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.contract.PositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationResult
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheQuality
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate

enum class EngineSessionBackend(
    val label: String,
) {
    LocalEngine("local-engine"),
    RemoteServer("remote-server"),
}

data class EngineSessionCapabilities(
    val supportsDeviceBenchmark: Boolean,
    val backend: EngineSessionBackend = EngineSessionBackend.LocalEngine,
)

/**
 * 3계층(Extended API) — application-facing engine session boundary.
 *
 * UI code depends on this contract instead of the low-level EngineCoreApi
 * (2계층). Local and future remote-server engines should implement this
 * interface without exposing process sync, cache isolation, or transport
 * details to Compose/app-service orchestration (5계층).
 */
interface EngineSessionClient {
    /**
     * ⚠️ **`backend`는 진단 로그로 새어 나간다.** `runEngineStartup/NewGame/Undo` 경로가
     * 이 값의 `label`을 [com.worksoc.goaicoach.shared.policy.EngineOperationRequest.backendId]로
     * 찍고, 그것이 느림/타임아웃 진단 이벤트의 키가 된다. 원격 백엔드가 `local-engine`으로 찍히면
     * 로그를 읽는 사람이 어느 엔진이 느렸는지 알 수 없다 — `EngineSessionLifecycleApplicationTest`의
     * `engineStartupOperationCarriesTheBackendIdFromCapabilities`가 그 배선을 고정한다.
     */
    val capabilities: EngineSessionCapabilities

    /**
     * 디버그 리포트에 실리는 position-analysis 캐시 통계 한 줄.
     *
     * 소비자는 프로덕션에 **하나뿐이다** — app-android의 `SettingsAndDiagnosticsControllerWiring`이
     * `DebugReportController`에 넘긴다. 이 멤버 자체는 한 줄 위임이고, 문자열을 실제로 조립하는
     * 쪽은 `PositionAnalysisCacheResolverTest.statsTextCombinesLocalAndTrustedProviders`가 이미
     * 고정한다. 그래서 refactor backlog #60에서 **현 상태 유지로 판정했다**: 여기에 테스트를 하나
     * 더 붙여도 위임 한 줄을 지킬 뿐이다.
     */
    fun positionAnalysisCacheStatsText(nowMillis: Long): String

    /**
     * 대국 후 캐시 최적화 계획을 세울 때 국면별 캐시 품질을 묻는다.
     *
     * ⚠️ **지금 이 경로는 잠들어 있다** — 유일한 소비자인 `PositionCacheOptimizationController`가
     * 계획을 세우지만, 그 계획을 보여주는 프롬프트는 `PostGamePositionAnalysisCacheOptimizationPromptEnabled
     * = false` 뒤에 있다. 그래도 **빼지 않는다**: 플래그는 묘비가 아니라 스위치이고, 계획을 세우는
     * 쪽(`buildPositionAnalysisCacheOptimizationPlan`)의 `qualityFor` 사용은
     * `PositionAnalysisCacheOptimizationTest`가 자체 람다로 이미 고정하고 있다.
     * refactor backlog #60 판정: 현 상태 유지.
     */
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
        handicapCount: Int = 0,
        komi: Double = com.worksoc.goaicoach.shared.domain.DefaultKomi,
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

    /**
     * Raw endgame composition entry point for a prepared game snapshot.
     *
     * Do not wire this directly to default pass/pass UI as an unbounded call.
     * Default scoring should go through the assistant-judge SLA. Unbounded
     * chief-judge scoring belongs behind an explicit user objection and must
     * discard results when the match/session generation changes.
     */
    suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution

    suspend fun undoMove(): EngineStatus

    /**
     * Manual last-resort recovery for a wedged engine (e.g. the engine turn
     * watchdog fires). Must not suspend or block — see [com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi.forceReset].
     * Defaults to a no-op since not every backend has a real transport to reset.
     */
    fun forceResetEngine() {}

    suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile
}
