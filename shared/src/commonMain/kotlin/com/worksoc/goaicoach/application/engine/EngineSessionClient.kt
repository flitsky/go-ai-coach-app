package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationResult
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheQuality
import com.worksoc.goaicoach.application.contract.PositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings

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
 *
 * ## 멤버가 없다 — 역할 넷의 합성이다 (refactor backlog #35)
 * 멤버 15개는 역할 인터페이스 넷에 **하나씩만** 선언돼 있다 — [EngineLifecycleClient](수명 5)·
 * [EngineGamePlayClient](대국 진행 3)·[EngineScoringClient](계가 3)·[EngineAnalysisClient](분석 4).
 * 이 타입은 넷을 다 가진 객체 하나가 필요한 **조립 루트**를 위해 남는다 — `MainActivity`의
 * `remoteClient ?: LocalEngineSessionClient(…)`, `GoCoachApp`의 파라미터와 배선 컨텍스트, androidTest가
 * `GoCoachApp`에 주입하는 `FakeEngineSessionClient` 서브클래스. Kotlin 선언에는 교집합 타입이 없어서
 * 그 자리는 이 합성으로만 적을 수 있다.
 *
 * ⚠️ **새 소비자(컨트롤러·러너 확장 함수·요청 객체)는 이 타입이 아니라 필요한 역할 타입을 받는다.**
 * 합성이 넷 모두의 서브타입이라 조립 루트는 지금처럼 이 객체를 넘기면 되고, 배선 코드는 바뀌지 않는다.
 *
 * ⚠️ **본문에 멤버를 더하지 말 것.** 새 멤버는 역할 **하나에만** 선언한다. 같은 멤버를 두 역할에
 * 선언하면 기본 인자를 여러 상위 타입에서 물려받는 컴파일 에러가 나거나, 두 선언의 KDoc·계약이
 * 따로 늙기 시작한다.
 *
 * ## 역할을 나눠도 엔진은 하나다
 * 역할은 소비자가 **부를 수 있는 멤버**를 좁힐 뿐, 상태·동시성·백엔드를 나누지 않는다.
 *  - **분석도 대국 엔진의 판을 바꾼다.** [EngineAnalysisClient.analyzePosition]은 요청 국면으로 엔진 판을
 *    먼저 동기화한 뒤 분석한다. *"분석 역할은 부수효과가 없다"*, *"역할마다 다른 백엔드로 보낼 수 있다"* 로
 *    읽지 말 것 — 대국 엔진과 분석 엔진을 가르는 것은 이 분할과 다른 축이다(`ENGINE_API_CALL_POLICY.md` 중기안).
 *  - **구현을 역할별 클래스로 쪼개지 말 것.** `LocalEngineSessionClient`의 캐시 붙은 분석 경로를 대국
 *    ([EngineGamePlayClient.runAutoAiTurn] 안의 분석)과 분석([EngineAnalysisClient.analyzePosition]·
 *    [EngineAnalysisClient.optimizePositionAnalysisCache])이 함께 쓴다. 쪼개면 캐시가 둘이 되어 적중이 떨어진다.
 *  - **엔진 호출의 직렬화·대기 정책은 역할이 아니라 메서드 단위로 정한다** — 같은 역할 안에서도 갈린다.
 *    refactor backlog #15의 설계에서 형세 추정([EngineScoringClient.estimateScoreForState])은 바로 포기하고
 *    재동기화([EngineScoringClient.syncAndEstimateGraphScore])는 기다린다 — 둘 다 계가 역할이다. 수명 역할의
 *    [EngineLifecycleClient.forceResetEngine]은 그 락을 아예 잡지 않는다(함정 71).
 */
interface EngineSessionClient :
    EngineLifecycleClient,
    EngineGamePlayClient,
    EngineScoringClient,
    EngineAnalysisClient

/**
 * 엔진 **수명** 역할 — 세션 기동, 새 대국, 수동 복구, 기기 벤치마크, 그리고 백엔드가 무엇을 할 수 있는가.
 *
 * 소비자: 기동(`runEngineStartupApplication`, `GoCoachApp`에서 부른다), 새 대국(`NewGameController` →
 * `StartEngineBackedGameRunnerApplication`), 기기 벤치마크(`EngineBenchmarkController` →
 * `EngineDeviceBenchmarkApplication`), 워치독 복구 버튼(`GoCoachApp`). 전체 그림은 [EngineSessionClient].
 */
