package com.worksoc.goaicoach.engine

import com.worksoc.goaicoach.engine.android.EngineCoreApiFactory
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.MoveResult
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 디버그 전용 멈춤 스위치(refactor backlog #74 실기 확인용, [DebugEngineStallInjector]). 한 번만 걸리고,
 * 걸리지 않으면 그대로 위임한다 — 스위치가 새면 디버그 빌드의 모든 분석이 멈춘다.
 */
class DebugEngineStallInjectorTest {
    private val armFile: File = File(Files.createTempDirectory("stall").toFile(), DebugEngineStallInjector.ArmFileName)
    private val engine = CountingEngine()
    private val injector = DebugEngineStallInjector(delegate = engine, armFile = armFile)
    private val limit = AnalysisLimit(visits = 16, timeMillis = 1_000L, candidateCount = 5)

    @Test
    fun parsesTheFourModesAndIgnoresAnythingElse() {
        assertEquals(DebugEngineStallInjector.Stall.Slow(1500L), DebugEngineStallInjector.parseStall("slow:1500"))
        assertEquals(DebugEngineStallInjector.Stall.Late(25_000L), DebugEngineStallInjector.parseStall("late:25000"))
        assertEquals(DebugEngineStallInjector.Stall.Wedge, DebugEngineStallInjector.parseStall("wedge"))
        assertEquals(DebugEngineStallInjector.Stall.Fail(2), DebugEngineStallInjector.parseStall("fail:2"))
        assertNull(DebugEngineStallInjector.parseStall("slow"))
        assertNull(DebugEngineStallInjector.parseStall("late:-1"))
        assertNull(DebugEngineStallInjector.parseStall("hang:10"))
        assertNull("0번 실패는 스위치가 아니다", DebugEngineStallInjector.parseStall("fail:0"))
        assertNull("상한을 넘기면 받지 않는다", DebugEngineStallInjector.parseStall("fail:11"))
    }

    /** `fail:N`(refactor backlog #109) — 분석 N번과 genMove N번이 진짜 실패를 던지고, 그 뒤로는 다시 위임한다. */
    @Test
    fun failMakesTheNextTurnsFailForRealAnalysisAndGenMoveAlikeThenDelegatesAgain() = runBlocking {
        armFile.writeText("fail:2")

        repeat(2) { turn ->
            val analysisFailure = runCatching { injector.analyze(limit) }.exceptionOrNull()
            val genMoveFailure = runCatching { injector.genMove(StoneColor.White) }.exceptionOrNull()
            assertTrue("차례 ${turn + 1}의 분석은 진짜 실패다(시간 초과 아님): $analysisFailure", analysisFailure is IllegalStateException)
            assertTrue("차례 ${turn + 1}의 genMove 폴백도 실패한다: $genMoveFailure", genMoveFailure is IllegalStateException)
        }
        assertFalse("파일은 첫 분석이 읽고 지운다", armFile.exists())
        assertEquals(0, engine.analyzeCalls)
        assertEquals(0, engine.genMoveCalls)

        injector.analyze(limit)
        injector.genMove(StoneColor.White)
        assertEquals("N번 뒤에는 그대로 위임한다", 1, engine.analyzeCalls)
        assertEquals(1, engine.genMoveCalls)
    }

    @Test
    fun withoutAnArmedFileItJustDelegates() = runBlocking {
        injector.analyze(limit)

        assertEquals(1, engine.analyzeCalls)
    }

    @Test
    fun lateThrowsARealTimeoutOnceAndThenDelegatesAgain() = runBlocking {
        armFile.writeText("late:0\n")

        val thrown = runCatching { injector.analyze(limit) }.exceptionOrNull()
        injector.analyze(limit)

        assertTrue("늦은 답은 진짜 TimeoutCancellationException이어야 5계층이 시간 초과로 본다: $thrown", thrown is TimeoutCancellationException)
        assertFalse("한 번만 건다 — 파일은 읽는 순간 지운다", armFile.exists())
        assertEquals("두 번째 분석은 그대로 위임한다", 1, engine.analyzeCalls)
    }

    @Test
    fun wedgeHoldsUntilForceResetThenFailsLikeAClosedPipe() = runBlocking {
        armFile.writeText("wedge")
        var thrown: Throwable? = null

        val job = launch { thrown = runCatching { injector.analyze(limit) }.exceptionOrNull() }
        repeat(5) { yield() }
        assertTrue("forceReset 전에는 풀리지 않는다", job.isActive)
        injector.forceReset()
        job.join()

        assertTrue("$thrown", thrown is IllegalStateException)
        assertEquals("forceReset은 진짜 엔진에도 간다", 1, engine.resets)
        assertEquals(0, engine.analyzeCalls)
    }

    @Test
    fun slowWaitsCancellablySoUndoCanStopIt() = runBlocking {
        armFile.writeText("slow:60000")

        val job = launch { injector.analyze(limit) }
        repeat(5) { yield() }
        job.cancel()
        withTimeout(5_000L) { job.join() }

        assertTrue(job.isCancelled)
        assertEquals(0, engine.analyzeCalls)
    }

    private class CountingEngine(
        private val base: EngineCoreApi = EngineCoreApiFactory.stub(),
    ) : EngineCoreApi by base {
        var analyzeCalls = 0
        var genMoveCalls = 0
        var resets = 0

        override suspend fun analyze(limit: AnalysisLimit): AnalysisResult {
            analyzeCalls += 1
            return AnalysisResult(status = EngineStatus.ready("counted"), candidates = emptyList(), summary = "counted")
        }

        override suspend fun genMove(player: StoneColor): MoveResult {
            genMoveCalls += 1
            return MoveResult(status = EngineStatus.ready("counted"), move = Move.Pass(player), summary = "counted")
        }

        override fun forceReset() {
            resets += 1
        }
    }
}
