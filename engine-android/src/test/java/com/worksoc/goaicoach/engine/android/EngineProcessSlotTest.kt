package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.engine.android.FakeEngineProcessRuntime.Kind
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EngineProcessSlot]·[EngineProcessHandle] 단위 — 기동·폐기의 규칙만(refactor backlog #14).
 * 어댑터를 거친 경쟁·시간 초과는 [KataGoProcessLifecycleTest]가 잰다.
 */
class EngineProcessSlotTest {
    private val events = RecordingEvents()
    private val slot = EngineProcessSlot(EngineProcessKind.Gtp, events)
    private val started = CopyOnWriteArrayList<FakeEngineProcess>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        started.forEach { it.exit() }
        scope.cancel()
    }

    @Test
    fun aUsableHandleIsSharedAndGenerationsCountUpOnlyOnRestart() = runBlocking {
        val first = slot.acquire(::startFake)
        assertSame("a usable handle is reused", first, slot.acquire(::startFake))

        slot.retire(first, EngineProcessRetireReason.Timeout)
        val second = slot.acquire(::startFake)

        assertEquals(1L, first.generation)
        assertEquals(2L, second.generation)
        assertEquals(listOf("started gen=1", "retired gen=1 Timeout", "started gen=2"), events.lines)
    }

    /** ABA — 낡은 핸들의 폐기는 새 세대를 건드리지 않는다. */
    @Test
    fun retiringAStaleHandleLeavesTheNewerGenerationInPlace() = runBlocking {
        val first = slot.acquire(::startFake)
        slot.retireCurrent(EngineProcessRetireReason.ForceReset)
        val second = slot.acquire(::startFake)

        slot.retire(first, EngineProcessRetireReason.Timeout)

        assertSame(second, slot.currentOrNull())
        assertTrue(second.isUsable)
        assertEquals(emptyList<String>(), started[1].signals)
        assertEquals(EngineProcessRetireReason.ForceReset, first.retireReason)
        assertEquals(listOf("started gen=1", "retired gen=1 ForceReset", "started gen=2"), events.lines)
    }

    @Test
    fun retireIsIdempotentAndOnlyStopIsGraceful() = runBlocking {
        val stopped = slot.acquire(::startFake)
        slot.retire(stopped, EngineProcessRetireReason.Stop)
        slot.retire(stopped, EngineProcessRetireReason.Timeout)
        val timedOut = slot.acquire(::startFake)
        slot.retire(timedOut, EngineProcessRetireReason.Timeout)
        slot.retire(timedOut, EngineProcessRetireReason.Timeout)

        assertEquals("Stop → SIGTERM, once", listOf("TERM"), started[0].signals)
        assertEquals("Timeout → SIGKILL, once", listOf("KILL"), started[1].signals)
        assertEquals(EngineProcessRetireReason.Stop, stopped.retireReason)
        assertFalse(stopped.isUsable)
    }

    /** 폐기 없이 죽은 프로세스(크래시)는 다음 acquire가 `Died`로 치우고 새로 띄운다. */
    @Test
    fun aProcessThatDiedOnItsOwnIsReplacedAndRecordedAsDied() = runBlocking {
        val first = slot.acquire(::startFake)
        started[0].exit()

        val second = slot.acquire(::startFake)

        assertEquals(2L, second.generation)
        assertEquals(EngineProcessRetireReason.Died, first.retireReason)
        assertEquals(listOf("started gen=1", "retired gen=1 Died", "started gen=2"), events.lines)
    }

    @Test
    fun aFailedStartPublishesNothingAndDoesNotUseUpAGeneration() = runBlocking {
        val failure = runCatching { slot.acquire { throw IllegalArgumentException("KataGo model not found") } }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertNull(slot.currentOrNull())
        assertEquals(1L, slot.acquire(::startFake).generation)
    }

    /** forceReset(= retireCurrent)은 진행 중인 기동의 수명 락을 기다리지 않고, 막 뜨는 프로세스를 건드리지도 않는다. */
    @Test
    fun retireCurrentDoesNotWaitForAStartInProgressAndLeavesThatStartAlone() {
        val gate = CountDownLatch(1)
        val inStart = CountDownLatch(1)
        val starting = scope.async {
            slot.acquire {
                inStart.countDown()
                gate.await()
                startFake()
            }
        }
        assertTrue(inStart.await(2, TimeUnit.SECONDS))

        val begin = System.nanoTime()
        slot.retireCurrent(EngineProcessRetireReason.ForceReset)
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin)
        gate.countDown()
        val handle = runBlocking { starting.await() }

        assertTrue("retireCurrent must not wait (${elapsedMillis}ms)", elapsedMillis < 100)
        assertTrue(handle.isUsable)
        assertSame(handle, slot.currentOrNull())
    }

    /** 동시에 들어온 acquire 여럿 — 기동은 하나. */
    @Test
    fun concurrentAcquiresStartOnce() {
        val calls = (1..8).map { scope.async { slot.acquire { Thread.sleep(50); startFake() } } }
        val handles = runBlocking { calls.map { it.await() } }

        assertEquals(1, started.size)
        assertEquals(1, handles.toSet().size)
    }

    private fun startFake(): EngineProcessPipes =
        FakeEngineProcess(
            kind = Kind.Gtp,
            ordinal = started.size + 1,
            ignoresSigterm = false,
            ignoresSigkill = false,
            respond = { _, _ -> null },
        ).also { started += it }

    private class RecordingEvents : EngineProcessEvents {
        val lines = CopyOnWriteArrayList<String>()

        override fun started(handle: EngineProcessHandle) {
            lines += "started gen=${handle.generation}"
        }

        override fun retired(handle: EngineProcessHandle, reason: EngineProcessRetireReason) {
            lines += "retired gen=${handle.generation} $reason"
        }
    }
}
