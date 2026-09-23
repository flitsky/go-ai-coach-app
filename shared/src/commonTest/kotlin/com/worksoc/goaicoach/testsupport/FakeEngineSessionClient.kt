package com.worksoc.goaicoach.testsupport

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationResult
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheQuality
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.application.engine.AutoAiTurnResult
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProgress
import com.worksoc.goaicoach.application.engine.EngineSessionCapabilities
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.EngineStartupResult
import com.worksoc.goaicoach.application.engine.LocalEngineMoveResult
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.SearchTimeSettings

/**
 * 3계층 [EngineSessionClient]의 **단 하나뿐인** 공용 테스트 페이크.
 *
 * ## 왜 있는가
 * 이 인터페이스를 손으로 구현한 페이크가 테스트 8파일에 909줄로 복제돼 있었다. 멤버가 15개라
 * 테스트마다 **쓰지도 않는 14개를 베껴 쓰고 있었고**, 그 때문에 [EngineSessionClient]에 멤버를
 * 하나 더하거나 기본 구현을 하나 지우는 순간 8파일이 동시에 컴파일 에러가 났다.
 * 그것이 정확히 함정 70이 말하는 상태다 — 공용 인터페이스의 기본 구현을 걷어내려면
 * (리팩토링 백로그 #20) **이 자리가 먼저 있어야 한다.** 이제 그 변경은 이 파일 한 곳에서 끝난다.
 *
 * ## 기본 구현의 규약: 스텁하지 않은 멤버는 터진다
 * 스텁하지 않은 suspend 멤버는 [error]로 즉시 실패한다. 이것은 편의가 아니라 **옛 페이크들이
 * `error("not used")`로 표현하던 계약을 그대로 옮긴 것**이다. 조용히 그럴듯한 기본값을 돌려주면
 * 테스트가 무엇을 부르는지 모르는 채로 초록이 되고, 그때부터 그 테스트는 아무것도 지키지 않는다.
 *
 * 예외는 셋이다 — [capabilities], [positionAnalysisCacheStatsText],
 * [positionAnalysisCacheQualityFor]. 이 셋은 옛 페이크 8개가 **전부 같은 값을 돌려주고 있었다**
 * (`false` / `"disabled"` / `null`). 셋을 일부러 틀린 값으로 바꾸고 `:shared:check`를 돌려 본
 * 결과 **빨개지는 테스트가 하나도 없었다** — 인터페이스가 값을 요구해서 있을 뿐, 어떤 테스트도
 * 관찰하지 않는 자리다. 그래서 여기서만 `error`가 아닌 값을 준다.
 * 다른 값이 필요한 테스트는 [capabilities]처럼 그대로 `override` 하면 된다.
 *
 * ## 쓰는 법
 * 테스트가 필요한 멤버만 `override` 하는 `private class`를 테스트 파일에 남긴다. 기록용
 * 프로퍼티(무엇이 어떤 인자로 불렸는가)는 그 서브클래스가 계속 들고 있는다 — 그 이름이
 * 단언에 직접 등장하기 때문에 공용화 대상이 아니다.
 */
open class FakeEngineSessionClient : EngineSessionClient {
    override val capabilities: EngineSessionCapabilities =
        EngineSessionCapabilities(supportsDeviceBenchmark = false)

    override fun positionAnalysisCacheStatsText(nowMillis: Long): String = "disabled"

    override fun positionAnalysisCacheQualityFor(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
        nowMillis: Long,
    ): PositionAnalysisCacheQuality? = null

    override suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult = notStubbed("startSession")

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
        handicapCount: Int,
        komi: Double,
    ): EngineStartupResult = notStubbed("startNewGame")

    override suspend fun analyzePosition(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
    ): AnalysisResult = notStubbed("analyzePosition")

    override suspend fun optimizePositionAnalysisCache(
        plan: PositionAnalysisCacheOptimizationPlan,
    ): PositionAnalysisCacheOptimizationResult = notStubbed("optimizePositionAnalysisCache")

    override suspend fun syncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate = notStubbed("syncAndEstimateGraphScore")

    override suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate = notStubbed("configureSyncAndEstimateGraphScore")

    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult = notStubbed("runAutoAiTurn")

    override suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult = notStubbed("syncAfterHumanMove")

    override suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate = notStubbed("estimateScoreForState")

    override suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution = notStubbed("resolveEndgameForState")

    override suspend fun undoMove(): EngineStatus = notStubbed("undoMove")

    override suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile = notStubbed("runStartupBenchmark")

    /**
     * 스텁하지 않은 멤버가 불렸다. 무엇이 불렸는지를 메시지에 남긴다 —
     * 옛 페이크들의 `error("not used")`는 **어느 멤버였는지를 말해주지 않아서**
     * 실패를 읽는 데 시간이 들었다.
     */
    private fun notStubbed(member: String): Nothing =
        error("${this::class.simpleName}.$member is not stubbed")
}
