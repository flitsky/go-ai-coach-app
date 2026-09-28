package com.worksoc.goaicoach.engine

import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.MoveResult
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * **디버그 빌드 전용** — 다음 `analyze()` 한 번을 일부러 멈추게 한다(refactor backlog #74 실기 확인용). `fail:N`은
 * 다음 N번의 AI 차례를 진짜로 실패시킨다(#109).
 *
 * 시간 초과는 손으로 낼 방법이 없다. 가장 짧은 제한이 1초인데 보이는 단계(16방문)의 탐색은 그 안에 끝나고,
 * 예산을 줄이면 마감(제한 + 20초)은 짧아지지만 답은 더 빨리 와서 여전히 시간 초과가 나지 않는다. 그래서 파일
 * 하나로 **한 번만** 거는 스위치를 둔다. UI·문구·셸 상태는 없다. 릴리스 빌드에는 감싸지 않는다(`EngineBootstrap`).
 *
 * ```
 * adb shell run-as com.zenit9hub.ai.baduk sh -c 'echo late:25000 > files/debug-engine-stall'
 * ```
 * 다음 `analyze()`가 파일을 읽고 **지운 뒤** 그 모드로 돈다(한 번만). 모드:
 * - `slow:N` — N밀리초 기다렸다가 진짜 결과를 낸다. 기다림은 **취소된다**(무르기 확인용).
 * - `late:N` — 취소를 무시하고 N밀리초 기다린 뒤 **시간 초과**를 던진다. 늦게라도 돌아오는 답(프로세스의
 *   `withTimeout`이 끊는 경우)을 흉내 낸다 — 실제 KataGo처럼 그동안은 취소에 반응하지 않는다.
 * - `wedge` — 「엔진 다시 시작하기」(forceReset)가 올 때까지 취소를 무시하고 붙잡은 뒤, 파이프가 닫힌 것처럼
 *   `IllegalStateException`을 던진다. 진짜로 멎은 엔진(설계 F4)을 흉내 낸다.
 * - `fail:N` — 다음 N번의 AI 차례가 **진짜로 실패**한다(refactor backlog #109 — 시간 초과도 취소도 아니다). 분석 N번과
 *   `genMove` N번이 곧바로 `IllegalStateException`을 던진다 — 분석만 실패하면 `genMove` 폴백이 두므로 둘 다다.
 *   파일은 첫 분석이 읽고 지우고, 남은 수는 메모리에 둔다(forceReset도 지우지 않는다).
 *
 * ⚠️ 한 번 건 스위치는 **그 다음 분석** 하나가 먹는다 — 추천 수·착수 평가의 분석도 분석이다. 확인하는 동안은
 * 둘을 끄고, 같은 국면이 분석 캐시에 있으면 엔진까지 가지 않으니 새 국면에서 건다.
 *
 * ⚠️ 멈춤은 3계층의 오퍼레이션 락을 **쥔 채**다(refactor backlog #15) — 진짜 멈춘 엔진과 같다. `wedge` 동안 형세·추천 수는
 * 기다리지 않고 "바쁘다"로 돌아오고, 재동기화·새 대국은 「엔진 다시 시작하기」까지 줄 선다(그 뒤 새 프로세스에서 돈다).
 * `late`의 기다림은 취소를 무시하므로 무르기를 해도 N밀리초 동안 락을 놓지 않는다 — 진짜 KataGo의 취소된 탐색은
 * 2계층 배수가 맡아 곧바로 놓는다(`KataGoProcessEngineAdapter.roundTrip`). 무르기 확인에는 `slow`를 쓴다.
 */
internal class DebugEngineStallInjector(
    private val delegate: EngineCoreApi,
    private val armFile: File,
) : EngineCoreApi by delegate {
    @Volatile
    private var wedgeRelease: CompletableDeferred<Unit>? = null
    private val failingAnalyses = AtomicInteger(0)
    private val failingGenMoves = AtomicInteger(0)

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult {
        when (val stall = takeArmedStall()) {
            null -> Unit
            is Stall.Fail -> {
                failingAnalyses.set(stall.turns)
                failingGenMoves.set(stall.turns)
            }
            is Stall.Slow -> delay(stall.millis)
            is Stall.Late -> {
                withContext(NonCancellable) { delay(stall.millis) }
                // 진짜 TimeoutCancellationException을 낸다(생성자는 공개돼 있지 않다).
                withTimeout(1L) { awaitCancellation() }
            }
            Stall.Wedge -> {
                val release = CompletableDeferred<Unit>().also { wedgeRelease = it }
                withContext(NonCancellable) { release.await() }
                error("KataGo process ended while waiting (debug wedge released by forceReset)")
            }
        }
        if (failingAnalyses.takeOne()) error("KataGo analysis failed (debug fail switch)")
        return delegate.analyze(limit)
    }

    override suspend fun genMove(player: StoneColor): MoveResult {
        if (failingGenMoves.takeOne()) error("KataGo genmove failed (debug fail switch)")
        return delegate.genMove(player)
    }

    private fun AtomicInteger.takeOne(): Boolean = getAndUpdate { remaining -> maxOf(remaining - 1, 0) } > 0

    override fun forceReset() {
        wedgeRelease?.complete(Unit)
        wedgeRelease = null
        delegate.forceReset()
    }

    private fun takeArmedStall(): Stall? {
        val text = runCatching { armFile.takeIf { it.isFile }?.readText() }.getOrNull() ?: return null
        runCatching { armFile.delete() }
        return parseStall(text.trim())
    }

    internal sealed class Stall {
        data class Slow(val millis: Long) : Stall()
        data class Late(val millis: Long) : Stall()
        data object Wedge : Stall()
        data class Fail(val turns: Int) : Stall()
    }

    internal companion object {
        const val ArmFileName = "debug-engine-stall"

        /** `fail:N`의 상한 — 잘못 적어 디버그 빌드의 분석이 끝없이 실패하지 않게. */
        private const val MaxFailTurns = 10L

        fun parseStall(text: String): Stall? {
            val mode = text.substringBefore(':').lowercase()
            val amount = text.substringAfter(':', missingDelimiterValue = "").toLongOrNull()
            return when {
                mode == "wedge" -> Stall.Wedge
                mode == "slow" && amount != null && amount >= 0L -> Stall.Slow(amount)
                mode == "late" && amount != null && amount >= 0L -> Stall.Late(amount)
                mode == "fail" && amount != null && amount in 1L..MaxFailTurns -> Stall.Fail(amount.toInt())
                else -> null
            }
        }
    }
}
