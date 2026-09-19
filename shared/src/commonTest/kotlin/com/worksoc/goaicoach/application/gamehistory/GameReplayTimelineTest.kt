package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.movereview.MoveReviewTone
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 대국 다시보기(백로그 #156)가 되짚는 계산. */
class GameReplayTimelineTest {

    private fun play(row: Int, column: Int, player: StoneColor) =
        Move.Play(player, BoardCoordinate(row, column))

    private fun timelineOf(moves: List<Move>, handicapCount: Int = 0) =
        buildGameReplayTimeline(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Chinese,
            handicapCount = handicapCount,
            komi = 6.5,
            moves = moves,
        )

    @Test
    fun theStartPositionIsAlwaysThere() {
        val timeline = timelineOf(emptyList())

        assertEquals(1, timeline.states.size)
        assertEquals(0, timeline.lastMoveNumber)
        assertTrue(timeline.states.single().stones.isEmpty())
    }

    /**
     * ⚠️ **수순 번호가 곧 인덱스다.** 저장된 `moveNumber`가 *"그 수를 둔 뒤의 `moves.size`"* 라
     * 이 대응이 깨지면 형세·실착 표시가 **한 수씩 밀린다.**
     */
    @Test
    fun theMoveNumberIsTheIndexIntoTheStates() {
        val moves = listOf(
            play(2, 2, StoneColor.Black),
            play(6, 6, StoneColor.White),
            play(2, 6, StoneColor.Black),
        )

        val timeline = timelineOf(moves)

        assertEquals(4, timeline.states.size)
        assertEquals(3, timeline.lastMoveNumber)
        moves.indices.forEach { index ->
            assertEquals(index + 1, timeline.states[index + 1].moves.size)
        }
    }

    @Test
    fun aHandicapGameStartsWithStonesAndWhiteToPlay() {
        val timeline = timelineOf(emptyList(), handicapCount = 4)

        val start = timeline.states.first()
        assertEquals(4, start.stones.size)
        assertEquals(StoneColor.White, start.nextPlayer)
        assertEquals(4, start.handicapCount)
    }

    /**
     * ⚠️ **위법수가 화면을 죽이면 안 된다.** `GameState.play`는 `require`로 맨몸으로 던지고
     * 이 계산은 컴포지션 중에 불린다 — 되짚은 만큼은 보여 주고 어디서 끊겼는지 말한다.
     */
    @Test
    fun aBrokenRecordTruncatesInsteadOfThrowing() {
        val moves = listOf(
            play(2, 2, StoneColor.Black),
            play(6, 6, StoneColor.White),
            // 같은 자리에 또 둔다 — `BoardRules`가 "already occupied"로 던진다.
            play(2, 2, StoneColor.Black),
            play(4, 4, StoneColor.White),
        )

        val timeline = timelineOf(moves)

        assertEquals(3, timeline.truncatedAtMoveNumber)
        assertTrue(timeline.isTruncated)
        assertEquals(2, timeline.lastMoveNumber)
    }

    @Test
    fun stateAtClampsInsteadOfCrashing() {
        val timeline = timelineOf(listOf(play(2, 2, StoneColor.Black)))

        assertEquals(timeline.states.first(), timeline.stateAt(-5))
        assertEquals(timeline.states.last(), timeline.stateAt(99))
    }

    private fun marker(moveNumber: Int, pointLoss: Double?) =
        MoveReviewMarker(
            coordinate = BoardCoordinate(1, 1),
            moveNumber = moveNumber,
            tone = MoveReviewTone.Blunder,
            pointLoss = pointLoss,
        )

    @Test
    fun onlyMovesAtOrOverTheThresholdCount() {
        val markers = listOf(
            marker(10, 9.9),
            marker(20, BlunderPointLossThreshold),
            marker(30, 40.0),
        )

        assertEquals(listOf(20, 30), blunderMoveNumbers(markers))
    }

    /**
     * ⚠️ `pointLoss == null`은 **옛 저장분**이다 — 값이 없는 것을 0으로 보아 "실수 아님"으로
     * 넘기지도, 임계를 넘은 것으로 세지도 않는다.
     */
    @Test
    fun anUnknownPointLossIsNeverCountedAsABlunder() {
        assertEquals(emptyList(), blunderMoveNumbers(listOf(marker(7, null))))
    }

    @Test
    fun blunderNumbersComeBackSortedAndDeduplicated() {
        val markers = listOf(marker(30, 12.0), marker(10, 11.0), marker(30, 15.0))

        assertEquals(listOf(10, 30), blunderMoveNumbers(markers))
    }

    private fun snapshot(moveNumber: Int, whiteScoreLead: Double?) =
        ScoreSnapshot(
            moveNumber = moveNumber,
            whiteScoreLead = whiteScoreLead,
            source = ScoreSnapshotSource.EngineEstimate,
        )

    @Test
    fun theScoreLookupFallsBackToTheNearestEarlierSnapshot() {
        val snapshots = listOf(snapshot(10, 1.0), snapshot(30, -2.0))

        assertEquals(10, scoreSnapshotUpTo(snapshots, 20)?.moveNumber)
        assertEquals(30, scoreSnapshotUpTo(snapshots, 30)?.moveNumber)
    }

    /**
     * ⚠️ **없으면 `null`이다 — 0으로 메우지 않는다.** `0.0`은 "호각"이라는 뜻이 있어서, 재지
     * 않은 구간을 0으로 채우면 그래프가 *"이때는 호각이었다"* 고 거짓말을 한다.
     */
    @Test
    fun anUnmeasuredStretchHasNoScoreAtAll() {
        val snapshots = listOf(snapshot(30, -2.0), snapshot(40, null))

        assertNull(scoreSnapshotUpTo(snapshots, 20))
        assertNull(scoreSnapshotUpTo(emptyList(), 100))
        // 값이 비어 있는 스냅샷은 "잰 적 있음"으로 세지 않는다.
        assertEquals(30, scoreSnapshotUpTo(snapshots, 40)?.moveNumber)
    }
}
