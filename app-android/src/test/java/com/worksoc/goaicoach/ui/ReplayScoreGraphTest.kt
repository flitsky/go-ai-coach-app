package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import com.worksoc.goaicoach.ui.history.ReplayScoreReading
import com.worksoc.goaicoach.ui.history.replayGraphMoveAtScroll
import com.worksoc.goaicoach.ui.history.replayGraphScrollForMove
import com.worksoc.goaicoach.ui.history.replayScoreReadings
import org.junit.Assert.assertEquals
import org.junit.Test

/** 다시보기의 큰 형세 그래프(backlog #226) — 가로로 넘긴 만큼이 어느 수인가, 그 수는 어디에 오는가. */
class ReplayScoreGraphTest {
    private val pxPerMove = 30f

    /** 가운데 표시선 아래의 수 = 넘긴 거리 ÷ 한 수의 폭, 가장 가까운 수로. 0수와 마지막 수를 넘지 않는다. */
    @Test
    fun theMoveUnderTheCursorIsTheNearestOne() {
        assertEquals(0, replayGraphMoveAtScroll(scrollPx = 0, pxPerMove = pxPerMove, lastMoveNumber = 130))
        assertEquals(0, replayGraphMoveAtScroll(scrollPx = 14, pxPerMove = pxPerMove, lastMoveNumber = 130))
        assertEquals(1, replayGraphMoveAtScroll(scrollPx = 16, pxPerMove = pxPerMove, lastMoveNumber = 130))
        assertEquals(37, replayGraphMoveAtScroll(scrollPx = 37 * 30, pxPerMove = pxPerMove, lastMoveNumber = 130))
        assertEquals(130, replayGraphMoveAtScroll(scrollPx = 99_999, pxPerMove = pxPerMove, lastMoveNumber = 130))
        assertEquals(0, replayGraphMoveAtScroll(scrollPx = -500, pxPerMove = pxPerMove, lastMoveNumber = 130))
    }

    /** 수 → 스크롤 → 수가 제자리로 돌아온다 — 버튼으로 옮긴 수를 그래프가 따라간 뒤 다시 다른 수로 읽히면 수순이 떨린다. */
    @Test
    fun aMoveMapsToAScrollThatMapsBackToTheSameMove() {
        listOf(8f, 31.5f, 33.75f).forEach { width ->
            (0..300).forEach { move ->
                assertEquals("폭 $width, $move 수", move, replayGraphMoveAtScroll(replayGraphScrollForMove(move, width), width, lastMoveNumber = 300))
            }
        }
    }

    /** 아직 화면 크기를 모르는 순간(폭 0)과 빈 판에서도 터지지 않는다. */
    @Test
    fun degenerateSizesAreHarmless() {
        assertEquals(0, replayGraphMoveAtScroll(scrollPx = 120, pxPerMove = 0f, lastMoveNumber = 130))
        assertEquals(0, replayGraphMoveAtScroll(scrollPx = 120, pxPerMove = pxPerMove, lastMoveNumber = 0))
        assertEquals(0, replayGraphScrollForMove(moveNumber = -3, pxPerMove = pxPerMove))
    }

    /**
     * 그래프의 점은 **판 전체**의 형세다 — 0수(0집)에서 시작해 수순대로, 흑이 앞서면 +. 점수차가 없는 기록은 점이 되지 않고,
     * 같은 수에 둘이면 뒤의 것(다시 잰 값)을 쓴다.
     */
    @Test
    fun theReadingsCoverTheWholeGameInMoveOrder() {
        fun snapshot(move: Int, whiteLead: Double?, winRate: Double? = null) =
            ScoreSnapshot(moveNumber = move, whiteScoreLead = whiteLead, whiteWinRate = winRate, source = ScoreSnapshotSource.EngineEstimate)

        val readings = replayScoreReadings(
            listOf(snapshot(3, -4.0), snapshot(1, 2.5), snapshot(2, null, winRate = 0.4), snapshot(3, -6.0), snapshot(5, 10.0)),
        )

        assertEquals(
            listOf(ReplayScoreReading(0, 0.0), ReplayScoreReading(1, -2.5), ReplayScoreReading(3, 6.0), ReplayScoreReading(5, -10.0)),
            readings,
        )
        assertEquals(listOf(ReplayScoreReading(0, 0.0)), replayScoreReadings(emptyList()))
    }
}
