package com.worksoc.goaicoach.engine

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveResult
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.Deferred

/**
 * 아직 준비되지 않은 엔진을 **기다렸다가** 위임하는 래퍼(백로그 #101).
 *
 * ## 왜 이 계층인가 — `EngineSessionClient`가 아니다
 * 처음에는 한 계층 위인 `EngineSessionClient`를 감싸려 했는데, 그쪽은 **비-suspend 멤버가 넷**이라
 * *"준비 전에 무엇으로 답하는가"* 를 넷 다 정해야 했다. [EngineCoreApi]는 **[forceReset] 하나만**
 * 비-suspend라, 여기서 감싸면 위 인터페이스의 동기 멤버 셋이 **저절로 해결된다** —
 * 캐시 통계 둘은 애초에 coreApi를 건드리지 않고, `capabilities`만 따로 정하면 된다.
 *
 * ## ⚠️ 대기가 취소될 위험이 없다는 것이 이 설계의 전제다
 * `EngineSessionClient`와 KataGo 사이에 `withTimeout`이 없다(조사로 확인, 2026-09-05).
 * `runObservedEngineOperation`의 5초는 **진단 로그 임계값일 뿐**이라 호출을 끊지 않는다.
 * ⚠️ **그 사이에 타임아웃을 넣는 변경을 하려거든 이 파일을 먼저 볼 것** — 첫 실행의 100MB 복사가
 * 몇 초 걸리므로, 그 구간에 걸린 호출이 취소되면 **엔진이 영영 시작되지 않는다.**
 *
 * ## ⚠️ [forceReset]은 준비 전에 아무 일도 하지 않는다
 * `Deferred.getCompleted()`를 쓰면 **미완료 상태에서 예외를 던진다.** 이 메서드는 "멈춘 엔진을
 * 마지막 수단으로 버린다"는 뜻인데, **아직 태어나지도 않은 프로세스에는 버릴 것이 없다.**
 * 그래서 완료된 참조를 따로 들고 있다가 있을 때만 위임한다.
 */
internal class DeferredEngineCoreApi(
    private val deferred: Deferred<EngineCoreApi>,
) : EngineCoreApi {

    /**
     * 완료된 참조. ⚠️ **`@Volatile`이다** — [forceReset]은 아무 스레드에서나 불릴 수 있고
     * (워치독·사용자 조작), 여기 쓰이는 값은 다른 스레드가 채운다.
     */
    @Volatile
    private var resolved: EngineCoreApi? = null

    init {
        // ⚠️ `getCompleted()`는 실험적 API라 옵트인이 필요하다. **미완료 상태에서는 던지므로**
        // `cause == null`(정상 완료)일 때만 부른다 — 그 조건이 곧 완료 보장이다.
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        deferred.invokeOnCompletion { cause ->
            if (cause == null) resolved = runCatching { deferred.getCompleted() }.getOrNull()
        }
    }

    private suspend fun api(): EngineCoreApi = deferred.await()

    override suspend fun initialize(profile: EngineProfile): EngineStatus = api().initialize(profile)

    override suspend fun configure(profile: EngineProfile): EngineStatus = api().configure(profile)

    override suspend fun newGame(
        boardSize: BoardSize,
        ruleset: Ruleset,
        handicapCount: Int,
        komi: Double,
    ): EngineStatus = api().newGame(boardSize, ruleset, handicapCount, komi)

    override suspend fun playMove(move: Move): EngineStatus = api().playMove(move)

    override suspend fun genMove(player: StoneColor): MoveResult = api().genMove(player)

    override suspend fun undoMove(): EngineStatus = api().undoMove()

    override suspend fun clearSearchCache(): EngineStatus = api().clearSearchCache()

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult = api().analyze(limit)

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate = api().estimateScore(limit)

    override suspend fun deadStones(): DeadStonesResult = api().deadStones()

    override suspend fun scoreFinal(): FinalScoreResult = api().scoreFinal()

    override suspend fun stop(): EngineStatus = api().stop()

    /**
     * ⚠️ **준비 전에는 조용히 아무것도 하지 않는다** — 던지지 않는다. 이 메서드의 계약이
     * *"멈춘 프로세스를 버린다"* 인데 프로세스가 아직 없으면 이미 그 상태이기 때문이다.
     * 인터페이스의 기본 구현도 빈 몸통이다.
     */
    override fun forceReset() {
        resolved?.forceReset()
    }
}