interface EngineLifecycleClient {
    /**
     * ⚠️ **`backend`는 진단 로그로 새어 나간다.** 이 값의 `label`이
     * [com.worksoc.goaicoach.shared.policy.EngineOperationRequest.backendId]가 되고, 그것이 느림/타임아웃
     * 진단 이벤트의 키가 된다. 원격 백엔드가 `local-engine`으로 찍히면 로그를 읽는 사람이 어느 엔진이
     * 느렸는지 알 수 없다.
     *
     * 프로덕션에서 이 값을 `backendId`로 찍는 곳은 **둘뿐이다**(2026-09-26 실측, refactor backlog #35):
     *  - 기동 — `runEngineStartupApplication`의 `engine_startup` 오퍼레이션.
     *    `EngineSessionLifecycleApplicationTest`의 `engineStartupOperationCarriesTheBackendIdFromCapabilities`가
     *    이 배선을 고정한다.
     *  - 구현 내부의 `position_analysis` 요청 — `LocalEngineSessionClient`가 캐시를 거쳐 엔진 분석을 부를 때다
     *    (분석, 캐시 최적화, 자동 AI 착수 안의 분석).
     *
     * 그 밖의 오퍼레이션 — 새 대국(`StartEngineBackedGameRunnerApplication`이 `backendId` 없이 만든다),
     * 자동 AI 착수·종국, 착수 동기화, 계가, 추천수, 캐시 최적화, 벤치마크 — 은 원격이어도 기본값
     * `local-engine`으로 찍힌다. 고치면 진단 로그가 바뀌므로 이 계약을 나누는 일과는 따로 다룬다.
     *
     * 이 값을 직접 읽는 소비자는 기동과 기기 벤치마크 게이트(`supportsDeviceBenchmark`)뿐이라 수명 역할에
     * 둔다.
     */
    val capabilities: EngineSessionCapabilities

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

/**
 * **대국 진행** 역할 — 사람 착수 뒤 동기화, 자동 AI 착수, 종국 해결.
 *
 * 소비자: 사람 착수(`HumanMoveController` → `HumanMoveApplication`), 자동 AI 차례와 그 종국
 * (`AutoAiTurnController` → `AutoAiScheduledTurnRunnerApplication`·`AutoAiEndgameRunnerApplication` →
 * `AutoAiRunnerApplication`).
 *
 * 종국 해결([resolveEndgameForState])이 계가 역할이 아니라 여기 있는 이유: 사람이 둬서 생긴 종국은
 * [syncAfterHumanMove]가 안에서 이미 풀어 [LocalEngineMoveResult.endgame]으로 돌려준다. 종국 처리를 두 역할에
 * 쪼개지 않으려고 자동 AI 쪽 종국도 같은 역할에 둔다. 전체 그림은 [EngineSessionClient].
 */
interface EngineGamePlayClient {
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
}

/**
 * **계가(형세)** 역할 — 국면의 점수 추정과, 판을 다시 맞춘 뒤의 그래프용 추정.
 *
 * 소비자: 형세 추정(`ScoreEstimateController` → `ScoreEstimateRunnerApplication`), 무르기·계가 규칙 변경 뒤
 * 재동기화(`UndoController`·`ScoringRuleController` → `PostUndo`/`ScoringRule` 러너 → `ScoreSyncRunnerApplication`),
 * 이어하기 복원(`SavedSessionController` → `RestoredGameScoreSyncRunnerApplication`). 전체 그림은 [EngineSessionClient].
 */
interface EngineScoringClient {
    suspend fun syncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate

    suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate

    suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate
}

/**
 * **국면 분석** 역할 — 추천수 분석, position-analysis 캐시의 최적화·품질·통계.
 *
 * 소비자: 추천수(`TopMovesController` → `TopMoveAnalysisEngine`), 대국 후 캐시 최적화
 * (`PositionCacheOptimizationController` → `PositionAnalysisCacheOptimization*`), 디버그 리포트의 캐시 통계
 * (`SettingsAndDiagnosticsControllerWiring`이 메서드 참조로 넘긴다).
 *
 * ⚠️ **부수효과가 없는 역할이 아니다** — [analyzePosition]은 엔진 판을 요청 국면으로 먼저 맞춘다.
 * 전체 그림은 [EngineSessionClient].
 */
interface EngineAnalysisClient {
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

    suspend fun analyzePosition(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode = EngineSearchMode.GtpStatefulFast,
    ): AnalysisResult

    suspend fun optimizePositionAnalysisCache(
        plan: PositionAnalysisCacheOptimizationPlan,
    ): PositionAnalysisCacheOptimizationResult
}
