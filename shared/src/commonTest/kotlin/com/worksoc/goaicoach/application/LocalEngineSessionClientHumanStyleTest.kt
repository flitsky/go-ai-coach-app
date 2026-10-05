package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.LocalEngineSessionClient
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
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
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * 급수 캐릭터는 사람 모델로 둔다(백로그 #215) — 신경망은 한 번에 하나만 올리므로, AI 차례가 올릴 망을 고른다:
 * 급수 캐릭터(초보·하수·중수)의 차례는 사람 모델, 그 밖의 AI 차례는 주 모델.
 *
 * 엔진에 가는 명령의 **순서**를 본다. 갈아 올린 프로세스는 판이 비어 있어서, 판을 맞추기 전에 정책을 물으면 빈 판의 수를 둔다.
 */
class LocalEngineSessionClientHumanStyleTest {
    private val engine = ParkingCoreApi().apply {
        humanNetworkAvailable = true
        humanPolicies = mapOf("rank_15k" to OnlyE5, "rank_9k" to OnlyE5, "rank_3k" to OnlyE5, "rank_9d" to JudgePlaysOn)
    }
    private val client = LocalEngineSessionClient(coreApi = engine, currentSessionGeneration = { 0L }, random = Random(7))

    private suspend fun turn(level: Int, state: GameState = GameState.empty()) =
        client.runAutoAiTurn(state, PlayLevelSetting(level = level), Profile, SearchTimeSettings(), EngineSearchMode.GtpStatefulFast, isolateSearchCache = false)

    /** 사람 모델을 올리고 → 판을 맞추고 → 그 급수의 정책을 받아 → 둔다. 탐색(`analyze`)이 없다. */
    @Test
    fun aKyuCharacterPlaysFromTheHumanPolicyWithoutSearching() = runBlocking {
        val result = turn(level = 1)

        assertEquals(
            listOf("useNetwork Human", "configure", "newGame", "humanPolicy rank_15k", "play Black E5", "estimate"),
            engine.calls,
        )
        assertEquals(Move.Play(StoneColor.Black, E5), result.turnOutcome.gameState.moves.single())
        assertEquals(EngineNetwork.Human, result.scoreEstimate?.network, "the snapshot after the move is the human network's provisional value")
    }

    /** 캐릭터마다 프로필이 다르다 — 초보 15급, 하수 9급, 중수 3급(구간의 가운데). */
    @Test
    fun eachKyuCharacterAsksForItsOwnProfile() = runBlocking {
        turn(level = 1)
        turn(level = 2)
        turn(level = 3)

        assertEquals(listOf("humanPolicy rank_15k", "humanPolicy rank_9k", "humanPolicy rank_3k"), engine.calls.filter { it.startsWith("humanPolicy") })
        assertEquals(1, engine.calls.count { it.startsWith("useNetwork") }, "staying on the human network must not restart the engine every move")
    }

    /** 단 구간 캐릭터(고수·초고수)는 주 모델 탐색이다 — 사람 모델이 있어도 올리지 않는다. */
    @Test
    fun aDanCharacterNeverLoadsTheHumanNetwork() = runBlocking {
        turn(level = 5)

        assertFalse(engine.calls.any { it.startsWith("useNetwork") || it.startsWith("humanPolicy") })
        assertTrue(engine.calls.contains("analyze"))
    }

    /** 급수 캐릭터와 두다가 단 구간 캐릭터의 차례가 오면 주 모델로 갈아 올리고 **판부터** 맞춘다. */
    @Test
    fun aDanTurnAfterAKyuTurnSwapsBackToTheMainNetworkAndResyncs() = runBlocking {
        turn(level = 1)
        val before = engine.calls.size

        turn(level = 5)

        assertEquals(listOf("useNetwork Main", "configure", "newGame"), engine.calls.drop(before).take(3))
    }

    /** 사람 모델 파일이 없는 기기 — 지금 방식 그대로다(명령이 예전과 한 줄도 다르지 않다). */
    @Test
    fun withoutTheHumanModelAKyuCharacterPlaysExactlyAsBefore() = runBlocking {
        engine.humanNetworkAvailable = false

        turn(level = 1)

        assertEquals(listOf("configure", "newGame", "newGame", "analyze", "play Black E5", "estimate"), engine.calls)
    }

    /**
     * 통과는 그 급수가 아니라 **가장 센 프로필**이 정한다 — 약한 프로필은 너무 일찍 통과하려 든다.
     * 급수 프로필이 통과를 떠올렸을 때(1% 이상)만 9단 정책을 한 번 더 보고, 거기서 통과가 1위면 통과한다.
     */
    @Test
    fun passingIsDecidedByTheJudgeProfile() = runBlocking {
        engine.humanPolicies = mapOf("rank_15k" to WantsToPass, "rank_9d" to JudgePasses)

        val result = turn(level = 1)

        assertEquals(listOf("humanPolicy rank_15k", "humanPolicy rank_9d", "play Black pass"), engine.calls.filter { it.startsWith("humanPolicy") || it.startsWith("play") })
        assertEquals(Move.Pass(StoneColor.Black), result.turnOutcome.gameState.moves.single())
    }

    @Test
    fun aWeakProfileThatWantsToPassEarlyKeepsPlayingWhenTheJudgeDisagrees() = runBlocking {
        engine.humanPolicies = mapOf("rank_15k" to WantsToPass, "rank_9d" to JudgePlaysOn)

        val result = turn(level = 1)

        assertEquals(listOf("humanPolicy rank_15k", "humanPolicy rank_9d"), engine.calls.filter { it.startsWith("humanPolicy") })
        assertEquals(Move.Play(StoneColor.Black, E5), result.turnOutcome.gameState.moves.single())
    }

    /** 통과를 떠올리지도 않은 수에서는 9단 정책을 묻지 않는다 — 대국 대부분의 수가 평가 1회로 끝난다. */
    @Test
    fun theJudgeIsNotAskedWhenPassingIsNotOnTheTable() = runBlocking {
        turn(level = 1)

        assertEquals(listOf("humanPolicy rank_15k"), engine.calls.filter { it.startsWith("humanPolicy") })
    }

    /** 뽑힌 자리가 이 판에서 둘 수 없는 자리면 빼고 다시 뽑는다 — 돌이 있는 자리에 두려다 차례 전체가 실패하지 않는다. */
    @Test
    fun anUnplayablePointIsDroppedAndAnotherIsDrawn() = runBlocking {
        val occupied = GameState.empty().play(Move.Play(StoneColor.Black, E5))
        engine.humanPolicies = mapOf("rank_15k" to HumanPolicy("rank_15k", mapOf(E5 to 0.98, C3 to 0.02), passProbability = 0.0))

        val result = turn(level = 1, state = occupied)

        assertEquals(Move.Play(StoneColor.White, C3), result.turnOutcome.gameState.moves.last())
    }

    /**
     * 사람 모델을 못 쓴다(깨진 파일·죽은 프로세스) — **그 차례부터 지금 방식으로** 둔다. 주 모델로 갈아 올리고 판을 다시 맞춘 뒤 탐색한다.
     * 그리고 다시 시도하지 않는다: 수마다 다시 시도하면 수마다 프로세스를 두 번 갈아 올린다.
     */
    @Test
    fun aBrokenHumanModelFallsBackForThisTurnAndIsNotRetried() = runBlocking {
        engine.humanPolicyFailure = IllegalStateException("model file is corrupt")

        val first = turn(level = 1)
        val afterFirst = engine.calls.size
        turn(level = 1)

        assertEquals(
            listOf("useNetwork Human", "configure", "newGame", "humanPolicy rank_15k", "useNetwork Main", "configure", "newGame", "newGame", "analyze", "play Black E5", "estimate"),
            engine.calls.take(afterFirst),
        )
        assertEquals(EngineNetwork.Main, first.scoreEstimate?.network)
        assertFalse(engine.calls.drop(afterFirst).any { it.startsWith("useNetwork") || it.startsWith("humanPolicy") }, "the human network must not be retried every move")
    }

    private companion object {
        val Profile = EngineProfile()
        val E5 = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val C3 = BoardCoordinate.fromLabel("C3", BoardSize.Nine)

        /** 둘 자리가 하나뿐인 정책 — 무엇이 뽑힐지 주사위와 무관하다. */
        val OnlyE5 = HumanPolicy("any", mapOf(E5 to 1.0), passProbability = 0.0)
        val WantsToPass = HumanPolicy("rank_15k", mapOf(E5 to 0.7), passProbability = 0.3)
        val JudgePasses = HumanPolicy("rank_9d", mapOf(E5 to 0.2), passProbability = 0.8)
        val JudgePlaysOn = HumanPolicy("rank_9d", mapOf(E5 to 0.9), passProbability = 0.1)
    }
}
