package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.application.engine.syncToGameState
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
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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
 * KataGo 프로세스의 **수명** — 기동·폐기·시간 초과·강제 리셋이 서로 겹칠 때(refactor backlog #14).
 *
 * [FakeEngineProcessRuntime]의 메모리 안 프로세스로 돈다. 그 가짜의 막힌 읽기는 진짜 파이프처럼
 * 인터럽트로 풀리지 않고, 프로세스가 내려가야(EOF) 풀린다 — 그래서 "시간 초과가 막힌 읽기를 실제로
 * 끊는가"가 여기서 잴 수 있는 것이 된다(설계 F4: `withTimeout { runInterruptible { readLine() } }`는
 * 늦게라도 도착하는 답만 끊는다).
 *
 * 어댑터 호출은 [calls]에서 따로 돈다 — 고치기 전 코드에서 영영 안 끝나는 호출이 테스트 스레드를 붙잡지
 * 않고 [outcomeWithin]의 빨강으로 보이게 하려는 것이다. 정리는 가짜 프로세스를 전부 끝내 남은 읽기를 푼다.
 *
 * 세대 = 종류마다 뜬 순서(`runtime.gtp(1)`이 1세대). 신호 기록: `TERM` = `destroy()`, `KILL` = `destroyForcibly()`.
 */
class KataGoProcessLifecycleTest {
    private val runtime = FakeEngineProcessRuntime()

    /** 이 테스트에서 모든 명령이 기다릴 마감(ms) — 프로덕션 마감(30초·L+20초·120초)을 대신한다. */
    @Volatile private var deadline = 60_000L

