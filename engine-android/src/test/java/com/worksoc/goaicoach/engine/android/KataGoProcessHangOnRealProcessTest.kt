package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [KataGoProcessLifecycleTest]의 두 핵심을 **진짜 OS 프로세스**로 한 번 더 잰다(refactor backlog #14) —
 * `LocalKataGoProcessRuntime`이 띄운 `sh`가 스스로 SIGSTOP한다([FakeKataGoExecutable]의 `stopSelfOn`).
 * #74 실기 재현법(`kill -STOP`)과 같은 상태다: 답하지 않고 stdin도 읽지 않는다.
 *
 * 메모리 안 가짜로 잰 것이 실제 `Process`·파이프에서도 그런지 — 시간 초과가 멈춘 프로세스를 실제로 내려
 * 막힌 읽기를 푸는지, `destroyForcibly()`가 멈춘 프로세스를 내리는지 — 를 여기서 본다.
 *
 * ⚠️ 이 JVM 테스트가 도는 macOS는 기기(Linux)와 다르다([FakeKataGoExecutable] KDoc의 실측): SIGTERM이 멈춘
 * 프로세스를 내리고, 스트림을 닫으면 막힌 읽기가 풀린다. 그래서 forceReset 쪽 테스트는 **고치기 전 코드에서도
 * 초록**이다(회귀 그물). 시간 초과 쪽은 고치기 전 코드가 프로세스를 아예 건드리지 않으므로 여기서도 빨갛다.
 */
class KataGoProcessHangOnRealProcessTest {
    private val fake = FakeKataGoExecutable.create(stopSelfOn = "kata-raw-nn")
    private val calls = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        fake.close() // 멈춘 채 남은 sh를 SIGKILL로 치운다 — 막힌 읽기도 그때 풀린다
        calls.cancel()
    }

    /** 멈춘 KataGo — 호출은 자기 마감에 시간 초과로 끝나고, 다음 호출은 새 프로세스에서 돈다. */
    @Test
    fun aTimeoutEndsARealProcessThatStoppedWithoutAnswering() {
        val adapter = KataGoProcessEngineAdapter(LocalKataGoProcessRuntime(fake.processConfig), deadlineMillis = { 1_000L })
        runBlocking { adapter.initialize(EngineProfile()) }

        val outcome = call { adapter.estimateScore(GtpPathLimit) }.outcomeWithin(5_000, "estimateScore on a stopped KataGo")

        assertTrue("expected a TimeoutCancellationException: $outcome", outcome.exceptionOrNull() is TimeoutCancellationException)
        runBlocking { adapter.configure(EngineProfile()) }
        assertEquals(listOf("gtp", "gtp"), fake.starts())
        assertEquals(InitializeCommands + "kata-raw-nn 0" + InitializeCommands, fake.gtpCommands())
        runBlocking { adapter.stop() }
    }

    /**
     * 「엔진 다시 시작하기」 — 멈춘(SIGSTOP) KataGo에서도 멈춘 호출이 곧바로 풀린다. 기기(Linux)에서는 SIGTERM이
     * 보류되므로 SIGKILL이어야 풀린다 — macOS에서는 오늘 코드(SIGTERM)도 풀린다(클래스 KDoc).
     */
    @Test
    fun forceResetEndsARealProcessThatStoppedWhileACallWaitsOnIt() {
        val adapter = KataGoProcessEngineAdapter(fake.processConfig)
        runBlocking { adapter.initialize(EngineProfile()) }

        val stuck = call { adapter.estimateScore(GtpPathLimit) }
        assertTrue(pollUntil(2_000) { "kata-raw-nn 0" in fake.gtpCommands() })
        Thread.sleep(100) // sh가 스스로 멈출 틈
        val resetMillis = measureMillis { adapter.forceReset() }
        val outcome = stuck.outcomeWithin(2_000, "the call stuck on the stopped KataGo")

        assertTrue("forceReset must not wait (${resetMillis}ms)", resetMillis < 500)
        val failure = outcome.exceptionOrNull()
        assertTrue("fails as the process ends: $failure", failure != null && failure !is CancellationException)
        runBlocking { adapter.configure(EngineProfile()) }
        assertEquals(listOf("gtp", "gtp"), fake.starts())
        runBlocking { adapter.stop() }
    }

    private fun <T> call(block: suspend () -> T): Deferred<T> = calls.async { block() }

    private fun <T> Deferred<T>.outcomeWithin(withinMillis: Long, what: String): Result<T> =
        runBlocking {
            withTimeoutOrNull(withinMillis) { join() }
                ?: fail("$what did not finish within ${withinMillis}ms")
            runCatching { await() }
        }

    private fun measureMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    }

    private companion object {
        val GtpPathLimit = AnalysisLimit(
            visits = 16,
            candidateCount = 5,
            includePolicy = false,
            refinePolicyMoves = 0,
            minVisitsPerCandidate = 0,
            minTimeMillis = null,
        )

        val InitializeCommands = listOf(
            "kata-set-param maxVisits 16",
            "kata-set-param maxTime 0.25",
        )
    }
}
