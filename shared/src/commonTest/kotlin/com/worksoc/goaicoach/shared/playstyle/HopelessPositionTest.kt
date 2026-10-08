package com.worksoc.goaicoach.shared.playstyle

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.OwnershipEstimate
import com.worksoc.goaicoach.shared.enginecontract.OwnershipPoint
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 진 판을 AI가 어떻게 끝내는가(백로그 #213, 사용자 2026-10-07) — **통과는 종국에서만, 중반에는 기권 제안.**
 *
 * 숫자는 사용자가 정했거나 골랐다: 승률 99% 이상 · AI 집 0 · 30집 · 2수 · 판의 80% · 10수. 여기서 그 숫자와 갈림을 고정한다.
 */
class HopelessPositionTest {
    private val board = GameState.empty(boardSize = BoardSize.Nine)
        .play(Move.Play(StoneColor.Black, At(4, 4)))
        .play(Move.Play(StoneColor.White, At(0, 0)))

    /** 판 전체가 [value]만큼 기운 영역(백 쪽이 +). [exceptions]에 적힌 자리만 다른 값이다. */
    private fun estimate(whiteWinRate: Double?, whiteScoreLead: Double?, value: Double = -0.9, exceptions: Map<BoardCoordinate, Double> = emptyMap()) =
        ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteWinRate = whiteWinRate,
            whiteScoreLead = whiteScoreLead,
            ownership = OwnershipEstimate(
                blackLikelyPoints = 0,
                whiteLikelyPoints = 0,
                neutralOrUnclearPoints = 0,
                threshold = 0.6,
                points = (0 until 9).flatMap { row -> (0 until 9).map { column -> At(row, column) } }
                    .map { coordinate -> OwnershipPoint(coordinate, exceptions[coordinate] ?: value) },
            ),
            summary = "estimated",
        )

    /** 수순이 [moves]개인 9줄 판 — 판의 진행(종국인가)만 보는 테스트가 쓴다. */
    private fun boardAfter(moves: Int): GameState =
        GameState.empty(boardSize = BoardSize.Nine).copy(moves = List(moves) { index -> Move.Pass(if (index % 2 == 0) StoneColor.Black else StoneColor.White) })

    /** 백(AI)의 집이 하나도 없고, 흑 승률 99.5%, 69집 차 — 사용자의 폰 대국 28수째와 같은 모양이다. */
    @Test
    fun noTerritoryAHugeDeficitAndNoChanceIsBarren() {
        val hopeless = estimate(whiteWinRate = 0.005, whiteScoreLead = -69.0)

        assertTrue(HopelessPosition.isLost(StoneColor.White, hopeless))
        assertTrue(HopelessPosition.isFarBehind(StoneColor.White, hopeless))
        assertTrue(HopelessPosition.isBarren(StoneColor.White, hopeless, board))
    }

    /** 같은 형세를 **이기는 쪽**에서 보면 아무것도 아니다 — 흑 좌석의 AI에게도 부호가 맞아야 한다. */
    @Test
    fun theSameBoardIsNothingOfTheSortForTheWinner() {
        val whiteIsCrushed = estimate(whiteWinRate = 0.005, whiteScoreLead = -69.0)
        assertFalse(HopelessPosition.isLost(StoneColor.Black, whiteIsCrushed))
        assertFalse(HopelessPosition.isBarren(StoneColor.Black, whiteIsCrushed, board))

        assertTrue(HopelessPosition.isBarren(StoneColor.Black, estimate(whiteWinRate = 0.995, whiteScoreLead = 69.0, value = 0.9), board))
    }

    /** 조건은 겹쳐 쌓인다 — 진 판(승률) ⊃ 크게 뒤짐(30집) ⊃ 집까지 없음. 하나라도 빠지면 그 위는 아니다. */
    @Test
    fun theConditionsStackAndEachOneIsRequired() {
        assertFalse(HopelessPosition.isLost(StoneColor.White, estimate(whiteWinRate = 0.02, whiteScoreLead = -69.0)), "a 2% chance is still a chance")

        val lostButClose = estimate(whiteWinRate = 0.005, whiteScoreLead = -29.0)
        assertTrue(HopelessPosition.isLost(StoneColor.White, lostButClose))
        assertFalse(HopelessPosition.isFarBehind(StoneColor.White, lostButClose), "29 points is not a huge deficit")

        val oneOwnPoint = estimate(whiteWinRate = 0.005, whiteScoreLead = -69.0, exceptions = mapOf(At(8, 8) to 0.7))
        assertTrue(HopelessPosition.isFarBehind(StoneColor.White, oneOwnPoint))
        assertFalse(HopelessPosition.isBarren(StoneColor.White, oneOwnPoint, board), "one point of own territory means there is something to play for")
    }

    /**
     * 초반에는 아무도 집이 없다 — 「집 0」이 저절로 참이라, 점수차가 크지 않으면 걸리지 않아야 한다
     * (E10에서 잘못 걸린 두 판이 모두 초반, 16집 차였다).
     */
    @Test
    fun anEarlyGameWithNobodyOwningAnythingIsNotBarren() {
        val nobodyOwnsAnything = estimate(whiteWinRate = 0.005, whiteScoreLead = -16.0, value = 0.0)
        assertFalse(HopelessPosition.isBarren(StoneColor.White, nobodyOwnsAnything, board))
    }

    /** 내 돌이 놓인 자리는 집이 아니다 — 살아 있는 돌 하나가 「집이 있다」로 읽히면 조건이 영영 안 걸린다. */
    @Test
    fun aPointUnderAStoneIsNotTerritory() {
        val ownStoneLooksOwned = estimate(whiteWinRate = 0.005, whiteScoreLead = -69.0, exceptions = mapOf(At(0, 0) to 0.9))
        assertTrue(HopelessPosition.isBarren(StoneColor.White, ownStoneLooksOwned, board))
    }

    /** 모르는 것으로 판단하지 않는다 — 승률·점수차·영역 가운데 필요한 값이 없으면 아니다. */
    @Test
    fun aMissingValueNeverCounts() {
        assertFalse(HopelessPosition.isLost(StoneColor.White, estimate(whiteWinRate = null, whiteScoreLead = -69.0)))
        assertFalse(HopelessPosition.isFarBehind(StoneColor.White, estimate(whiteWinRate = 0.005, whiteScoreLead = null)))
        val withoutOwnership = estimate(whiteWinRate = 0.005, whiteScoreLead = -69.0).copy(ownership = null)
        assertFalse(HopelessPosition.isBarren(StoneColor.White, withoutOwnership, board))
    }

    /** 종국은 **판의 80%를 둔 뒤**다(사용자 2026-10-07) — 9줄 81자리의 80%는 64.8이라 65수부터, 19줄은 289수부터. */
    @Test
    fun theEndgameStartsAtEightyPercentOfTheBoard() {
        assertFalse(HopelessPosition.isEndgame(boardAfter(64)))
        assertTrue(HopelessPosition.isEndgame(boardAfter(65)))
        val nineteen = GameState.empty(boardSize = BoardSize.Nineteen)
        assertFalse(HopelessPosition.isEndgame(nineteen.copy(moves = boardAfter(288).moves)))
        assertTrue(HopelessPosition.isEndgame(nineteen.copy(moves = boardAfter(289).moves)))
    }

    /**
     * **대국 중반에는 통과하지 않는다**(사용자 2026-10-07: *"중간에 통과를 누르는 행위는 나와선 안 된다"*). 진 판이 아무리 이어져도
     * 판의 80%를 두기 전에는 통과하지 않고, 80%를 넘겼으면 진 판이 2수 이어진 뒤에 통과한다.
     */
    @Test
    fun aLostGameIsPassedOnlyInTheEndgame() {
        val lost = estimate(whiteWinRate = 0.005, whiteScoreLead = -69.0)
        fun streakAfterTwoLostTurns(lastOwnMoveAt: Int): LosingStreak =
            LosingStreak().after(StoneColor.White, lost, boardAfter(lastOwnMoveAt - 2)).after(StoneColor.White, lost, boardAfter(lastOwnMoveAt))

        assertFalse(streakAfterTwoLostTurns(lastOwnMoveAt = 40).passesAt(boardAfter(41)), "no passing in the middle of the game")
        assertTrue(streakAfterTwoLostTurns(lastOwnMoveAt = 66).passesAt(boardAfter(67)))
        val oneLostTurn = LosingStreak().after(StoneColor.White, lost, boardAfter(66))
        assertFalse(oneLostTurn.passesAt(boardAfter(67)), "one lost turn is not enough")
    }

    /** 진 판이기만 하면 종국에서는 통과한다 — 집이 조금 남았거나 점수차가 작아도(사용자: *"승률이 0%이고 종국"*). */
    @Test
    fun inTheEndgameALostGameIsEnoughToPass() {
        val lostByALittle = estimate(whiteWinRate = 0.005, whiteScoreLead = -8.0, exceptions = mapOf(At(8, 8) to 0.9))
        val streak = LosingStreak().after(StoneColor.White, lostByALittle, boardAfter(66)).after(StoneColor.White, lostByALittle, boardAfter(68))

        assertTrue(streak.passesAt(boardAfter(69)))
        assertEquals(0, streak.farBehindTurns)
    }

    /**
     * **중반의 기권 제안** — 집 없이 크게 뒤진 채 2수(사용자 2026-10-07), 또는 크게 뒤진 채 10수(#213의 「불리한 채 10수」)면 제안한다.
     * 종국이면 제안하지 않는다 — 다음 차례에 통과로 끝낸다(계가로 끝나야 집 수 차이가 남는다).
     */
    @Test
    fun resignationIsOfferedInTheMiddleOfAHopelessGame() {
        val barren = estimate(whiteWinRate = 0.005, whiteScoreLead = -69.0)
        val farBehindWithTerritory = estimate(whiteWinRate = 0.005, whiteScoreLead = -45.0, exceptions = mapOf(At(8, 8) to 0.9))
        fun streak(estimate: ScoreEstimate, turns: Int, firstOwnMoveAt: Int = 20): Pair<LosingStreak, GameState> {
            var streak = LosingStreak()
            var state = boardAfter(firstOwnMoveAt)
            repeat(turns) { turn ->
                state = boardAfter(firstOwnMoveAt + turn * 2)
                streak = streak.after(StoneColor.White, estimate, state)
            }
            return streak to state
        }

        streak(barren, turns = 1).let { (one, state) -> assertFalse(one.offersResignation(state), "one turn is not enough") }
        streak(barren, turns = 2).let { (two, state) -> assertTrue(two.offersResignation(state)) }
        streak(farBehindWithTerritory, turns = 9).let { (nine, state) -> assertFalse(nine.offersResignation(state), "with territory left it takes ten turns") }
        streak(farBehindWithTerritory, turns = 10).let { (ten, state) -> assertTrue(ten.offersResignation(state)) }
        streak(barren, turns = 2, firstOwnMoveAt = 66).let { (late, state) -> assertFalse(late.offersResignation(state), "in the endgame the game is passed out instead") }
    }

    /** 한 번이라도 형세가 돌아오면 처음부터 센다. 형세를 모르는 차례도 이어진 것으로 치지 않는다. */
    @Test
    fun aRecoveryOrAnUnknownEvaluationStartsTheCountOver() {
        val barren = estimate(whiteWinRate = 0.005, whiteScoreLead = -69.0)
        val even = estimate(whiteWinRate = 0.5, whiteScoreLead = 0.0)

        val recovered = LosingStreak().after(StoneColor.White, barren, boardAfter(20)).after(StoneColor.White, even, boardAfter(22)).after(StoneColor.White, barren, boardAfter(24))
        assertEquals(1, recovered.barrenTurns)
        assertFalse(recovered.offersResignation(boardAfter(24)))

        val unknown = LosingStreak().after(StoneColor.White, barren, boardAfter(20)).after(StoneColor.White, null, boardAfter(22))
        assertEquals(LosingStreak(countedAtMoveCount = 22), unknown)
    }

    /** 센 것은 **그 판이 이어질 때만** 유효하다 — 그 뒤로 상대가 정확히 한 수 둔 판이어야 한다(새 대국·무르기·이어하기면 어긋난다). */
    @Test
    fun theCountOnlyContinuesOnTheSameGame() {
        val counted = LosingStreak(lostTurns = 5, countedAtMoveCount = 66)

        assertTrue(counted.continuesAt(boardAfter(67)))
        assertFalse(counted.continuesAt(boardAfter(66)))
        assertFalse(counted.continuesAt(boardAfter(0)))
        assertFalse(counted.passesAt(boardAfter(70)), "a count from another point of the game does not pass")
    }

    private companion object {
        fun At(row: Int, column: Int) = BoardCoordinate(row, column)
    }
}
