package com.worksoc.goaicoach.shared.playstyle

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 국면과, 진 판을 AI가 어떻게 끝내는가(백로그 #213·#221) — **초반은 아무것도 하지 않고, 중반·후반에는 기권을 제안하고, 종반에는 통과한다.**
 *
 * 숫자는 사용자가 정했거나 골랐다(2026-10-07·08): 국면 30·60·80% · 상대 착수 뒤의 형세 5회 · 문턱 중반 15% · 후반 10% ·
 * 승률 99% 이상 2수. 여기서 그 숫자와 갈림을 고정한다.
 */
class HopelessPositionTest {
    /** 수순이 [moves]개인 판 — 흑이 먼저 두고 번갈아 둔다(수의 내용은 판정과 무관해 통과로 채운다). */
    private fun boardAfter(moves: Int, size: BoardSize = BoardSize.Nine): GameState =
        GameState.empty(boardSize = size).copy(
            moves = List(moves) { index -> Move.Pass(if (index % 2 == 0) StoneColor.Black else StoneColor.White) },
            nextPlayer = if (moves % 2 == 0) StoneColor.Black else StoneColor.White,
        )

    private fun estimate(whiteWinRate: Double?, whiteScoreLead: Double? = -40.0) =
        ScoreEstimate(status = EngineStatus.ready("estimated"), whiteWinRate = whiteWinRate, whiteScoreLead = whiteScoreLead, summary = "estimated")

    /** [moveNumber]번째 수 직후의 형세 — 백 기준 점수차. */
    private fun reading(moveNumber: Int, whiteScoreLead: Double?, source: ScoreSnapshotSource = ScoreSnapshotSource.HumanNetworkEstimate) =
        ScoreSnapshot(moveNumber = moveNumber, whiteScoreLead = whiteScoreLead, source = source)

    /** 흑(상대)이 둔 홀수 번째 수들 직후의 형세를 같은 값으로 채운다 — 백(AI)이 [whiteScoreLead]만큼 앞선(−면 뒤진) 판. */
    private fun readingsAfterBlackMoves(moveNumbers: Iterable<Int>, whiteScoreLead: Double) =
        moveNumbers.map { moveNumber -> reading(moveNumber, whiteScoreLead) }

    // ── 국면 ────────────────────────────────────────────────────────────────────────────────

    /** 사용자가 정한 경계(2026-10-08) — 수순 길이 ÷ 자리 수가 30% · 60% · 80%. 9줄은 25 · 49 · 65수째부터다. */
    @Test
    fun thePhasesSplitAtThirtySixtyAndEightyPercentOfTheBoard() {
        fun phases(size: BoardSize, vararg moveCounts: Int) = moveCounts.map { GamePhase.at(it, size) }

        assertEquals(
            listOf(GamePhase.Opening, GamePhase.Opening, GamePhase.Middle, GamePhase.Middle, GamePhase.Late, GamePhase.Late, GamePhase.Endgame),
            phases(BoardSize.Nine, 0, 24, 25, 48, 49, 64, 65),
        )
        assertEquals(
            listOf(GamePhase.Opening, GamePhase.Middle, GamePhase.Middle, GamePhase.Late, GamePhase.Late, GamePhase.Endgame),
            phases(BoardSize.Thirteen, 50, 51, 101, 102, 135, 136),
        )
        assertEquals(
            listOf(GamePhase.Opening, GamePhase.Middle, GamePhase.Middle, GamePhase.Late, GamePhase.Late, GamePhase.Endgame),
            phases(BoardSize.Nineteen, 108, 109, 216, 217, 288, 289),
        )
        assertEquals(GamePhase.Late, GamePhase.of(boardAfter(60)))
    }

    /** 문턱은 판 크기에 비례하고 국면마다 다르다 — 중반 15% · 후반 10%. 초반과 종반에는 제안이 없다. */
    @Test
    fun theDeficitBarScalesWithTheBoardAndIsLowerInTheLateGame() {
        assertEquals(12.15, HopelessPosition.deficitBar(GamePhase.Middle, BoardSize.Nine)!!, 1e-9)
        assertEquals(8.1, HopelessPosition.deficitBar(GamePhase.Late, BoardSize.Nine)!!, 1e-9)
        assertEquals(25.35, HopelessPosition.deficitBar(GamePhase.Middle, BoardSize.Thirteen)!!, 1e-9)
        assertEquals(36.1, HopelessPosition.deficitBar(GamePhase.Late, BoardSize.Nineteen)!!, 1e-9)
        assertNull(HopelessPosition.deficitBar(GamePhase.Opening, BoardSize.Nine))
        assertNull(HopelessPosition.deficitBar(GamePhase.Endgame, BoardSize.Nine))
    }

    // ── 중반·후반의 기권 제안 ─────────────────────────────────────────────────────────────────

    /**
     * **상대가 둔 뒤의 형세가 5회 연속 문턱 밖**이면 제안한다(사용자 2026-10-08). 백(AI)이 둘 차례인 35수 판 — 흑이 27·29·31·33·35수째를
     * 뒀고 그때마다 백이 13집 뒤졌다(9줄 중반의 문턱은 12.15집).
     */
    @Test
    fun fiveReadingsInARowBeyondTheBarOfferResignation() {
        val state = boardAfter(35)
        val grounds = HopelessPosition.resignationGrounds(StoneColor.White, state, readingsAfterBlackMoves(27..35 step 2, whiteScoreLead = -13.0))

        assertNotNull(grounds)
        assertEquals(listOf(27, 29, 31, 33, 35), grounds.readings.map { it.moveNumber }, "the grounds are listed in move order")
        assertTrue(grounds.readings.all { it.ownScoreLead == -13.0 && it.deficitBar == 12.15 })
    }

    /** 넷으로는 모자라다 — 다섯 번째가 아직 없거나(기록이 빈 수), 하나가 따라붙었으면(문턱 안) 처음부터 다시 모인다. */
    @Test
    fun fourReadingsOrOneRecoveryIsNotEnough() {
        val state = boardAfter(35)

        assertNull(HopelessPosition.resignationGrounds(StoneColor.White, state, readingsAfterBlackMoves(29..35 step 2, whiteScoreLead = -13.0)), "the reading after move 27 is missing")

        val recoveredOnce = readingsAfterBlackMoves(27..35 step 2, whiteScoreLead = -13.0).map { if (it.moveNumber == 31) it.copy(whiteScoreLead = -12.0) else it }
        assertNull(HopelessPosition.resignationGrounds(StoneColor.White, state, recoveredOnce), "12 points behind is inside the bar")

        val recoveredNow = readingsAfterBlackMoves(25..35 step 2, whiteScoreLead = -13.0).map { if (it.moveNumber == 35) it.copy(whiteScoreLead = -5.0) else it }
        assertNull(HopelessPosition.resignationGrounds(StoneColor.White, state, recoveredNow), "the latest reading decides first")
    }

    /** **초반에는 로직이 없다**(사용자) — 초반에 잰 값은 아무리 뒤져도 세지 않는다. 9줄의 중반은 25수째부터다. */
    @Test
    fun readingsTakenInTheOpeningNeverCount() {
        val crushed = readingsAfterBlackMoves(1..33 step 2, whiteScoreLead = -60.0)

        assertNull(HopelessPosition.resignationGrounds(StoneColor.White, boardAfter(23), crushed), "still in the opening")
        assertNull(HopelessPosition.resignationGrounds(StoneColor.White, boardAfter(31), crushed), "only 25·27·29·31 were taken in the middle game")
        assertNotNull(HopelessPosition.resignationGrounds(StoneColor.White, boardAfter(33), crushed), "the fifth middle-game reading")
    }

    /** 후반의 문턱은 낮다 — 9집 뒤진 판은 중반에는 제안이 아니고(문턱 12.15집), 후반의 형세 5회로는 제안이다(문턱 8.1집). */
    @Test
    fun theLateGameBarIsLowerThanTheMiddleGameBar() {
        val nineBehind = readingsAfterBlackMoves(25..63 step 2, whiteScoreLead = -9.0)

        assertNull(HopelessPosition.resignationGrounds(StoneColor.White, boardAfter(47), nineBehind), "not enough for the middle game")
        // 49수째부터 후반이다 — 49·51·53·55·57의 다섯이 모여야 한다. 그 전에는 중반에 잰 값이 섞여 있다.
        assertNull(HopelessPosition.resignationGrounds(StoneColor.White, boardAfter(55), nineBehind), "a middle-game reading is judged by the middle-game bar")
        val grounds = assertNotNull(HopelessPosition.resignationGrounds(StoneColor.White, boardAfter(57), nineBehind))
        assertTrue(grounds.readings.all { it.deficitBar == 8.1 })
    }

    /** 국면이 바뀌는 자리에서는 값마다 **제가 재어진 국면의 문턱**을 쓴다 — 중반에 13집, 후반에 9집 뒤진 판은 이어진 것이다. */
    @Test
    fun eachReadingIsJudgedByThePhaseItWasTakenIn() {
        val readings = readingsAfterBlackMoves(listOf(43, 45, 47), whiteScoreLead = -13.0) + readingsAfterBlackMoves(listOf(49, 51), whiteScoreLead = -9.0)

        val grounds = assertNotNull(HopelessPosition.resignationGrounds(StoneColor.White, boardAfter(51), readings))
        assertEquals(listOf(12.15, 12.15, 12.15, 8.1, 8.1), grounds.readings.map { it.deficitBar })
    }

    /** **종반에는 제안하지 않는다** — 통과로 끝낸다(계가로 끝나야 집 수 차이가 남는다). 9줄의 종반은 65수째부터다. */
    @Test
    fun noOfferInTheEndgame() {
        val crushed = readingsAfterBlackMoves(25..65 step 2, whiteScoreLead = -60.0)

        assertNotNull(HopelessPosition.resignationGrounds(StoneColor.White, boardAfter(63), crushed))
        assertNull(HopelessPosition.resignationGrounds(StoneColor.White, boardAfter(65), crushed))
    }

    /** 제 차례가 아니면 근거가 없다 — 기권은 제 차례에만 둘 수 있다. 이기는 쪽에서 보면 같은 기록이 아무것도 아니다(부호). */
    @Test
    fun onlyTheSideToMoveAndOnlyTheLosingSide() {
        val whiteIsBehind = readingsAfterBlackMoves(25..35 step 2, whiteScoreLead = -30.0)
        assertNull(HopelessPosition.resignationGrounds(StoneColor.White, boardAfter(34), whiteIsBehind), "black is to move")

        // 흑이 AI인 판: 백이 둔 짝수 번째 수 직후의 형세를 본다. 백이 30집 앞서면 흑이 뒤진 것이다.
        val afterWhiteMoves = (26..34 step 2).map { reading(it, whiteScoreLead = 30.0) }
        assertNotNull(HopelessPosition.resignationGrounds(StoneColor.Black, boardAfter(34), afterWhiteMoves))
        assertNull(HopelessPosition.resignationGrounds(StoneColor.Black, boardAfter(34), afterWhiteMoves.map { it.copy(whiteScoreLead = -30.0) }), "black is the one ahead")
    }

    /** 모르는 것으로 판단하지 않는다 — 값이 없는 기록과, 돌 수만 센 국소 계가(척도가 다르다)는 형세가 아니다. 주 모델이 다시 잰 값은 형세다. */
    @Test
    fun onlyNetworkEstimatesWithAScoreCount() {
        val state = boardAfter(35)
        val behind = readingsAfterBlackMoves(27..35 step 2, whiteScoreLead = -30.0)

        assertNull(HopelessPosition.resignationGrounds(StoneColor.White, state, behind.map { if (it.moveNumber == 29) it.copy(whiteScoreLead = null, whiteWinRate = 0.0) else it }))
        assertNull(HopelessPosition.resignationGrounds(StoneColor.White, state, behind.map { if (it.moveNumber == 29) it.copy(source = ScoreSnapshotSource.LocalAreaEstimate) else it }))
        assertNotNull(HopelessPosition.resignationGrounds(StoneColor.White, state, behind.map { if (it.moveNumber == 29) it.copy(source = ScoreSnapshotSource.EngineEstimate) else it }))
    }

    /** AI가 둔 뒤의 형세(짝수 번째)는 보지 않는다 — 그 값이 아무리 좋아도 나빠도 상대가 둔 뒤의 다섯이 정한다. */
    @Test
    fun readingsAfterTheAisOwnMovesAreIgnored() {
        val state = boardAfter(35)
        val afterBlack = readingsAfterBlackMoves(27..35 step 2, whiteScoreLead = -30.0)
        val afterWhiteLooksFine = (26..34 step 2).map { reading(it, whiteScoreLead = 5.0) }

        assertNotNull(HopelessPosition.resignationGrounds(StoneColor.White, state, afterBlack + afterWhiteLooksFine))
        assertNull(HopelessPosition.resignationGrounds(StoneColor.White, state, afterWhiteLooksFine.map { it.copy(whiteScoreLead = -30.0) }), "no readings after the opponent's moves")
    }

    // ── 종반의 통과 ─────────────────────────────────────────────────────────────────────────

    /** 진 판 = 상대 승률 99% 이상(사용자의 「승률 0%」). 이기는 쪽에서 보면 아니고, 값이 없으면 아니다. */
    @Test
    fun aLostGameIsAWinRateOfOnePercentOrLess() {
        assertTrue(HopelessPosition.isLost(StoneColor.White, estimate(whiteWinRate = 0.005)))
        assertFalse(HopelessPosition.isLost(StoneColor.White, estimate(whiteWinRate = 0.02)), "a 2% chance is still a chance")
        assertFalse(HopelessPosition.isLost(StoneColor.Black, estimate(whiteWinRate = 0.005)))
        assertTrue(HopelessPosition.isLost(StoneColor.Black, estimate(whiteWinRate = 0.995)))
        assertFalse(HopelessPosition.isLost(StoneColor.White, estimate(whiteWinRate = null)))
    }

    /**
     * **종반 전에는 통과하지 않는다**(사용자 2026-10-07: *"중간에 통과를 누르는 행위는 나와선 안 된다"*). 진 판이 아무리 이어져도
     * 판의 80%를 두기 전에는 통과하지 않고, 80%를 넘겼으면 진 판이 2수 이어진 뒤에 통과한다.
     */
    @Test
    fun aLostGameIsPassedOnlyInTheEndgame() {
        val lost = estimate(whiteWinRate = 0.005)
        fun streakAfterTwoLostTurns(lastOwnMoveAt: Int): LosingStreak =
            LosingStreak().after(StoneColor.White, lost, boardAfter(lastOwnMoveAt - 2)).after(StoneColor.White, lost, boardAfter(lastOwnMoveAt))

        assertFalse(streakAfterTwoLostTurns(lastOwnMoveAt = 40).passesAt(boardAfter(41)), "no passing before the endgame")
        assertFalse(streakAfterTwoLostTurns(lastOwnMoveAt = 62).passesAt(boardAfter(63)), "the late game is not the endgame")
        assertTrue(streakAfterTwoLostTurns(lastOwnMoveAt = 66).passesAt(boardAfter(67)))
        val oneLostTurn = LosingStreak().after(StoneColor.White, lost, boardAfter(66))
        assertFalse(oneLostTurn.passesAt(boardAfter(67)), "one lost turn is not enough")
    }

    /** 진 판이기만 하면 종반에서는 통과한다 — 점수차가 작아도(사용자: *"승률이 0%이고 종국"*). 통과는 점수차를 묻지 않는다. */
    @Test
    fun inTheEndgameALostGameIsEnoughToPass() {
        val lostByALittle = estimate(whiteWinRate = 0.005, whiteScoreLead = -3.0)
        val streak = LosingStreak().after(StoneColor.White, lostByALittle, boardAfter(66)).after(StoneColor.White, lostByALittle, boardAfter(68))

        assertTrue(streak.passesAt(boardAfter(69)))
    }

    /** 한 번이라도 승률이 돌아오면 처음부터 센다. 형세를 모르는 차례도 이어진 것으로 치지 않는다. */
    @Test
    fun aRecoveryOrAnUnknownEvaluationStartsTheCountOver() {
        val lost = estimate(whiteWinRate = 0.005)
        val even = estimate(whiteWinRate = 0.5)

        val recovered = LosingStreak().after(StoneColor.White, lost, boardAfter(66)).after(StoneColor.White, even, boardAfter(68)).after(StoneColor.White, lost, boardAfter(70))
        assertEquals(LosingStreak(lostTurns = 1, countedAtMoveCount = 70), recovered)
        assertFalse(recovered.passesAt(boardAfter(71)))

        val unknown = LosingStreak().after(StoneColor.White, lost, boardAfter(66)).after(StoneColor.White, null, boardAfter(68))
        assertEquals(LosingStreak(countedAtMoveCount = 68), unknown)
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
}
