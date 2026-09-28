package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.application.engine.LocalEngineSessionClient
import com.worksoc.goaicoach.engine.android.FakeEngineProcessRuntime.Kind
import com.worksoc.goaicoach.engine.android.FakeEngineProcessRuntime.Reply
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.GameStateReplayer
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 오퍼레이션 단위 직렬화(refactor backlog #15)를 **진짜 어댑터** 위에서 — [LocalEngineSessionClient] +
 * [KataGoProcessEngineAdapter] + [FakeEngineProcessRuntime]의 메모리 안 프로세스(막힌 읽기는 인터럽트로 안 풀리고
 * 프로세스가 내려가야 풀린다, `KataGoProcessLifecycleTest`와 같은 가짜).
 *
 * `:shared`의 `LocalEngineSessionClientSerializationTest`가 명령 순서를 한 스레드에서 결정적으로 잰다면, 여기는
 * #14가 만든 것(왕복마다의 마감·SIGKILL·세대)과 오퍼레이션 락이 **실제 스레드에서** 어떻게 맞물리는지를 잰다 —
 * 함정 71(멈춘 오퍼레이션을 푸는 `forceReset`이 락을 기다리면 안 된다)과, 취소된 오퍼레이션이 답을 기다리는 동안
 * 락을 쥐고 있으면 안 된다는 것.
 */
class OperationSerializationOnProcessAdapterTest {
    private val runtime = FakeEngineProcessRuntime()

    /** 이 테스트에서 모든 명령이 기다릴 마감(ms) — 프로덕션 마감(30초·L+20초·120초)을 대신한다. */
    @Volatile private var deadline = 60_000L

    private val adapter = KataGoProcessEngineAdapter(runtime, deadlineMillis = { deadline })
    private val client = LocalEngineSessionClient(coreApi = adapter, currentSessionGeneration = { 0L })
    private val calls = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        runtime.releaseAll()
        calls.cancel()
    }

    /**
     * T4 — 오퍼레이션이 멈춘 KataGo 위에서 쥐고 있을 때 「엔진 다시 시작하기」: `forceReset`은 **곧바로** 돌아오고(락을
     * 기다리지 않는다), 멈춘 쪽은 실패하고, 뒤에 줄 선 재동기화는 **새 프로세스에서 처음부터** 판을 다시 둔다.
     * 예전: 줄 선 쪽이 이미 1세대 핸들을 잡고 그 왕복 락에 줄 서 있다가, 1세대가 내려가자 `boardsize`도 못 보내고
     * "retired before `boardsize`"로 실패했다.
     */
    @Test
    fun forceResetFreesAStuckOperationAtOnceAndTheQueuedOneReplaysOnTheNextProcess() {
        runtime.responder = { process, line -> if (process.ordinal == 1 && line.startsWith("kata-raw-nn")) Reply.Never else null }

        val stuck = call { client.syncAndEstimateGraphScore(TwoMoves, Profile) }
        assertTrue(runtime.awaitStarts(Kind.Gtp, 1))
        assertTrue(runtime.gtp(1).awaitReceived("kata-raw-nn"))
        val queued = call { client.syncAndEstimateGraphScore(BlackAtD4, Profile) }
        Thread.sleep(50) // 줄을 서게 둔다
        // 따로 돈 스레드에서 부른다 — forceReset이 락을 기다리는 회귀(함정 71)가 JVM을 매달지 않고 빨개진다.
        val resetMillis = millisOnASeparateThread(within = 2_000, what = "forceReset") { client.forceResetEngine() }

        assertTrue("forceReset must not wait for the stuck operation (${resetMillis}ms)", resetMillis < 100)
        val stuckOutcome = stuck.outcomeWithin(1_000, "the stuck operation")
        assertTrue("the stuck operation fails as its process ends: $stuckOutcome", stuckOutcome.exceptionOrNull() is IllegalStateException)
        queued.outcomeWithin(2_000, "the queued resync").getOrThrow()
        assertEquals(TwoMovesResync, runtime.gtp(1).received)
        assertEquals("the queued resync replays in full on generation 2, alone", BlackAtD4Resync, runtime.gtp(2).received)
    }

    /**
     * T4b — 한 프로세스의 stdin에 오퍼레이션 둘의 명령이 **묶음째로** 들어간다. 앞의 것의 수순 재생 한가운데(느린 `play`)에
     * 뒤의 것이 들어와도 섞이지 않는다. 예전: 왕복 락은 명령 하나만 묶어서, 뒤의 것의 `boardsize`가 앞의 것의
     * `kata-raw-nn` 앞에 끼었다 — 앞의 것은 남의 판을 추정했다.
     */
    @Test
    fun twoOperationsReachTheProcessAsContiguousCommandBlocks() {
        runtime.responder = { process, line -> if (process.ordinal == 1 && line == "play W C3") Reply.After(200, "=\n\n") else null }

        val first = call { client.syncAndEstimateGraphScore(TwoMoves, Profile) }
        assertTrue(runtime.awaitStarts(Kind.Gtp, 1))
        assertTrue(runtime.gtp(1).awaitReceived("play W C3"))
        val second = call { client.syncAndEstimateGraphScore(BlackAtD4, Profile) }

        first.outcomeWithin(2_000, "the first resync").getOrThrow()
        second.outcomeWithin(2_000, "the second resync").getOrThrow()
        assertEquals(TwoMovesResync + BlackAtD4Resync, runtime.gtp(1).received)
    }

    /**
     * T5(회귀 그물) — JSON 분석(추천 수·AI 차례의 분석이 쓰는 analysis 프로세스)이 답을 기다리는 중에 취소되면(무르기),
     * 그 답을 마저 받는 동안 **엔진 전체를 쥐고 있지 않는다**: 무르기 뒤의 재동기화는 GTP 프로세스에서 곧바로 끝난다.
     * 붙잡힌 답은 나중에 와도 다음 쿼리가 **제 답**을 읽는다(오늘도 초록 — 락을 그냥 두르면 재동기화가 탐색이
     * 끝날 때까지 기다려 빨개진다).
     */
    @Test
    fun aCancelledJsonAnalysisDoesNotHoldBackTheResyncThatFollowsTheUndo() {
        val release = CountDownLatch(1)
        val firstQuery = AtomicBoolean(true)
        runtime.responder = { process, line ->
            if (process.kind == Kind.Analysis && firstQuery.getAndSet(false)) Reply.WhenReleased(release, analysisReplyFor(line)) else null
        }

        val analysis = call { client.analyzePosition(TwoMoves, JsonLimit, EngineSearchMode.JsonPositionAnalysis) }
        assertTrue(runtime.awaitStarts(Kind.Analysis, 1))
        assertTrue(runtime.analysis(1).awaitReceived("{"))
        analysis.cancel()
        val resync = call { client.syncAndEstimateGraphScore(BlackAtD4, Profile) }

        resync.outcomeWithin(1_000, "the resync that follows the undo (the cancelled search's reply is still held)").getOrThrow()
        release.countDown()
        analysis.outcomeWithin(2_000, "the cancelled analysis")
        val next = runBlocking { client.analyzePosition(TwoMoves, JsonLimit, EngineSearchMode.JsonPositionAnalysis) }
        assertEquals("the next query reads its own reply", null, next.fallback)
        assertEquals("the analysis process survives a cancelled caller", emptyList<String>(), runtime.analysis(1).signals)
        assertEquals("GTP processes started", 1, runtime.processes(Kind.Gtp).size)
    }

    // ── 도우미 ─────────────────────────────────────────────────────────────────

    private fun <T> call(block: suspend () -> T): Deferred<T> = calls.async { block() }

    private fun <T> Deferred<T>.outcomeWithin(withinMillis: Long, what: String): Result<T> =
        runBlocking {
            withTimeoutOrNull(withinMillis) { join() }
                ?: fail("$what did not finish within ${withinMillis}ms")
            runCatching { await() }
        }

    /**
     * [block]을 따로 돈 스레드에서 돌려 걸린 시간을 잰다. [within] 안에 돌아오지 않으면 매달리지 않고 실패한다 —
     * 그 스레드는 데몬이라 `tearDown`의 `releaseAll`이 풀어 주지 못해도 JVM을 붙잡지 않는다.
     */
    private fun millisOnASeparateThread(within: Long, what: String, block: () -> Unit): Long {
        val elapsed = AtomicLong(-1)
        val failure = AtomicReference<Throwable?>(null)
        val thread = Thread({
            val start = System.nanoTime()
            runCatching(block).onFailure(failure::set)
            elapsed.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
        }, what).apply {
            isDaemon = true
            start()
        }
        thread.join(within)
        if (thread.isAlive) fail("$what did not return within ${within}ms — it is waiting for the stuck operation")
        failure.get()?.let { throw it }
        return elapsed.get()
    }

    private companion object {
        val Profile = EngineProfile()

        val JsonLimit = AnalysisLimit(
            visits = 16,
            candidateCount = 5,
            includePolicy = true,
            refinePolicyMoves = 0,
            minVisitsPerCandidate = 0,
            minTimeMillis = null,
        )

        val TwoMoves: GameState = GameStateReplayer.replay(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            moves = listOf(play(StoneColor.Black, "E5"), play(StoneColor.White, "C3")),
        )
        val BlackAtD4: GameState = GameStateReplayer.replay(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            moves = listOf(play(StoneColor.Black, "D4")),
        )

        val NewEvenGameCommands = listOf("boardsize 9", "komi 6.5", "kata-set-rules japanese", "clear_board")
        val TwoMovesResync = NewEvenGameCommands + listOf("play B E5", "play W C3", "kata-raw-nn 0")
        val BlackAtD4Resync = NewEvenGameCommands + listOf("play B D4", "kata-raw-nn 0")

        fun play(player: StoneColor, label: String): Move.Play =
            Move.Play(player, BoardCoordinate.fromLabel(label, BoardSize.Nine))

        /** analysis 프로세스의 최소 응답 — 가짜의 기본 답과 같은 모양이다(후보 없음). */
        fun analysisReplyFor(query: String): String =
            """{"id":"${JSONObject(query).getString("id")}","turnNumber":0,"moveInfos":[],"rootInfo":{"visits":1}}""" + "\n"
    }
}
