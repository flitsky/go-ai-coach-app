package com.worksoc.goaicoach

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.testsupport.FakeEngineSessionClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T13(refactor backlog #15) — 무르기는 **떠나는 국면의** 엔진 작업(추천 수·형세·착수 동기화)을 취소한다.
 *
 * 그 결과는 어차피 버려진다(세대가 바뀌어 결과 가드가 버린다). 예전에는 취소하지 않아도 됐다 — 무르기 뒤 재동기화가
 * 그 작업과 **나란히** 돌았으니까(그래서 판이 섞였다). 이제 엔진 오퍼레이션은 한 번에 하나라, 취소하지 않으면
 * 재동기화(착수를 막는 blocking 작업)가 버려질 작업이 끝나기를 기다린다. 실제 배선([wireGoCoachControllers])으로 잰다.
 */
class UndoCancelsStaleEngineWorkWiringTest {
    @Test
    fun undoCancelsTheTopMovesAnalysisOfThePositionItLeaves() {
        val analysisStarted = CountDownLatch(1)
        val analysisCancelled = CountDownLatch(1)
        val context = FakeGoCoachAppWiringContext(
            inGameSession(playerSetup = HumanBlackAiWhite),
            engineClient = object : FakeEngineSessionClient() {
                override suspend fun analyzePosition(
                    state: GameState,
                    limit: AnalysisLimit,
                    searchMode: EngineSearchMode,
                ): AnalysisResult {
                    analysisStarted.countDown()
                    try {
                        awaitCancellation()
                    } finally {
                        analysisCancelled.countDown()
                    }
                }
            },
        )
        context.changeCore { it.copy(gameState = it.gameState.play(BlackAtThreeThree).play(WhiteAtFiveFive)) }
        context.changeSettings { it.copy(topMovesEnabled = true) }
        context.engineIsReady = true
        wireGoCoachControllers(context).topMovesController.requestAnalysis(context.gameState(), automatic = false)
        assertTrue("추천 수 분석이 걸려야 한다", context.dispatcher.runNext())
        assertTrue("추천 수 분석이 엔진 호출에 들어간다", analysisStarted.await(2, TimeUnit.SECONDS))

        wireGoCoachControllers(context).undoController.undoLastTurn()

        assertTrue("판이 무른 국면으로 돌아가야 한다", context.coreWrites.last().gameState.moves.isEmpty())
        assertTrue(
            "무르기가 떠나는 국면의 추천 수 분석을 취소하지 않았다 — 그 분석이 엔진을 쥔 동안 무르기 뒤 재동기화가 기다린다",
            analysisCancelled.await(2, TimeUnit.SECONDS),
        )
    }

    private companion object {
        val HumanBlackAiWhite = PlayerSetup()
        val BlackAtThreeThree = Move.Play(StoneColor.Black, BoardCoordinate(row = 2, column = 2))
        val WhiteAtFiveFive = Move.Play(StoneColor.White, BoardCoordinate(row = 4, column = 4))
    }
}
