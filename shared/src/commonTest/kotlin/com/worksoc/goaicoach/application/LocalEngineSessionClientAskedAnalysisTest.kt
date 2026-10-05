package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.LocalEngineSessionClient
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.enginecontract.HumanPolicy
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/**
 * 사용자가 **물어본** 분석은 주 모델이 답한다(백로그 #215 보강 ②).
 *
 * 급수 캐릭터와 두는 동안 엔진에는 사람 모델만 올라가 있다 — 그 급수처럼 두려고 올린 것이지 판을 읽으려고 올린 것이 아니다.
 * 형세 보기·추천 수는 그 자리에서 주 모델로 갈아 올려 답하고, 다음 AI 차례가 사람 모델을 다시 올린다.
 *
 * 엔진에 가는 명령의 **순서**를 본다. 갈아 올린 프로세스는 판이 비어 있어서, 판을 맞추기 전에 물으면 빈 판의 답이 나온다.
 */
class LocalEngineSessionClientAskedAnalysisTest {
    private val engine = ParkingCoreApi().apply {
        humanNetworkAvailable = true
        humanPolicies = mapOf("rank_15k" to OnlyC3, "rank_9d" to OnlyC3)
    }
    private val client = LocalEngineSessionClient(coreApi = engine, currentSessionGeneration = { 0L }, random = Random(7))

    /** 초보 캐릭터(백)가 한 수 둔다 — 그 뒤 엔진에는 사람 모델이 올라가 있고 판은 대국의 판이다. */
    private suspend fun kyuCharacterMoves(from: GameState = AfterBlackE5): GameState =
        client.runAutoAiTurn(from, PlayLevelSetting(level = 1), Profile, SearchTimeSettings(), EngineSearchMode.GtpStatefulFast, isolateSearchCache = false)
            .turnOutcome.gameState

    /** 대국 중의 형세 보기는 "판이 이미 이 국면"이라고 믿고 부른다(`syncFirst = false`) — 갈아 올렸으면 그 믿음을 거두고 판부터 맞춘다. */
    @Test
    fun aTappedScoreEstimateSwapsToTheMainNetworkAndResyncsTheBoard() = runBlocking {
        val game = kyuCharacterMoves()
        val before = engine.calls.size

        val estimate = client.estimateScoreForState(game, Profile, syncFirst = false)

        assertEquals(listOf("useNetwork Main", "newGame", "play Black E5", "play White C3", "estimate"), engine.calls.drop(before))
        assertEquals(EngineNetwork.Main, estimate.network)
    }

    /** 주 모델이 이미 올라가 있으면 예전 그대로다 — 갈아 올리지도, 판을 다시 맞추지도 않는다. */
    @Test
    fun aSecondTapOnTheMainNetworkNeitherSwapsNorResyncs() = runBlocking {
        val game = kyuCharacterMoves()
        client.estimateScoreForState(game, Profile, syncFirst = false)
        val before = engine.calls.size

        client.estimateScoreForState(game, Profile, syncFirst = false)

        assertEquals(listOf("estimate"), engine.calls.drop(before))
    }

    /** 추천 수도 주 모델이 낸다 — 갈아 올린 뒤에 판을 맞추고 탐색한다. */
    @Test
    fun aTopMoveAnalysisSwapsToTheMainNetworkBeforeItSyncs() = runBlocking {
        val game = kyuCharacterMoves()
        val before = engine.calls.size

        client.analyzePosition(game, SmallLimit)

        assertEquals(listOf("useNetwork Main", "newGame", "play Black E5", "play White C3", "analyze"), engine.calls.drop(before))
    }

    /** 물어본 뒤의 AI 차례는 사람 모델을 다시 올리고 판부터 맞춘다 — 급수 캐릭터는 계속 그 급수처럼 둔다. */
    @Test
    fun theNextKyuTurnBringsTheHumanNetworkBack() = runBlocking {
        val game = kyuCharacterMoves()
        client.estimateScoreForState(game, Profile, syncFirst = false)
        val afterUser = game.play(Move.Play(StoneColor.Black, G7))
        engine.humanPolicies = mapOf("rank_15k" to OnlyG3, "rank_9d" to OnlyG3)
        val before = engine.calls.size

        kyuCharacterMoves(from = afterUser)

        assertEquals(listOf("useNetwork Human", "configure", "newGame"), engine.calls.drop(before).take(3))
    }

    /**
     * 갈아 올리다 **끊긴** 형세 보기 — 옛 프로세스는 이미 내려갔고 다음 명령이 띄울 새 프로세스의 판은 비어 있다.
     * 다음 형세 보기는 갈아 올릴 일이 없어도(이미 주 모델) 판부터 맞춘다. 믿고 그냥 물으면 **빈 판의 형세**가 나온다.
     */
    @Test
    fun aSwapCancelledHalfwayLeavesTheBoardUnknown() = runBlocking {
        val game = kyuCharacterMoves()
        engine.parkAt("useNetwork Main")
        val interrupted = async { client.estimateScoreForState(game, Profile, syncFirst = false) }
        engine.awaitParked()
        interrupted.cancel()
        interrupted.join()
        val before = engine.calls.size

        client.estimateScoreForState(game, Profile, syncFirst = false)

        assertEquals(listOf("newGame", "play Black E5", "play White C3", "estimate"), engine.calls.drop(before))
    }

    /** 사람 모델이 없는 기기 — 형세 보기·추천 수가 엔진에 보내는 명령은 예전과 한 줄도 다르지 않다. */
    @Test
    fun withoutTheHumanModelAskedAnalysesAreExactlyAsBefore() = runBlocking {
        engine.humanNetworkAvailable = false
        val game = kyuCharacterMoves()
        val before = engine.calls.size

        client.estimateScoreForState(game, Profile, syncFirst = false)

        assertEquals(listOf("estimate"), engine.calls.drop(before))
        assertFalse(engine.calls.any { it.startsWith("useNetwork") })
    }

    private companion object {
        val Profile = EngineProfile()
        val SmallLimit = AnalysisLimit(visits = 16, timeMillis = 500L, candidateCount = 3)
        val E5 = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val C3 = BoardCoordinate.fromLabel("C3", BoardSize.Nine)
        val G3 = BoardCoordinate.fromLabel("G3", BoardSize.Nine)
        val G7 = BoardCoordinate.fromLabel("G7", BoardSize.Nine)
        val AfterBlackE5: GameState = GameState.empty().play(Move.Play(StoneColor.Black, E5))

        /** 둘 자리가 하나뿐인 정책 — 무엇이 뽑힐지 주사위와 무관하다. */
        val OnlyC3 = HumanPolicy("any", mapOf(C3 to 1.0), passProbability = 0.0)
        val OnlyG3 = HumanPolicy("any", mapOf(G3 to 1.0), passProbability = 0.0)
    }
}
