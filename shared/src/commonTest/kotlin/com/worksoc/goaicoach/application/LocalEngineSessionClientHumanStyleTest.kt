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
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.HumanPolicy
import com.worksoc.goaicoach.shared.enginecontract.OwnershipEstimate
import com.worksoc.goaicoach.shared.enginecontract.OwnershipPoint
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * 캐릭터는 사람 모델로 둔다(백로그 #215) — 신경망은 한 번에 하나만 올리므로, AI 차례가 올릴 망을 고른다:
 * 캐릭터 다섯(15급·9급·1급·3단·7단)의 차례는 사람 모델, 그 밖의 AI 차례(숨겨 둔 그룹)는 주 모델.
 *
 * 엔진에 가는 명령의 **순서**를 본다. 갈아 올린 프로세스는 판이 비어 있어서, 판을 맞추기 전에 정책을 물으면 빈 판의 수를 둔다.
 */
class LocalEngineSessionClientHumanStyleTest {
    private val engine = ParkingCoreApi().apply {
        humanNetworkAvailable = true
        humanPolicies = mapOf("rank_15k" to OnlyE5, "rank_9k" to OnlyE5, "rank_1k" to OnlyE5, "rank_3d" to OnlyE5, "rank_7d" to OnlyE5, "rank_9d" to JudgePlaysOn)
    }
    private val client = LocalEngineSessionClient(coreApi = engine, currentSessionGeneration = { 0L }, random = Random(7))

    private suspend fun turn(level: Int, state: GameState = GameState.empty()) =
        client.runAutoAiTurn(state, PlayLevelSetting(level = level), Profile, SearchTimeSettings(), EngineSearchMode.GtpStatefulFast, isolateSearchCache = false)

    /** 사람 모델로 두지 않는 AI의 차례 — 숨겨 둔 그룹(초급)은 캐릭터가 아니라 예전 방식(주 모델 탐색)으로 둔다. */
    private suspend fun mainNetworkTurn(state: GameState = GameState.empty()) =
        client.runAutoAiTurn(state, PlayLevelSetting(group = PlayLevelGroup.Beginner, level = 1), Profile, SearchTimeSettings(), EngineSearchMode.GtpStatefulFast, isolateSearchCache = false)

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

    /**
     * 캐릭터마다 프로필이 다르다 — 판다 15급 · 돌뫼 9급 · 반상 1급 · 사범 꼬북 3단 · 관장 천원 7단(사용자 2026-10-06).
     * 단 구간 둘도 탐색하지 않는다: 주 모델 탐색은 9단 프로필을 전승으로 이기는 세기라 「3단」·「7단」이 될 수 없다.
     */
    @Test
    fun eachCharacterAsksForItsOwnProfile() = runBlocking {
        (1..5).forEach { level -> turn(level = level) }

        assertEquals(
            listOf("humanPolicy rank_15k", "humanPolicy rank_9k", "humanPolicy rank_1k", "humanPolicy rank_3d", "humanPolicy rank_7d"),
            engine.calls.filter { it.startsWith("humanPolicy") },
        )
        assertEquals(1, engine.calls.count { it.startsWith("useNetwork") }, "staying on the human network must not restart the engine every move")
        assertFalse(engine.calls.contains("analyze"), "no character searches while the human network is available")
    }

    /** 캐릭터가 아닌 AI(숨겨 둔 그룹)는 주 모델 탐색이다 — 사람 모델이 있어도 올리지 않는다. */
    @Test
    fun anAiThatIsNotACharacterNeverLoadsTheHumanNetwork() = runBlocking {
        mainNetworkTurn()

        assertFalse(engine.calls.any { it.startsWith("useNetwork") || it.startsWith("humanPolicy") })
        assertTrue(engine.calls.contains("analyze"))
    }

    /** 캐릭터와 두다가 주 모델로 두는 AI의 차례가 오면 주 모델로 갈아 올리고 **판부터** 맞춘다. */
    @Test
    fun aMainNetworkTurnAfterACharacterTurnSwapsBackAndResyncs() = runBlocking {
        turn(level = 1)
        val before = engine.calls.size

        mainNetworkTurn()

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

    /**
     * **상대가 통과했으면 심판에게 묻는다**(사용자 2026-10-07) — 약한 프로필은 통과를 떠올리지 않아서, 사람이 「끝났다」고 통과해도
     * 묻지도 않고 계속 뒀다(폰: 사용자가 열다섯 번 통과하는 동안 AI가 매번 뒀다). 심판이 통과가 1위라고 하면 같이 통과해 판을 끝낸다.
     */
    @Test
    fun whenTheOpponentPassesTheJudgeIsAskedEvenIfTheWeakProfileNeverThinksOfPassing() = runBlocking {
        val userPassed = GameState.empty().play(Move.Play(StoneColor.Black, E5)).play(Move.Pass(StoneColor.White))
        engine.humanPolicies = mapOf("rank_15k" to OnlyC3, "rank_9d" to JudgePasses)

        val result = turn(level = 1, state = userPassed)

        assertEquals(listOf("humanPolicy rank_15k", "humanPolicy rank_9d", "play Black pass"), engine.calls.filter { it.startsWith("humanPolicy") || it.startsWith("play Black") }.takeLast(3))
        assertEquals(Move.Pass(StoneColor.Black), result.turnOutcome.gameState.moves.last())
    }

    /** 심판이 아직 둘 곳이 있다고 보면 그대로 둔다 — 상대의 이른 통과를 봐주지 않는다. */
    @Test
    fun anEarlyPassByTheOpponentIsNotAnswered() = runBlocking {
        val userPassed = GameState.empty().play(Move.Play(StoneColor.Black, E5)).play(Move.Pass(StoneColor.White))
        engine.humanPolicies = mapOf("rank_15k" to OnlyC3, "rank_9d" to JudgePlaysOn)

        val result = turn(level = 1, state = userPassed)

        assertEquals(Move.Play(StoneColor.Black, C3), result.turnOutcome.gameState.moves.last())
    }

    /**
     * **대국 중반에는 통과하지 않는다 — 가망이 없으면 기권을 제안한다**(사용자 2026-10-07). AI 집 0 · 상대 승률 99% 이상 · 30집 넘게
     * 뒤진 채 2수면 그 차례의 결과가 기권 제안을 싣고, AI는 계속 둔다(묻는 것은 세션이 한다 — 한 판에 한 번).
     */
    @Test
    fun inTheMiddleOfAHopelessGameTheCharacterOffersToResignAndKeepsPlaying() = runBlocking {
        engine.scriptedEstimate = HopelessForBlack
        engine.humanPolicies = mapOf("rank_15k" to OnlyE5, "rank_9d" to JudgePlaysOn)
        val first = turn(level = 1)
        engine.humanPolicies = mapOf("rank_15k" to OnlyC3, "rank_9d" to JudgePlaysOn)
        val second = turn(level = 1, state = first.turnOutcome.gameState.play(Move.Play(StoneColor.White, G7)))
        engine.humanPolicies = mapOf("rank_15k" to OnlyC7, "rank_9d" to JudgePlaysOn)

        val third = turn(level = 1, state = second.turnOutcome.gameState.play(Move.Play(StoneColor.White, G3)))

        assertFalse(first.offersResignation, "one hopeless turn is not enough")
        assertTrue(second.offersResignation)
        assertEquals(Move.Play(StoneColor.Black, C7), third.turnOutcome.gameState.moves.last(), "no passing in the middle of the game")
    }

    /**
     * **통과는 종국에서만** — 진 판이 2수 이어지고 판의 80%를 뒀으면 두지 않고 통과한다(9줄은 65수부터). 통과할 때는 정책을 묻지도 않는다.
     */
    @Test
    fun inTheEndgameALostGameIsPassedOut() = runBlocking {
        engine.scriptedEstimate = HopelessForBlack
        val sixtyFourMoves = GameState.empty().copy(moves = List(64) { index -> Move.Pass(if (index % 2 == 0) StoneColor.Black else StoneColor.White) })
        engine.humanPolicies = mapOf("rank_15k" to OnlyE5, "rank_9d" to JudgePlaysOn)
        val first = turn(level = 1, state = sixtyFourMoves)
        engine.humanPolicies = mapOf("rank_15k" to OnlyC3, "rank_9d" to JudgePlaysOn)
        val second = turn(level = 1, state = first.turnOutcome.gameState.play(Move.Play(StoneColor.White, G7)))
        val before = engine.calls.size

        val third = turn(level = 1, state = second.turnOutcome.gameState.play(Move.Play(StoneColor.White, G3)))

        assertEquals(Move.Pass(StoneColor.Black), third.turnOutcome.gameState.moves.last())
        assertFalse(engine.calls.drop(before).any { it.startsWith("humanPolicy") }, "a character that passes out does not ask where to play")
        assertFalse(second.offersResignation, "in the endgame the game is passed out, not resigned")
    }

    /** 한 번이라도 가망이 생기면 처음부터 다시 센다 — 가망 없음·회복·가망 없음은 2연속이 아니다. */
    @Test
    fun aRecoveryStartsTheHopelessCountOver() = runBlocking {
        engine.humanPolicies = mapOf("rank_15k" to OnlyE5, "rank_9d" to JudgePlaysOn)
        engine.scriptedEstimate = HopelessForBlack
        val afterFirst = turn(level = 1).turnOutcome.gameState.play(Move.Play(StoneColor.White, G7))
        engine.humanPolicies = mapOf("rank_15k" to OnlyC3, "rank_9d" to JudgePlaysOn)
        engine.scriptedEstimate = null
        val afterSecond = turn(level = 1, state = afterFirst).turnOutcome.gameState.play(Move.Play(StoneColor.White, G3))
        engine.humanPolicies = mapOf("rank_15k" to OnlyC7, "rank_9d" to JudgePlaysOn)
        engine.scriptedEstimate = HopelessForBlack

        val third = turn(level = 1, state = afterSecond)

        assertFalse(third.offersResignation)
    }

    /**
     * 센 것은 **그 판이 이어질 때만** 유효하다 — 새 대국·무르기·이어하기처럼 수순이 이어지지 않으면 처음부터 센다.
     * 앞 판에서 가망 없었다고 새 판의 첫 수에 기권을 제안하면 안 된다.
     */
    @Test
    fun theHopelessCountDoesNotCarryOverToAnotherGame() = runBlocking {
        engine.scriptedEstimate = HopelessForBlack
        engine.humanPolicies = mapOf("rank_15k" to OnlyE5, "rank_9d" to JudgePlaysOn)
        val afterFirst = turn(level = 1).turnOutcome.gameState.play(Move.Play(StoneColor.White, G7))
        engine.humanPolicies = mapOf("rank_15k" to OnlyC3, "rank_9d" to JudgePlaysOn)
        assertTrue(turn(level = 1, state = afterFirst).offersResignation)
        engine.humanPolicies = mapOf("rank_15k" to OnlyE5, "rank_9d" to JudgePlaysOn)

        val newGame = turn(level = 1)

        assertEquals(Move.Play(StoneColor.Black, E5), newGame.turnOutcome.gameState.moves.single())
        assertFalse(newGame.offersResignation)
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

    /**
     * **계가는 주 모델이 한다**(보강 ①). 사람의 통과로 끝난 판 — 사람 모델이 올라간 채로 오므로, 판을 맞추기 **전에** 주 모델을 올린다.
     * 순서가 뒤집히면 맞춘 판이 옛 프로세스와 함께 사라지고, 새 프로세스는 빈 판을 계가한다.
     */
    @Test
    fun aGameEndedByTheUsersPassIsScoredOnTheMainNetwork() = runBlocking {
        turn(level = 1)
        val before = engine.calls.size

        client.syncAfterHumanMove(EndedByTwoPasses, Profile, EndedByTwoPasses.moves.last(), previousReviewCandidates = emptyList())

        val calls = engine.calls.drop(before)
        assertEquals(listOf("useNetwork Main", "newGame", "play Black E5", "play White pass", "play Black pass"), calls.take(5))
        assertTrue(calls.indexOf("deadStones") > calls.indexOf("play Black pass"), "the dead stones must be judged after the board is synced: $calls")
    }

    /** 판이 끝나지 않는 사람 착수는 갈아 올리지 않는다 — 수마다 엔진을 다시 띄우면 급수 대국이 느려진다. */
    @Test
    fun anOrdinaryUserMoveStaysOnTheHumanNetwork() = runBlocking {
        val afterAi = turn(level = 1).turnOutcome.gameState
        val afterUser = afterAi.play(Move.Play(StoneColor.White, C3))
        val before = engine.calls.size

        val result = client.syncAfterHumanMove(afterUser, Profile, afterUser.moves.last(), previousReviewCandidates = emptyList())

        assertFalse(engine.calls.drop(before).any { it.startsWith("useNetwork") })
        assertEquals(EngineNetwork.Human, result.estimate?.network)
    }

    /**
     * AI의 통과로 끝난 판의 계가는 "판이 이미 이 국면"이라고 믿고 계가만 한다 — 주 모델로 갈아 올렸으면 그 믿음이 깨지므로 판부터 맞춘다.
     */
    @Test
    fun aGameEndedByTheAisPassIsResyncedOnTheMainNetworkBeforeScoring() = runBlocking {
        turn(level = 1)
        val before = engine.calls.size

        client.resolveEndgameForState(EndedByTwoPasses, Profile, prePassCandidates = emptyList())

        val calls = engine.calls.drop(before)
        assertEquals(listOf("useNetwork Main", "newGame", "play Black E5", "play White pass", "play Black pass"), calls.take(5))
        assertTrue(calls.contains("deadStones"))
    }

    /** 주 모델로 두던 판의 계가는 예전 그대로다 — 갈아 올리지도, 판을 다시 맞추지도 않는다. */
    @Test
    fun scoringAGameAlreadyOnTheMainNetworkDoesNotResync() = runBlocking {
        mainNetworkTurn()
        val before = engine.calls.size

        client.resolveEndgameForState(EndedByTwoPasses, Profile, prePassCandidates = emptyList())

        val calls = engine.calls.drop(before)
        assertFalse(calls.any { it.startsWith("useNetwork") || it == "newGame" }, "scoring must not replay the game when nothing was swapped: $calls")
    }

    /**
     * **사람 모델을 못 쓰는 엔진**에서 초고수는 예전처럼 탐색하고, **둘 때만** 32방문으로 엔진을 건다. 차례가 끝난 뒤 세션에 돌려주는
     * 프로필은 16방문 그대로다 — 그것이 공용 프로필이 되어 추천 수·형세가 쓰므로, 상대의 방문 수가 새면 초고수와 둘 때만 내 추천 수가
     * 두 배로 느려진다.
     */
    @Test
    fun withoutTheHumanModelTheTopTierSearchesWithItsOwnVisitsButHandsBackTheSharedProfile() = runBlocking {
        engine.humanNetworkAvailable = false

        val result = turn(level = 5)

        assertEquals(listOf(32), engine.configuredVisits)
        assertEquals(16, result.profile.analysisLimit.visits)
    }

    @Test
    fun withoutTheHumanModelTheOtherDanTierKeepsSearchingWithSixteenVisits() = runBlocking {
        engine.humanNetworkAvailable = false

        turn(level = 4)

        assertEquals(listOf(16), engine.configuredVisits)
    }

    private companion object {
        val EndedByTwoPasses: GameState = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Pass(StoneColor.White))
            .play(Move.Pass(StoneColor.Black))
        val Profile = EngineProfile()
        val E5 = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val C3 = BoardCoordinate.fromLabel("C3", BoardSize.Nine)

        val C7 = BoardCoordinate.fromLabel("C7", BoardSize.Nine)
        val G3 = BoardCoordinate.fromLabel("G3", BoardSize.Nine)
        val G7 = BoardCoordinate.fromLabel("G7", BoardSize.Nine)

        /** 둘 자리가 하나뿐인 정책 — 무엇이 뽑힐지 주사위와 무관하다. */
        val OnlyC3 = HumanPolicy("any", mapOf(C3 to 1.0), passProbability = 0.0)
        val OnlyC7 = HumanPolicy("any", mapOf(C7 to 1.0), passProbability = 0.0)

        /** 흑에게 가망 없는 형세 — 백 승률 99.5%, 백이 69집 앞서고, 판 전체가 백 쪽으로 기울어 흑의 집이 없다. */
        val HopelessForBlack = ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteWinRate = 0.995,
            whiteScoreLead = 69.0,
            ownership = OwnershipEstimate(
                blackLikelyPoints = 0,
                whiteLikelyPoints = 81,
                neutralOrUnclearPoints = 0,
                threshold = 0.6,
                points = (0 until 9).flatMap { row -> (0 until 9).map { column -> OwnershipPoint(BoardCoordinate(row, column), 0.9) } },
            ),
            summary = "estimated",
        )

        /** 둘 자리가 하나뿐인 정책 — 무엇이 뽑힐지 주사위와 무관하다. */
        val OnlyE5 = HumanPolicy("any", mapOf(E5 to 1.0), passProbability = 0.0)
        val WantsToPass = HumanPolicy("rank_15k", mapOf(E5 to 0.7), passProbability = 0.3)
        val JudgePasses = HumanPolicy("rank_9d", mapOf(E5 to 0.2), passProbability = 0.8)
        val JudgePlaysOn = HumanPolicy("rank_9d", mapOf(E5 to 0.9), passProbability = 0.1)
    }
}
