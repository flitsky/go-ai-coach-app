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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DeferredEngineCoreApi]의 계약(백로그 #101).
 *
 * ⚠️ **이 래퍼가 하는 일은 "기다린다"가 전부다 — 틀리기 쉬운 곳은 기다리지 *않아야* 하는 자리다.**
 * `forceReset`은 비-suspend라 기다릴 수 없고, `Deferred.getCompleted()`를 그냥 부르면
 * **미완료 상태에서 예외를 던진다.** 그 예외는 "멈춘 엔진을 버리는" 마지막 수단 경로에서 터진다.
 */
class DeferredEngineCoreApiTest {

    private class FakeEngine : EngineCoreApi {
        var resets = 0
        var initialised = 0
        override suspend fun initialize(profile: EngineProfile): EngineStatus {
            initialised++
            return EngineStatus.ready("fake")
        }
        override suspend fun configure(profile: EngineProfile) = EngineStatus.ready("fake")
        override suspend fun newGame(boardSize: BoardSize, ruleset: Ruleset, handicapCount: Int, komi: Double) =
            EngineStatus.ready("fake")
        override suspend fun playMove(move: Move) = EngineStatus.ready("fake")
        override suspend fun genMove(player: StoneColor): MoveResult = throw UnsupportedOperationException()
        override suspend fun undoMove() = EngineStatus.ready("fake")
        override suspend fun analyze(limit: AnalysisLimit): AnalysisResult = throw UnsupportedOperationException()
        override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate = throw UnsupportedOperationException()
        override suspend fun deadStones(): DeadStonesResult = throw UnsupportedOperationException()
        override suspend fun scoreFinal(): FinalScoreResult = throw UnsupportedOperationException()
        override suspend fun stop() = EngineStatus.ready("fake")
        override fun forceReset() { resets++ }
    }

    /**
     * ⚠️ **가장 중요한 계약.** 엔진이 아직 없을 때 `forceReset`이 던지면, 워치독처럼 **이미 뭔가
     * 잘못된 상황**에서 두 번째 예외가 터진다. 아직 태어나지 않은 프로세스에는 버릴 것이 없으므로
     * **조용히 아무것도 하지 않아야** 한다.
     */
    @Test
    fun forceResetBeforeTheEngineExistsDoesNothingAndDoesNotThrow() {
        val pending = CompletableDeferred<EngineCoreApi>()
        val api = DeferredEngineCoreApi(pending)

        api.forceReset()   // 던지면 이 줄에서 테스트가 깨진다

        assertFalse("아직 완료되지 않았는데 완료로 봤다.", pending.isCompleted)
    }

    /** 준비가 끝난 뒤에는 진짜 엔진에 위임한다 — 조용히 삼키면 복구 수단이 사라진다. */
    @Test
    fun forceResetDelegatesOnceTheEngineIsReady() = runBlocking {
        val engine = FakeEngine()
        val ready = CompletableDeferred<EngineCoreApi>()
        val api = DeferredEngineCoreApi(ready)
        ready.complete(engine)
        // ⚠️ `invokeOnCompletion`이 돌 틈을 준다 — `forceReset`은 그 핸들러가 채운 참조를 쓴다.
        yield()

        api.forceReset()

        assertEquals("준비된 뒤에도 위임하지 않았다 — 마지막 복구 수단이 사라진다(#101).", 1, engine.resets)
    }

    /**
     * ⚠️ **준비 전에 건 호출이 버려지지 않고 기다렸다가 실행돼야 한다.** 이것이 이 항목의 목적
     * 전부다 — 사용자가 엔진 복사 중에 대국을 시작해도, 그 호출은 복사가 끝나면 이어진다.
     */
    @Test
    fun aCallMadeBeforeReadinessRunsAfterwards() = runBlocking {
        val engine = FakeEngine()
        val later = CompletableDeferred<EngineCoreApi>()
        val api = DeferredEngineCoreApi(later)

        val inFlight = async { api.initialize(EngineProfile()) }
        assertFalse("엔진이 없는데 벌써 실행됐다.", inFlight.isCompleted)

        later.complete(engine)
        val status = inFlight.await()

        assertTrue("준비 뒤에도 실행되지 않았다 — 호출이 버려졌다(#101).", engine.initialised == 1)
        assertEquals("fake", status.message)
    }
}