    private val adapter = KataGoProcessEngineAdapter(runtime, deadlineMillis = { deadline })
    private val calls = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        runtime.releaseAll()
        calls.cancel()
    }

    // ── 기동: 동시에 들어와도 하나만 ──────────────────────────────────────────────

    /**
     * 아무것도 안 뜬 상태에서 첫 호출 여럿이 한꺼번에 들어온다(앱 시작 때 `DeferredEngineCoreApi`가 기다리던
     * 호출을 한 번에 깨우는 자리). 기동은 **하나**여야 한다 — 둘이면 모델을 쥔 KataGo 하나가 고아로 남는다.
     */
    @Test
    fun concurrentFirstCallsStartExactlyOneGtpProcess() {
        runtime.startGateMillis = 200

        val configures = (1..8).map { call { adapter.configure(EngineProfile()) } }
        configures.forEachIndexed { index, configure ->
            configure.outcomeWithin(5_000, "configure #${index + 1}").getOrThrow()
        }

        assertEquals("GTP processes started", 1, runtime.processes(Kind.Gtp).size)
        assertEquals("every configure lands on the one process", 16, runtime.gtp(1).received.size)
    }

    /** JSON analysis 프로세스도 같다 — 분석 둘이 겹쳐도 하나만 뜬다. */
    @Test
    fun concurrentJsonAnalysesStartExactlyOneAnalysisProcess() {
        runtime.startGateMillis = 200

        val analyses = (1..2).map { call { adapter.analyze(JsonPathLimit) } }
        analyses.forEach { analysis -> analysis.outcomeWithin(5_000, "analyze").getOrThrow() }

        assertEquals("analysis processes started", 1, runtime.processes(Kind.Analysis).size)
        assertEquals("GTP processes started", 1, runtime.processes(Kind.Gtp).size)
        assertEquals("both queries land on the one analysis process", 2, runtime.analysis(1).received.size)
    }

    // ── 시간 초과: 막힌 읽기를 실제로 끊는다 ───────────────────────────────────────

    /**
     * 진짜로 멈춘 KataGo(답을 영영 안 한다). 호출은 **자기 마감에** 시간 초과로 끝나야 하고, 그 프로세스는
     * SIGKILL로 내려가야 한다. 그다음 호출들은 새 프로세스에서 처음부터 맞춘다.
     */
    @Test
    fun aTrulyHungReadEndsAtItsDeadlineAndTheNextCallsRunOnAFreshProcess() {
        deadline = 200
        runtime.responder = { process, line -> if (process.ordinal == 1 && line.startsWith("kata-raw-nn")) Reply.Never else null }
        runBlocking { adapter.initialize(EngineProfile()) }

        val outcome = call { adapter.estimateScore(GtpPathLimit) }.outcomeWithin(2_000, "estimateScore on a hung KataGo")

        assertTimedOut(outcome)
        assertEquals("the hung process is killed", listOf("KILL"), runtime.gtp(1).signals)
        runBlocking {
            adapter.configure(EngineProfile())
            adapter.syncToGameState(EvenGameAfterTwoMoves)
            adapter.analyze(GtpPathLimit)
        }
        assertEquals("GTP processes started", 2, runtime.processes(Kind.Gtp).size)
        assertEquals(
            InitializeCommands + listOf(
                "boardsize 9",
                "komi 6.5",
                "kata-set-rules japanese",
                "clear_board",
                "play B E5",
                "play W C3",
                "kata-set-param maxVisits 16",
                "kata-set-param maxTime",
                "kata-search_analyze B",
            ) + InitializeCommands,
            runtime.gtp(2).received,
        )
    }

    /** JSON 쿼리가 멈추면 — 시간 초과로 끝나고(GTP로 폴백하지 않는다), analysis 프로세스만 내려간다. */
    @Test
    fun aHungJsonQueryEndsAtItsDeadlineWithoutFallingBackToGtpAndLeavesTheGtpProcessAlone() {
        deadline = 200
        runtime.responder = { process, _ -> if (process.kind == Kind.Analysis && process.ordinal == 1) Reply.Never else null }
        runBlocking { adapter.initialize(EngineProfile()) }

        val outcome = call { adapter.analyze(JsonPathLimit) }.outcomeWithin(2_000, "analyze on a hung analysis engine")

        assertTimedOut(outcome)
        assertEquals("the hung analysis process is killed", listOf("KILL"), runtime.analysis(1).signals)
        assertEquals("no GTP fallback after a timeout", InitializeCommands, runtime.gtp(1).received)
        assertEquals("the GTP process is untouched", emptyList<String>(), runtime.gtp(1).signals)
        val next = runBlocking { adapter.analyze(JsonPathLimit) }
        assertEquals("the next JSON analysis succeeds on a fresh process", null, next.fallback)
        assertEquals("analysis processes started", 2, runtime.processes(Kind.Analysis).size)
        assertEquals("GTP processes started", 1, runtime.processes(Kind.Gtp).size)
    }

    /**
     * 늦은 답(마감 뒤 도착)은 지금처럼 **시간 초과**다 — 값이 아니고, 다음 명령에게 넘어가지도 않는다.
     * (오늘도 초록 — 오늘은 답이 도착한 뒤에야 시간 초과가 나고, 고친 뒤에는 마감에 난다.)
     */
    @Test
    fun aLateReplyIsStillATimeoutAndIsNotHandedToTheNextCommand() {
        deadline = 100
        runtime.responder = { process, line ->
            when {
                process.ordinal == 1 && line == "genmove B" -> Reply.After(300, "= D4\n\n")
                line == "genmove W" -> Reply.Now("= E5\n\n")
                else -> null
            }
        }
        runBlocking {
            adapter.initialize(EngineProfile())
            adapter.newGame(BoardSize.Nine, Ruleset.Japanese, handicapCount = 0, komi = 6.5)
        }

        val outcome = call { adapter.genMove(StoneColor.Black) }.outcomeWithin(2_000, "genMove with a late reply")

        assertTimedOut(outcome)
        val next = runBlocking { adapter.genMove(StoneColor.White) }
        assertEquals("the next command reads its own reply", play(StoneColor.White, "E5"), next.move)
        assertEquals("GTP processes started", 2, runtime.processes(Kind.Gtp).size)
    }

    // ── 기동 예산: 새 프로세스의 첫 답은 모델 적재를 기다린다(refactor backlog #17) ─────────────

    /**
     * KataGo는 모델을 다 올린 뒤에야 stdin을 읽는다(에뮬레이터에서 8~33초). 그래서 새 프로세스의 **첫 답**은 명령
     * 마감에 모델 적재를 더한 만큼 늦다. 예전에는 그것이 첫 명령의 30초 마감 안에 들어 있어, 재시작 뒤의
     * `configure`가 적재가 끝나기도 전에 시간 초과로 SIGKILL되고 — 다음 재시작도 똑같이 — 되풀이될 수 있었다.
     *
     * 첫 답에는 명령 마감 위에 기동 예산이 따로 붙는다: 1세대도, 「엔진 다시 시작하기」 뒤의 2세대도 적재가 명령
     * 마감보다 길어도 첫 명령이 산다. 첫 답이 온 뒤에는 명령마다의 마감이 그대로다(늦은 답은 여전히 시간 초과·SIGKILL).
     */
    @Test
    fun aFreshProcessesFirstReplyWaitsForTheModelLoadButLaterRepliesKeepTheirOwnDeadline() {
        val adapter = startupBudgetAdapter()
        runtime.responder = { process, line ->
            when {
                process.received.size == 1 -> Reply.After(ModelLoadMillis, "=\n\n")
                line.startsWith("kata-raw-nn") -> Reply.After(ModelLoadMillis, "= whiteWin 0.5\n\n")
                else -> null
            }
        }

        call { adapter.initialize(EngineProfile()) }.outcomeWithin(3_000, "initialize on a process still loading its model").getOrThrow()
        adapter.forceReset()
        call { adapter.configure(EngineProfile()) }.outcomeWithin(3_000, "configure on the restarted process").getOrThrow()

        assertEquals("GTP processes started", 2, runtime.processes(Kind.Gtp).size)
        assertEquals("generation 2 is not killed while it loads its model", emptyList<String>(), runtime.gtp(2).signals)
        assertEquals(InitializeCommands, runtime.gtp(2).received)
        val late = call { adapter.estimateScore(GtpPathLimit) }.outcomeWithin(3_000, "a late reply on a warm process")
        assertTimedOut(late)
        assertEquals("a late reply on a warm process is still killed", listOf("KILL"), runtime.gtp(2).signals)
    }

    /** 기동 예산에도 끝이 있다 — 끝내 답하지 않는 새 프로세스는 그 예산에 시간 초과로 SIGKILL되고, 다음 호출은 새 세대로 간다. */
    @Test
    fun aFreshProcessThatNeverRepliesIsKilledAtTheEndOfItsStartupBudget() {
        val adapter = startupBudgetAdapter()
        runtime.responder = { process, _ -> if (process.ordinal == 1) Reply.Never else null }

        val outcome = call { adapter.configure(EngineProfile()) }.outcomeWithin(3_000, "configure on a process that never loads")

        assertTimedOut(outcome)
        assertEquals("the stuck fresh process is killed", listOf("KILL"), runtime.gtp(1).signals)
        runBlocking { adapter.configure(EngineProfile()) }
        assertEquals(InitializeCommands, runtime.gtp(2).received)
    }

    /** 명령 마감(200ms)이 모델 적재([ModelLoadMillis])보다 짧고, 기동 예산(1.2초)은 그보다 긴 어댑터. */
    private fun startupBudgetAdapter(): KataGoProcessEngineAdapter =
        KataGoProcessEngineAdapter(
            runtime,
            deadlineMillis = { budget -> if (budget == EngineStartupBudgetMillis) 1_200L else 200L },
        )

    // ── 호출자 취소(무르기·나가기): 프로세스는 살린다 ─────────────────────────────────

    /**
     * 호출자가 취소돼도(무르기 연타) 답은 끝까지 받아 스트림을 맞춘다 — 프로세스를 내리지 않는다.
     * 그래야 다음 명령이 **자기** 답을 읽는다. (오늘도 초록.)
     */
    @Test
    fun aCancelledCallerStillLetsTheReplyArriveSoTheNextCommandReadsItsOwnReply() {
        deadline = 1_000
        runtime.responder = { _, line ->
            when (line) {
                "genmove B" -> Reply.After(150, "= D4\n\n")
                "genmove W" -> Reply.Now("= E5\n\n")
                else -> null
            }
        }
        runBlocking {
            adapter.initialize(EngineProfile())
            adapter.newGame(BoardSize.Nine, Ruleset.Japanese, handicapCount = 0, komi = 6.5)
        }

        val cancelled = call { adapter.genMove(StoneColor.Black) }
        assertTrue(runtime.gtp(1).awaitReceived("genmove B"))
        Thread.sleep(50)
        cancelled.cancel()
        val outcome = cancelled.outcomeWithin(2_000, "the cancelled genMove")

        assertTrue("cancelled: $outcome", outcome.exceptionOrNull() is CancellationException)
        val next = runBlocking { adapter.genMove(StoneColor.White) }
        assertEquals("the next command reads its own reply", play(StoneColor.White, "E5"), next.move)
        assertEquals("a cancelled caller does not end the process", emptyList<String>(), runtime.gtp(1).signals)
        assertEquals("GTP processes started", 1, runtime.processes(Kind.Gtp).size)
    }

    /**
     * 취소된 호출자는 답을 **기다리지 않고 곧바로** 돌아간다 — 답을 마저 받아 스트림을 맞추는 일은 뒤에 남은 배수가
     * 한다(refactor backlog #15). 호출자는 오퍼레이션 락을 쥔 채이므로, 여기서 탐색이 끝나기를 기다리면 무르기 뒤의
     * 재동기화가 엔진 전체에서 그만큼 막힌다. 같은 프로세스의 다음 명령은 그 배수가 끝난 뒤에 나가 **제 답**을 읽는다.
     * 예전: 취소된 호출자가 `NonCancellable` 안에서 답이 올 때까지(마감까지) 붙잡혀 있었다.
     */
    @Test
    fun aCancelledCallerReturnsAtOnceWhileItsReplyIsDrainedAndTheNextCommandWaitsForThatDrain() {
        deadline = 5_000
        val release = CountDownLatch(1)
        runtime.responder = { _, line ->
            when (line) {
                "genmove B" -> Reply.WhenReleased(release, "= D4\n\n")
                "genmove W" -> Reply.Now("= E5\n\n")
                else -> null
            }
        }
        runBlocking {
            adapter.initialize(EngineProfile())
            adapter.newGame(BoardSize.Nine, Ruleset.Japanese, handicapCount = 0, komi = 6.5)
        }

        val cancelled = call { adapter.genMove(StoneColor.Black) }
        assertTrue(runtime.gtp(1).awaitReceived("genmove B"))
        cancelled.cancel()
        val outcome = cancelled.outcomeWithin(300, "the cancelled genMove (its reply is still held)")
        val next = call { adapter.genMove(StoneColor.White) }
        Thread.sleep(100)
        val nextWaitedForTheDrain = !next.isCompleted
        release.countDown()

        assertTrue("cancelled: $outcome", outcome.exceptionOrNull() is CancellationException)
        assertTrue("the next command must not go out before the cancelled reply is drained", nextWaitedForTheDrain)
        assertEquals("the next command reads its own reply", play(StoneColor.White, "E5"), next.outcomeWithin(2_000, "the next genMove").getOrThrow().move)
        assertEquals("a cancelled caller does not end the process", emptyList<String>(), runtime.gtp(1).signals)
        assertEquals("GTP processes started", 1, runtime.processes(Kind.Gtp).size)
    }

    /**
     * 취소된 호출자가 **진짜로 멈춘** 읽기 위에 있으면 — 호출자는 곧바로 취소로 돌아가고(refactor backlog #15), 뒤에
     * 남은 배수가 답을 기다리는 것도 마감까지다. 마감에 그 프로세스를 내린다(`#74`가 AI 차례를 실제로 취소하게 되면서
     * 생기는 자리 — 설계 R8). #15 전에는 호출자가 그 마감까지 붙잡혀 있다가 돌아왔다.
     */
    @Test
    fun aCancelledCallerOnAHungReadIsReleasedAtTheDeadlineByEndingThatProcess() {
        deadline = 300
        runtime.responder = { process, line -> if (process.ordinal == 1 && line == "genmove B") Reply.Never else null }
        runBlocking {
            adapter.initialize(EngineProfile())
            adapter.newGame(BoardSize.Nine, Ruleset.Japanese, handicapCount = 0, komi = 6.5)
        }

        val cancelled = call { adapter.genMove(StoneColor.Black) }
        assertTrue(runtime.gtp(1).awaitReceived("genmove B"))
        Thread.sleep(50)
        cancelled.cancel()
        val outcome = cancelled.outcomeWithin(150, "the cancelled genMove on a hung KataGo (well before its deadline)")

        val failure = outcome.exceptionOrNull()
        assertTrue("cancelled, not timed out: $failure", failure is CancellationException && failure !is TimeoutCancellationException)
        assertTrue("the hung process is killed at the deadline", pollUntil(2_000) { runtime.gtp(1).signals == listOf("KILL") })
        runBlocking { adapter.configure(EngineProfile()) }
        assertEquals("GTP processes started", 2, runtime.processes(Kind.Gtp).size)
    }

    // ── 세대: 낡은 호출은 새 프로세스를 건드리지 않는다 ─────────────────────────────

    /**
     * ABA — 1세대에서 멈춘 호출의 시간 초과가 **forceReset 뒤에 뜬 2세대**를 죽이면 안 된다.
     * 오늘은 시간 초과 재시작이 "지금 필드가 가리키는 것"을 내려서 2세대가 죽는다.
     */
    @Test
    fun aTimeoutThatFiresAfterAForceResetDoesNotEndTheProcessStartedAfterIt() {
        deadline = 100
        runtime.ignoreSigterm = true
        val release = CountDownLatch(1)
        runtime.responder = { process, line ->
            if (process.ordinal == 1 && line.startsWith("kata-raw-nn")) Reply.WhenReleased(release, "=\n\n") else null
        }
        runBlocking { adapter.configure(EngineProfile()) }

        val stuck = call { adapter.estimateScore(GtpPathLimit) }
        assertTrue(runtime.gtp(1).awaitReceived("kata-raw-nn"))
        adapter.forceReset()
        val next = call { adapter.configure(EngineProfile()) }
        assertTrue("generation 2 started", runtime.awaitStarts(Kind.Gtp, 2))
        Thread.sleep(150) // 1세대의 마감이 지났다
        release.countDown()
        stuck.outcomeWithin(2_000, "the call stuck on generation 1")

        next.outcomeWithin(2_000, "configure on generation 2").getOrThrow()
        assertEquals("generation 2 must survive generation 1's stale timeout", emptyList<String>(), runtime.gtp(2).signals)
        runBlocking { adapter.estimateScore(GtpPathLimit) }
        assertEquals("GTP processes started", 2, runtime.processes(Kind.Gtp).size)
        assertEquals("the next call runs on generation 2", "kata-raw-nn 0", runtime.gtp(2).received.last())
    }

    /**
     * 죽여도 읽기가 안 풀리는 경우(파이프를 쥔 채 남은 무언가). 시간 초과는 **그래도 자기 마감에** 돌아와야
     * 하고 — 막힌 스레드를 기다리지 않는다 — 그 뒤에 뜬 2세대는 멀쩡해야 한다.
     */
    @Test
    fun aTimedOutCallReturnsAtItsDeadlineEvenWhenEndingItsProcessDoesNotReleaseTheRead() {
        deadline = 300
        runtime.ignoreSigterm = true
        runtime.ignoreSigkill = true
        runtime.responder = { process, line -> if (process.ordinal == 1 && line.startsWith("kata-raw-nn")) Reply.Never else null }
        runBlocking { adapter.configure(EngineProfile()) }

        val stuck = call { adapter.estimateScore(GtpPathLimit) }
        assertTrue(runtime.gtp(1).awaitReceived("kata-raw-nn"))
        adapter.forceReset()
        val next = call { adapter.configure(EngineProfile()) }
        val outcome = stuck.outcomeWithin(2_000, "the call whose read is never released")

        assertTimedOut(outcome)
        next.outcomeWithin(2_000, "configure on generation 2").getOrThrow()
        assertEquals("generation 2 is untouched", emptyList<String>(), runtime.gtp(2).signals)
        runBlocking { adapter.estimateScore(GtpPathLimit) }
        assertEquals("the next call runs on generation 2", "kata-raw-nn 0", runtime.gtp(2).received.last())
    }

    /**
     * 멈춘 호출 뒤에 줄 선 호출은 1세대에 묶여 있다 — 1세대가 내려가면 **보내지 않고 곧바로** 실패해야 한다.
     * 새 프로세스로 옮겨 가 반쪽 설정을 보내면 안 된다.
     */
    @Test
    fun aCallQueuedBehindATimedOutCallFailsFastInsteadOfMovingToTheNextProcess() {
        deadline = 500
        runtime.responder = { process, line -> if (process.ordinal == 1 && line.startsWith("kata-raw-nn")) Reply.Never else null }
        runBlocking { adapter.configure(EngineProfile()) }

        val stuck = call { adapter.estimateScore(GtpPathLimit) }
        assertTrue(runtime.gtp(1).awaitReceived("kata-raw-nn"))
        val queued = call { adapter.newGame(BoardSize.Nine, Ruleset.Japanese, handicapCount = 0, komi = 6.5) }
        Thread.sleep(50) // 줄을 서게 둔다 — 1세대의 마감(500ms)보다 한참 앞이다

        assertTimedOut(stuck.outcomeWithin(2_000, "the stuck call"))
        val queuedOutcome = queued.outcomeWithin(2_000, "the queued newGame")
        assertTrue("fails fast on its retired process: $queuedOutcome", queuedOutcome.exceptionOrNull() is IllegalStateException)
        assertEquals("nothing more reached generation 1", InitializeCommands + "kata-raw-nn 0", runtime.gtp(1).received)
        runBlocking { adapter.newGame(BoardSize.Nine, Ruleset.Japanese, handicapCount = 0, komi = 6.5) }
        assertEquals(NewEvenGameCommands, runtime.gtp(2).received)
    }

    /**
     * 한 호출(`newGame` = 명령 넷)이 두 프로세스에 걸치면 안 된다. `boardsize`를 받은 직후 1세대가 리셋되고
     * 다른 호출이 2세대를 띄운다 — 오늘은 나머지 `komi`·룰·`clear_board`가 2세대로 가서, 2세대는 기본 판
     * 크기인 채로 그 설정을 받는다. 고친 뒤에는 `newGame`이 실패하고 2세대는 다른 호출의 명령만 받는다.
     */
    @Test
    fun aGameSetupThatLosesItsProcessDoesNotContinueOnAProcessAnotherCallStarted() {
        val other = AtomicReference<Deferred<EngineStatus>>()
        runtime.responder = { process, line ->
            if (process.ordinal == 1 && line.startsWith("boardsize")) {
                process.emit("=\n\n")
                adapter.forceReset()
                other.set(call { adapter.configure(EngineProfile()) })
                check(runtime.awaitStarts(Kind.Gtp, 2)) { "generation 2 never started" }
                Reply.Never // 답은 위에서 이미 썼다
            } else {
                null
            }
        }

        val outcome = call { adapter.newGame(BoardSize.Nine, Ruleset.Japanese, handicapCount = 0, komi = 6.5) }
            .outcomeWithin(2_000, "newGame")

        other.get().outcomeWithin(2_000, "configure on generation 2").getOrThrow()
        assertTrue("newGame must fail once its process is gone: $outcome", outcome.exceptionOrNull() is IllegalStateException)
        assertEquals(listOf("boardsize 9"), runtime.gtp(1).received)
        assertEquals("generation 2 received only the other call's commands", InitializeCommands, runtime.gtp(2).received)
    }

    // ── forceReset ─────────────────────────────────────────────────────────────

    /**
     * 「엔진 다시 시작하기」 — SIGTERM을 안 듣는 프로세스(멈춘 KataGo, SIGSTOP된 KataGo)에서도 멈춘 호출이
     * 곧바로 풀려야 한다. forceReset 자체는 메인 스레드에서 불리므로 기다리면 안 된다.
     */
    @Test
    fun forceResetReleasesACallStuckOnAProcessThatIgnoresSigterm() {
        runtime.ignoreSigterm = true
        runtime.responder = { process, line -> if (process.ordinal == 1 && line.startsWith("kata-raw-nn")) Reply.Never else null }
        runBlocking { adapter.configure(EngineProfile()) }

        val stuck = call { adapter.estimateScore(GtpPathLimit) }
        assertTrue(runtime.gtp(1).awaitReceived("kata-raw-nn"))
        val resetMillis = measureMillis { adapter.forceReset() }
        val outcome = stuck.outcomeWithin(1_000, "the call stuck on the reset process")

        assertTrue("forceReset must not wait (${resetMillis}ms)", resetMillis < 100)
        assertTrue("the stuck call fails as the process ends: $outcome", outcome.exceptionOrNull() is IllegalStateException)
        runBlocking { adapter.configure(EngineProfile()) }
        assertEquals("GTP processes started", 2, runtime.processes(Kind.Gtp).size)
        assertEquals(InitializeCommands, runtime.gtp(2).received)
    }

    /**
     * forceReset은 어떤 락도 잡지 않는다 — 멈춘 호출이 쥔 왕복 락도, 진행 중인 기동이 쥔 기동 락도.
     * (오늘도 초록 — 고친 뒤 기동 락이 생기면서 지켜야 할 것이 늘었다.)
     */
    @Test
    fun forceResetDoesNotWaitForAStuckCallOrForAStartInProgress() {
        runtime.responder = { process, line -> if (process.ordinal == 1 && line.startsWith("kata-raw-nn")) Reply.Never else null }
        runBlocking { adapter.configure(EngineProfile()) }
        val stuck = call { adapter.estimateScore(GtpPathLimit) }
        assertTrue(runtime.gtp(1).awaitReceived("kata-raw-nn"))
        runtime.startGateMillis = 1_000
        val starting = call { adapter.analyze(JsonPathLimit) }
        assertTrue("an analysis start is in progress", runtime.awaitStartInProgress(Kind.Analysis))

        val resetMillis = measureMillis { adapter.forceReset() }

        assertTrue("forceReset must not wait (${resetMillis}ms)", resetMillis < 100)
        stuck.outcomeWithin(2_000, "the stuck call")
        starting.outcomeWithin(3_000, "the analysis that was starting")
    }

    // ── 끝남: 프로세스가 죽거나 stop ───────────────────────────────────────────────

    /** 명령 도중 프로세스가 죽으면(크래시·저메모리 킬러) 그 호출은 실패하고 다음 호출은 새로 띄운다. (오늘도 초록.) */
    @Test
    fun aProcessThatDiesMidCommandFailsThatCallAndTheNextCallStartsAFreshOne() {
        runtime.responder = { process, line -> if (process.ordinal == 1 && line.startsWith("kata-raw-nn")) Reply.Crash else null }
        runBlocking { adapter.configure(EngineProfile()) }

        val outcome = call { adapter.estimateScore(GtpPathLimit) }.outcomeWithin(2_000, "estimateScore on a crashing KataGo")

        val failure = outcome.exceptionOrNull()
        assertTrue("fails as the process ends: $failure", failure is IllegalStateException && "process ended" in failure.message.orEmpty())
        runBlocking { adapter.configure(EngineProfile()) }
        assertEquals("GTP processes started", 2, runtime.processes(Kind.Gtp).size)
        assertEquals(InitializeCommands, runtime.gtp(2).received)
    }

    /** `stop()`은 `quit`을 보내고 SIGTERM으로 내린다 — 두 프로세스 다. 그 뒤 호출은 새로 띄운다. (오늘도 초록.) */
    @Test
    fun stopSendsQuitAndEndsBothProcessesWithSigterm() {
        runBlocking {
            adapter.configure(EngineProfile())
            adapter.analyze(JsonPathLimit)
            adapter.stop()
        }

        assertEquals(InitializeCommands + "quit", runtime.gtp(1).received)
        assertEquals(listOf("TERM"), runtime.gtp(1).signals)
        assertEquals(listOf("TERM"), runtime.analysis(1).signals)
        runBlocking { adapter.configure(EngineProfile()) }
        assertEquals("GTP processes started", 2, runtime.processes(Kind.Gtp).size)
    }

    /**
     * `stop()`이 `quit`을 기다리는 사이 1세대가 리셋되고 다른 호출이 2세대를 띄웠다. `stop()`은 **자기가 붙잡은**
     * 1세대만 내려야 한다 — 오늘은 끝에 "지금 필드"를 내려서 방금 뜬 2세대를 죽인다.
     */
    @Test
    fun stopDoesNotEndAProcessThatAnotherCallStartedWhileItWasQuitting() {
        val other = AtomicReference<Deferred<EngineStatus>>()
        runtime.responder = { process, line ->
            if (process.ordinal == 1 && line == "quit") {
                adapter.forceReset()
                other.set(call { adapter.configure(EngineProfile()) })
                check(runtime.awaitStarts(Kind.Gtp, 2)) { "generation 2 never started" }
                Reply.Never
            } else {
                null
            }
        }
        runBlocking { adapter.configure(EngineProfile()) }

        call { adapter.stop() }.outcomeWithin(2_000, "stop").getOrThrow()

        other.get().outcomeWithin(2_000, "configure on generation 2").getOrThrow()
        assertEquals("generation 2 is untouched by stop", emptyList<String>(), runtime.gtp(2).signals)
        assertEquals(InitializeCommands, runtime.gtp(2).received)
    }

    // ── 도우미 ─────────────────────────────────────────────────────────────────

    private fun <T> call(block: suspend () -> T): Deferred<T> = calls.async { block() }

    /**
     * [withinMillis] 안에 끝난 결과. 안 끝나면 실패한다 — 고치기 전 코드에서 막힌 읽기가 영영 안 풀리는 것이
     * 여기서 빨강으로 보인다(그 호출은 [calls]에 남고, 정리가 가짜 프로세스를 끝내 푼다).
     */
    private fun <T> Deferred<T>.outcomeWithin(withinMillis: Long, what: String): Result<T> =
        runBlocking {
            withTimeoutOrNull(withinMillis) { join() }
                ?: fail("$what did not finish within ${withinMillis}ms")
            runCatching { await() }
        }

    private fun assertTimedOut(outcome: Result<*>) {
        assertTrue("expected a TimeoutCancellationException: $outcome", outcome.exceptionOrNull() is TimeoutCancellationException)
    }

    private fun measureMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    }

    private companion object {
        /** 가짜 모델 적재 — 명령 마감(200ms)보다 길고 기동 예산(1.2초)보다 짧다. */
        const val ModelLoadMillis = 600L

        val JsonPathLimit = AnalysisLimit(
            visits = 16,
            candidateCount = 5,
            includePolicy = true,
            refinePolicyMoves = 0,
            minVisitsPerCandidate = 0,
            minTimeMillis = null,
        )

        val GtpPathLimit = JsonPathLimit.copy(includePolicy = false)

        /** `configure(EngineProfile())`가 보내는 명령. */
        val InitializeCommands = listOf(
            "kata-set-param maxVisits 16",
            "kata-set-param maxTime 0.25",
        )

        val NewEvenGameCommands = listOf("boardsize 9", "komi 6.5", "kata-set-rules japanese", "clear_board")

        val EvenGameAfterTwoMoves: GameState = GameStateReplayer.replay(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            moves = listOf(play(StoneColor.Black, "E5"), play(StoneColor.White, "C3")),
        )

        fun play(player: StoneColor, label: String): Move.Play =
            Move.Play(player, BoardCoordinate.fromLabel(label, BoardSize.Nine))
    }
}
