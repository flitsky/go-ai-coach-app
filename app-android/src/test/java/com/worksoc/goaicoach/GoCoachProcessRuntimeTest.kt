package com.worksoc.goaicoach

import com.worksoc.goaicoach.application.analysis.NoopPositionAnalysisCacheStore
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.engine.EngineBootstrap
import com.worksoc.goaicoach.engine.EngineIdentity
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.DeadStonesResult
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult
import com.worksoc.goaicoach.shared.enginecontract.MoveResult
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GoCoachProcessRuntime]의 계약(refactor backlog #110) — 엔진 묶음이 **프로세스에 한 벌**이라는 것.
 *
 * 앱에서는 액티비티가 다시 만들어질 때마다 새 화면이 같은 런타임을 읽고 [GoCoachProcessRuntime.startEngineBootstrap]을
 * 다시 부른다. 그래도 부트스트랩(= 어댑터 = KataGo)은 한 번이고, 모든 화면이 같은 클라이언트로 같은 엔진에 닿아야 한다.
 * 기기에서 프로세스 수를 세는 것은 `EngineProcessCountSmokeTest`다. 디스패처는 `Unconfined`로 받아 순서를 결정적으로 만든다.
 */
class GoCoachProcessRuntimeTest {

    private class FakeEngine : EngineCoreApi {
        var initialised = 0
        override suspend fun initialize(profile: EngineProfile): EngineStatus {
            initialised++
            return EngineStatus.ready("fake")
        }
        override suspend fun configure(profile: EngineProfile) = EngineStatus.ready("fake")
        override suspend fun newGame(boardSize: BoardSize, ruleset: Ruleset, handicapCount: Int, komi: Double) =
            EngineStatus.ready("fake")
        override suspend fun syncStaticPosition(state: GameState) = EngineStatus.ready("fake")
        override suspend fun playMove(move: Move) = EngineStatus.ready("fake")
        override suspend fun genMove(player: StoneColor): MoveResult = throw UnsupportedOperationException()
        override suspend fun undoMove() = EngineStatus.ready("fake")
        override suspend fun analyze(limit: AnalysisLimit): AnalysisResult = throw UnsupportedOperationException()
        override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate = throw UnsupportedOperationException()
        override suspend fun deadStones(): DeadStonesResult = throw UnsupportedOperationException()
        override suspend fun scoreFinal(): FinalScoreResult = throw UnsupportedOperationException()
        override suspend fun stop() = EngineStatus.ready("fake")
        override fun forceReset() = Unit
    }

    private val engine = FakeEngine()
    private var bootstraps = 0

    private fun runtime(mode: EngineMode = EngineMode.LocalProcess) = GoCoachProcessRuntime(
        diagnosticEventLog = NoopDiagnosticEventLog,
        positionAnalysisCacheStore = NoopPositionAnalysisCacheStore,
        createBootstrap = {
            bootstraps++
            EngineBootstrap(coreApi = engine, mode = mode, displayName = "KataGo (fake)", diagnostic = "fake ready")
        },
        remoteEngineUrl = null,
        mainDispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
    )

    /** 다시 만들어진 화면마다 부른다 — 그래도 부트스트랩(= 새 어댑터 = 새 KataGo)은 프로세스에 한 번이다. */
    @Test
    fun theBootstrapRunsOncePerProcessHoweverManyScreensStartIt() {
        val runtime = runtime()

        repeat(3) { runtime.startEngineBootstrap() }
        assertEquals("부트스트랩이 화면마다 다시 돈다 — 어댑터와 KataGo가 한 벌씩 쌓인다(#110).", 1, bootstraps)

        repeat(2) { runtime.startEngineBootstrap() }
        assertEquals("끝난 뒤에 붙은 화면이 부트스트랩을 다시 돌렸다(#110).", 1, bootstraps)
    }

    /** 생성자에는 부수효과가 없다 — 시작은 화면이 한다(시점이 옮기기 전과 같게, #125). */
    @Test
    fun buildingTheRuntimeStartsNothing() {
        runtime()
        assertEquals(0, bootstraps)
    }

    /** 모든 화면이 같은 클라이언트로 같은 엔진에 닿는다 — 두 번째 화면의 기동은 새 엔진이 아니라 같은 엔진의 `initialize`다. */
    @Test
    fun everyScreenReachesTheOneEngineThroughTheSameClient() = runBlocking {
        val runtime = runtime()
        val firstScreen = runtime.engineClient
        val secondScreen = runtime.engineClient
        assertSame("화면마다 클라이언트가 다르다 — 오퍼레이션 락도 어댑터도 따로 생긴다(#110).", firstScreen, secondScreen)
        assertSame(runtime.sessionGenerationRelay, runtime.sessionGenerationRelay)

        runtime.startEngineBootstrap()
        firstScreen.startSession(EngineProfile(), GameState.empty())
        runtime.startEngineBootstrap()
        secondScreen.startSession(EngineProfile(), GameState.empty())

        assertEquals(1, bootstraps)
        assertEquals("두 화면의 기동이 같은 엔진에 닿지 않았다.", 2, engine.initialised)
    }

    /** 부트스트랩 전에 나간 호출은 기다렸다가 부트스트랩이 만든 엔진에 닿는다(#101의 `Deferred`가 그대로 이어졌다). */
    @Test
    fun aCallMadeBeforeTheBootstrapWaitsAndThenReachesItsEngine() = runBlocking {
        val runtime = runtime()
        val startup = async { runtime.engineClient.startSession(EngineProfile(), GameState.empty()) }
        yield()
        assertEquals("부트스트랩 전인데 엔진에 닿았다.", 0, engine.initialised)

        runtime.startEngineBootstrap()
        startup.await()

        assertEquals(1, engine.initialised)
    }

    /** 준비 전에는 예측하지 않고(Unresolved), 없는 능력을 열지 않는다. 준비 뒤에는 부트스트랩이 말한 대로다(#101). */
    @Test
    fun theIdentityAndTheBenchmarkFollowTheBootstrap() {
        val local = runtime(EngineMode.LocalProcess)
        assertEquals(EngineIdentity.Unresolved, local.engineIdentity())
        assertFalse("준비 전에 벤치마크를 열었다.", local.engineClient.capabilities.supportsDeviceBenchmark)

        local.startEngineBootstrap()
        assertEquals(EngineMode.LocalProcess, local.engineIdentity().mode)
        assertEquals("KataGo (fake)", local.engineIdentity().name)
        assertTrue("로컬 KataGo인데 벤치마크가 막혔다.", local.engineClient.capabilities.supportsDeviceBenchmark)

        val stub = runtime(EngineMode.Stub)
        stub.startEngineBootstrap()
        assertEquals(EngineMode.Stub, stub.engineIdentity().mode)
        assertFalse("스텁 폴백에 없는 능력을 열었다.", stub.engineClient.capabilities.supportsDeviceBenchmark)
    }
}
