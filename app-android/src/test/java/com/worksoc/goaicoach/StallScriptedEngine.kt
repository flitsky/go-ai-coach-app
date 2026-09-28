package com.worksoc.goaicoach

import com.worksoc.goaicoach.engine.android.EngineCoreApiFactory
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.MoveResult
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * **일부러 멎을 수 있는 테스트용 엔진**(2계층 `EngineCoreApi`) — refactor backlog #74의 자체 검수(2026-09-28 사용자 결정).
 *
 * #74의 멎음은 실기에서 손으로 재현할 수 없을 만큼 드물다. 그래서 엔진 자리에 이 스텁을 끼우고 **다음 분석
 * 하나를 일부러 멎게** 한 뒤, 그 위의 진짜 계층들(3계층 `LocalEngineSessionClient` → 5계층 러너·컨트롤러 →
 * 앱 배선 `wireGoCoachControllers`)이 시간 초과를 알아채고 선택 팝업을 띄우고, 사용자가 고른 길로 **정상 분석이
 * 다시 답해** 복구되는지를 본다. 실기의 `DebugEngineStallInjector`(파일로 거는 디버그 스위치)와 같은 세 모양이다.
 *
 * ## 다음 `analyze()` 하나의 대본
 * - 대본이 비었으면 **정상 응답** — [freshCandidates]의 자리(판 가운데 줄)를 후보로 낸다.
 * - [hangNextAnalysis] — 테스트가 풀 때까지 멎는다. 푸는 법은 둘이다.
 *   - [Hang.passDeadline] — **어댑터의 마감이 지났다.** 진짜 `TimeoutCancellationException`을 던진다 —
 *     `KataGoGtpAnalysisClient`의 `withTimeout`이 끊는 것과 같은 예외다(생성자가 공개돼 있지 않아 진짜
 *     `withTimeout`으로 만든다).
 *   - [Hang.answerLate] — 늦게라도 답이 온다. 답은 [staleCandidates]의 자리(1선)다 — 이 자리의 돌이 판에 보이면
 *     버렸어야 할 늦은 답이 적용된 것이다.
 * - [wedgeNextAnalysis] — [forceReset](「엔진 다시 시작하기」)이 올 때까지 멎는다. 풀리면 파이프가 닫힌 것처럼
 *   `IllegalStateException`(기본) 또는 늦은 답([AfterReset.StaleAnswer])을 낸다.
 * - **진짜 실패**(refactor backlog #109) — [Hang.failForReal]로 멎은 분석을 풀거나, [failNextTurns]로 곧바로 실패시킨다.
 *   분석이 `IllegalStateException`을 던지고, **그 차례의 `genMove` 폴백도** 같은 예외를 던진다 — 둘 다 실패해야
 *   차례가 「AI turn failed」로 끝난다(분석만 실패하면 폴백이 둔다). 실패한 `genMove`는 `genMove` 뒤에
 *   `genMove:failed`로 남는다.
 *
 * ⚠️ **기본은 멎은 동안 취소에 반응하지 않는다**(`NonCancellable`) — 막힌 GTP 읽기는 인터럽트를 받지 않는다(설계 F4).
 * 취소된 차례는 그 읽기가 돌아올 때 끝난다. 그 한계 안에서도 위 계층이 늦은 답을 버리는지가 여기서 보는 것이다.
 * `hangNextAnalysis(cancellable = true)`는 취소에 곧바로 반응하는 전송(디버그 스위치의 `slow:N`, 원격 엔진)을 흉내 낸다.
 *
 * ⚠️ **명령마다 호출자가 살아 있는지 먼저 본다**(`ensureActive`). 진짜 어댑터는 명령마다 `Mutex`·`withTimeout`을
 * 지나므로 취소된 호출자는 다음 명령을 보내지 못한다 — 그 모양을 흉내 낸다. 명령 **직렬화**(한 번에 한 명령)는
 * 흉내 내지 않는다 — 이 스텁이 재는 것은 5계층의 분류·취소·복구이지 GTP 줄서기가 아니다.
 *
 * 받은 명령은 [calls]에 순서대로 남는다(엔진 스레드에서 쓰이므로 스레드 안전한 목록이다).
 */
internal class StallScriptedEngine(
    private val base: EngineCoreApi = EngineCoreApiFactory.stub(),
) : EngineCoreApi by base {
    val calls: MutableList<String> = CopyOnWriteArrayList()
    private val script = ConcurrentLinkedQueue<Hang>()
    private val wedgedUntilReset = CopyOnWriteArrayList<Hang>()

    /** 실패시킬 `genMove`의 남은 수 — 진짜 실패로 풀린 분석마다 하나씩 는다(그 차례의 폴백 몫). */
    private val failingGenMoves = AtomicInteger(0)

    init {
        // 앱에서는 기동(`startSession`)이 한다. 스텁은 초기화 전의 명령을 거부한다.
        runBlocking { base.initialize(EngineProfile()) }
    }

    fun count(call: String): Int = calls.count { it == call }

    /**
     * 다음 `analyze()` 하나가 테스트가 [Hang.passDeadline]/[Hang.answerLate]로 풀 때까지 멎는다.
     * @param cancellable `false`(기본)면 막힌 GTP 읽기처럼 취소를 무시한다. `true`면 호출자가 취소되는 순간 풀린다.
     */
    fun hangNextAnalysis(cancellable: Boolean = false): Hang = Hang(onReset = null, cancellable = cancellable).also(script::add)

    /** 다음 `analyze()` 하나가 [forceReset]까지 멎는다. */
    fun wedgeNextAnalysis(onReset: AfterReset = AfterReset.ClosedPipe): Hang = Hang(onReset = onReset, cancellable = false).also(script::add)

    /** 다음 [count]번의 AI 차례가 멎지 않고 곧바로 **진짜로 실패**한다 — 분석도, 그 차례의 `genMove` 폴백도(#109). */
    fun failNextTurns(count: Int) = repeat(count) { hangNextAnalysis().failForReal() }

    enum class AfterReset { ClosedPipe, StaleAnswer }

    internal enum class Release { DeadlinePassed, LateAnswer, ClosedPipe, RealFailure }

    inner class Hang internal constructor(
        internal val onReset: AfterReset?,
        internal val cancellable: Boolean,
    ) {
        private val entered = CountDownLatch(1)
        private val release = CompletableDeferred<Release>()

        internal fun enter() = entered.countDown()

        internal suspend fun awaitRelease(): Release = release.await()

        /** 엔진 스레드가 이 멎음에 **들어올 때까지** 기다린다(실제 시간 상한 — 오지 않으면 실패다). */
        fun awaitEntered(timeoutMillis: Long = 5_000L): Boolean = entered.await(timeoutMillis, TimeUnit.MILLISECONDS)

        val isEntered: Boolean get() = entered.count == 0L

        /** 어댑터의 마감이 지났다 — 멎은 분석이 진짜 `TimeoutCancellationException`으로 끝난다. */
        fun passDeadline() {
            check(onReset == null) { "forceReset까지 멎는 분석은 마감으로 풀리지 않는다" }
            release.complete(Release.DeadlinePassed)
        }

        /** 늦은 답이 온다 — [staleCandidates] 자리의 후보다. */
        fun answerLate() {
            check(onReset == null) { "forceReset까지 멎는 분석은 forceReset으로만 풀린다" }
            release.complete(Release.LateAnswer)
        }

        /**
         * 진짜 실패(시간 초과도 취소도 아니다 — 프로세스가 죽었다 등, refactor backlog #109). 분석이 `IllegalStateException`을
         * 던지고, 이어지는 `genMove` 폴백 하나도 실패한다 — 그래야 차례가 「AI turn failed」로 끝난다.
         */
        fun failForReal() {
            check(onReset == null) { "forceReset까지 멎는 분석은 forceReset으로만 풀린다" }
            failingGenMoves.incrementAndGet()
            release.complete(Release.RealFailure)
        }

        internal fun releaseByReset() {
            release.complete(if (onReset == AfterReset.StaleAnswer) Release.LateAnswer else Release.ClosedPipe)
        }
    }

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult {
        currentCoroutineContext().ensureActive()
        calls += "analyze"
        val hang = script.poll() ?: return answer(limit, freshCandidates)
        if (hang.onReset != null) wedgedUntilReset += hang
        hang.enter()
        val release = if (hang.cancellable) hang.awaitRelease() else withContext(NonCancellable) { hang.awaitRelease() }
        return when (release) {
            Release.DeadlinePassed -> {
                calls += "analyze:deadline"
                withTimeout(1L) { awaitCancellation() }
            }
            Release.LateAnswer -> {
                calls += "analyze:late-answer"
                answer(limit, staleCandidates)
            }
            Release.ClosedPipe -> {
                calls += "analyze:closed-pipe"
                error("KataGo process ended while waiting (pipe closed by forceReset)")
            }
            Release.RealFailure -> {
                calls += "analyze:failed"
                error("KataGo process died (scripted real failure)")
            }
        }
    }

    /** 「엔진 다시 시작하기」 — 막지 않고, forceReset까지 멎은 분석을 푼다. 다음 명령이 새 프로세스를 띄운다. */
    override fun forceReset() {
        calls += "forceReset"
        wedgedUntilReset.toList().forEach { hang ->
            wedgedUntilReset.remove(hang)
            hang.releaseByReset()
        }
        base.forceReset()
    }

    override suspend fun initialize(profile: EngineProfile): EngineStatus = command("initialize") { base.initialize(profile) }

    override suspend fun configure(profile: EngineProfile): EngineStatus = command("configure") { base.configure(profile) }

    override suspend fun newGame(boardSize: BoardSize, ruleset: Ruleset, handicapCount: Int, komi: Double): EngineStatus =
        command("newGame") { base.newGame(boardSize, ruleset, handicapCount, komi) }

    override suspend fun syncStaticPosition(state: GameState): EngineStatus =
        command("syncStaticPosition") { base.syncStaticPosition(state) }

    override suspend fun playMove(move: Move): EngineStatus = command("playMove") { base.playMove(move) }

    override suspend fun genMove(player: StoneColor): MoveResult = command("genMove") {
        if (failingGenMoves.getAndUpdate { remaining -> maxOf(remaining - 1, 0) } > 0) {
            calls += "genMove:failed"
            error("KataGo process died (scripted real failure, genmove fallback)")
        }
        base.genMove(player)
    }

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate = command("estimateScore") { base.estimateScore(limit) }

    private suspend fun <T> command(name: String, block: suspend () -> T): T {
        currentCoroutineContext().ensureActive()
        calls += name
        return block()
    }

    /**
     * 진짜 스텁의 분석 결과에서 후보 자리만 [points]로 바꿔 낸다 — 차례(색)·점수·방문 수는 스텁의 것을 쓴다.
     * 후보 수는 요청([AnalysisLimit.candidateCount])만큼이라 AI 수준의 고르기 범위가 비지 않는다.
     */
    private suspend fun answer(limit: AnalysisLimit, points: List<BoardCoordinate>): AnalysisResult {
        val result = base.analyze(limit)
        val player = result.candidates.firstOrNull()?.move?.player ?: return result
        val candidates = result.candidates.zip(points) { candidate: CandidateMove, point ->
            candidate.copy(move = Move.Play(player, point))
        }
        return result.copy(candidates = candidates)
    }

    companion object {
        /** 정상 응답의 후보 자리 — 9줄 판 가운데 줄(E열 위아래). */
        val freshCandidates: List<BoardCoordinate> = (0 until 9).map { column -> BoardCoordinate(row = 4, column = column) }

        /** 늦은(버렸어야 할) 답의 후보 자리 — 1선. 가운데 줄과 겹치지 않는다. */
        val staleCandidates: List<BoardCoordinate> = (0 until 9).map { column -> BoardCoordinate(row = 0, column = column) }
    }
}
