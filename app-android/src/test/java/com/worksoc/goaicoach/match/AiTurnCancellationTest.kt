package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.MoveResult
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 착수 경로가 **취소·시간 초과·진짜 실패**를 가르는지(refactor backlog #74 — `#16`이 절반만 닫은 자리).
 *
 * `selectAiMoveFromAnalysis`의 `runCatching { … }.getOrNull()`은 셋을 모두 "후보 없음"으로 삼키고 곧바로
 * `genMove`를 불렀다. 그래서
 * - 사용자가 무르기·나가기로 취소해도 엔진이 한 번 더 착수를 만들었고,
 * - 시간 초과가 난 요청을 **같은 예산으로 또** 태웠다(GTP 경로에서는 프로세스가 막 내려간 뒤라, 판도 모르는
 *   새 프로세스가 빈 판의 수를 냈다 — 설계 F2).
 *
 * 남기는 것은 하나다: **진짜 실패**(분석 프로세스가 죽었다 등)는 여전히 `genMove`로 이어진다(이 폴백의 원래 목적).
 */
class AiTurnCancellationTest {
    /** T1 — 사용자가 취소한 차례는 `genMove`로 이어지지 않는다. */
    @Test
    fun userCancellationDuringAnalysisDoesNotFallBackToGenMove() = runBlocking {
        val analyzeEntered = CompletableDeferred<Unit>()
        val gateway = ScriptedAiMoveGateway(
            onAnalyze = {
                analyzeEntered.complete(Unit)
                awaitCancellation()
            },
        )
        var outcome: TurnOutcome? = null

        val job = launch { outcome = applyAiTurnFor(gateway) }
        analyzeEntered.await()
        job.cancel()
        job.join()

        assertTrue("취소한 차례는 취소로 끝나야 한다", job.isCancelled)
        assertFalse("취소를 삼키고 genMove를 부르면 엔진이 아무도 기다리지 않는 수를 한 번 더 만든다", gateway.genMoveCalled)
        assertNull("취소한 차례는 결과를 내지 않는다", outcome)
    }

    /**
     * T2 — 호출자는 살아 있는데 분석이 **시간 초과**(안쪽 `withTimeout`)로 끝나면 같은 예산을 또 쓰지 않고
     * 그대로 위로 알린다. 올라가는 것은 `CancellationException`이 **아니어야** 한다 — 그러면 launch가
     * 조용히 끝나 AI가 영영 멈춘다.
     */
    @Test
    fun searchTimeoutWhileCallerIsActiveSurfacesInsteadOfFallingBackToGenMove() = runBlocking {
        val gateway = ScriptedAiMoveGateway(
            onAnalyze = { withTimeout(1L) { awaitCancellation() } },
        )

        val thrown = runCatching { applyAiTurnFor(gateway) }.exceptionOrNull()

        assertNotNull("시간 초과는 genMove로 덮이지 않고 올라가야 한다", thrown)
        assertFalse(
            "시간 초과를 CancellationException으로 올리면 launch가 조용히 끝난다: $thrown",
            thrown is CancellationException,
        )
        assertTrue("원인에 시간 초과가 실려야 한다: ${thrown?.cause}", thrown?.cause is TimeoutCancellationException)
        assertFalse("같은 예산으로 genMove를 또 태우면 안 된다", gateway.genMoveCalled)
    }

    /**
     * T3 — 취소된 뒤 막혔던 읽기가 **일반 예외로** 풀리는 경우(`forceReset`으로 파이프가 닫히면
     * `IllegalStateException`이 난다). 그 예외는 결과가 아니다 — 취소로 다룬다.
     */
    @Test
    fun failureRaisedAfterCancellationIsTreatedAsCancellation() = runBlocking {
        val analyzeEntered = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        val gateway = ScriptedAiMoveGateway(
            onAnalyze = {
                withContext(NonCancellable) {
                    analyzeEntered.complete(Unit)
                    releaseRead.await()
                }
                throw IllegalStateException("KataGo process ended while waiting for: kata-search_analyze")
            },
        )
        var outcome: TurnOutcome? = null

        val job = launch { outcome = applyAiTurnFor(gateway) }
        analyzeEntered.await()
        job.cancel()
        releaseRead.complete(Unit)
        job.join()

        assertTrue(job.isCancelled)
        assertFalse("취소된 뒤의 실패로 genMove를 부르면 안 된다", gateway.genMoveCalled)
        assertNull(outcome)
    }

    /** 회귀 그물 — 호출자가 살아 있을 때의 **진짜 실패**는 지금처럼 `genMove`로 이어진다. */
    @Test
    fun realFailureWhileCallerIsActiveStillFallsBackToGenMove() = runBlocking {
        val gateway = ScriptedAiMoveGateway(
            onAnalyze = { throw IllegalStateException("analysis process died") },
        )

        val outcome = applyAiTurnFor(gateway)

        assertTrue("진짜 실패의 폴백은 남는다", gateway.genMoveCalled)
        assertTrue(outcome.gameState.moves.last() is Move.Pass)
    }

    private suspend fun applyAiTurnFor(gateway: AiMoveEngineGateway): TurnOutcome =
        applyAiTurn(
            engineAdapter = gateway,
            currentState = GameState.empty(),
            aiPlayer = StoneColor.Black,
            playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 3),
        )
}

private class ScriptedAiMoveGateway(
    private val onAnalyze: suspend (AnalysisLimit) -> AnalysisResult,
) : AiMoveEngineGateway {
    var genMoveCalled = false
        private set

    override suspend fun playMove(move: Move): EngineStatus = EngineStatus.ready("played")

    override suspend fun genMove(player: StoneColor): MoveResult {
        genMoveCalled = true
        return MoveResult(
            status = EngineStatus.ready("generated"),
            move = Move.Pass(player),
            summary = "fallback generated ${player.label} pass",
        )
    }

    override suspend fun clearSearchCache(): EngineStatus = EngineStatus.ready("cache cleared")

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult = onAnalyze(limit)
}
