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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 가망 없는 판의 판정(백로그 #213, 사용자 2026-10-07) — **AI 집 0 · 상대 승률 99% 이상 · 점수차 30집 이상**을 함께 건다.
 * 조건 하나라도 빠지면 가망 없다고 보지 않는다: 실험실 E10에서 승률만으로는 8.7%를 잘못 걸었고, 「집 0」만 더하면 초반에 걸렸다.
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

    /** 백(AI)의 집이 하나도 없고, 흑 승률 99.5%, 69집 차 — 사용자의 폰 대국 28수째와 같은 모양이다. */
    @Test
    fun noTerritoryAHugeDeficitAndNoChanceIsHopeless() {
        assertTrue(HopelessPosition.isHopelessFor(StoneColor.White, estimate(whiteWinRate = 0.005, whiteScoreLead = -69.0), board))
    }

    /** 같은 형세를 **이기는 쪽**에서 보면 가망 없지 않다 — 흑 좌석의 AI에게도 부호가 맞아야 한다. */
    @Test
    fun theSameBoardIsNotHopelessForTheWinner() {
        assertFalse(HopelessPosition.isHopelessFor(StoneColor.Black, estimate(whiteWinRate = 0.005, whiteScoreLead = -69.0), board))
        assertTrue(HopelessPosition.isHopelessFor(StoneColor.Black, estimate(whiteWinRate = 0.995, whiteScoreLead = 69.0, value = 0.9), board))
    }

    /** 조건 하나라도 빠지면 아니다 — 승률이 1%를 넘거나, 점수차가 30집에 못 미치거나, 집이 한 자리라도 있으면. */
    @Test
    fun eachOfTheThreeConditionsIsRequired() {
        assertFalse(HopelessPosition.isHopelessFor(StoneColor.White, estimate(whiteWinRate = 0.02, whiteScoreLead = -69.0), board), "a 2% chance is still a chance")
        assertFalse(HopelessPosition.isHopelessFor(StoneColor.White, estimate(whiteWinRate = 0.005, whiteScoreLead = -29.0), board), "29 points is not a huge deficit")
        val oneOwnPoint = estimate(whiteWinRate = 0.005, whiteScoreLead = -69.0, exceptions = mapOf(At(8, 8) to 0.7))
        assertFalse(HopelessPosition.isHopelessFor(StoneColor.White, oneOwnPoint, board), "one point of own territory means there is something to play for")
    }

    /**
     * 초반에는 아무도 집이 없다 — 「집 0」이 저절로 참이라, 점수차가 크지 않으면 걸리지 않아야 한다
     * (E10에서 잘못 걸린 두 판이 모두 초반, 16집 차였다).
     */
    @Test
    fun anEarlyGameWithNobodyOwningAnythingIsNotHopeless() {
        val nobodyOwnsAnything = estimate(whiteWinRate = 0.005, whiteScoreLead = -16.0, value = 0.0)
        assertFalse(HopelessPosition.isHopelessFor(StoneColor.White, nobodyOwnsAnything, board))
    }

    /** 내 돌이 놓인 자리는 집이 아니다 — 살아 있는 돌 하나가 「집이 있다」로 읽히면 조건이 영영 안 걸린다. */
    @Test
    fun aPointUnderAStoneIsNotTerritory() {
        val ownStoneLooksOwned = estimate(whiteWinRate = 0.005, whiteScoreLead = -69.0, exceptions = mapOf(At(0, 0) to 0.9))
        assertTrue(HopelessPosition.isHopelessFor(StoneColor.White, ownStoneLooksOwned, board))
    }

    /** 모르는 것으로 통과하지 않는다 — 승률·점수차·영역 가운데 하나라도 없으면 가망 없다고 보지 않는다. */
    @Test
    fun aMissingValueIsNeverHopeless() {
        assertFalse(HopelessPosition.isHopelessFor(StoneColor.White, estimate(whiteWinRate = null, whiteScoreLead = -69.0), board))
        assertFalse(HopelessPosition.isHopelessFor(StoneColor.White, estimate(whiteWinRate = 0.005, whiteScoreLead = null), board))
        val withoutOwnership = estimate(whiteWinRate = 0.005, whiteScoreLead = -69.0).copy(ownership = null)
        assertFalse(HopelessPosition.isHopelessFor(StoneColor.White, withoutOwnership, board))
    }

    private companion object {
        fun At(row: Int, column: Int) = BoardCoordinate(row, column)
    }
}
