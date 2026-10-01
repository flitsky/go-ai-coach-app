package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.movereview.MoveReviewTone
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun entry(playerSetup: PlayerSetup, moveCount: Int) =
        GameHistoryEntry(
            id = "test-id",
            playedAtMillis = 0L,
            boardSize = BoardSize.Nine.value,
            ruleset = Ruleset.Chinese,
            komi = 6.5,
            handicapCount = 0,
            playerSetup = playerSetup,
            moveCount = moveCount,
            humanColor = StoneColor.Black,
            winner = null,
        )

    /**
     * ⚠️ **이게 실제로 보고된 결함이다** — 옛 기록의 `moveEvaluations`가 비어 있어도(저장 시점
     * 코드가 다르게 계산했거나 아예 캐시를 안 남겼거나) [moves]·[scoreSnapshots](원 데이터)만
     * 있으면 다시보기가 지금 이 순간의 로직으로 매번 다시 계산해야 한다.
     */
    @Test
    fun replayRecomputesFromRawDataEvenWhenTheCachedEvaluationsAreEmpty() {
        val replay = GameReplayData(
            moves = listOf(play(1, 1, StoneColor.Black), play(2, 2, StoneColor.White)),
            scoreSnapshots = listOf(
                snapshot(0, 0.0),
                snapshot(1, 8.0),
                snapshot(2, -2.0),
            ),
            // "옛 기록"을 흉내낸다 — 캐시된 착수 평가가 비어 있다.
            moveEvaluations = emptyList(),
        )
        val historyEntry = entry(playerSetup = PlayerSetup(), moveCount = 2)

        val recomputed = deriveReplayMoveEvaluations(historyEntry, replay)

        // 1수(사람, 흑)는 백 리드가 0→8로 8집 손해라 임계(3집)를 넘는다. 2수(AI, 백)는
        // humanColors에서 걸러져 애초에 후보조차 아니다.
        assertEquals(listOf(1), recomputed.map { it.moveNumber })
        assertEquals(8.0, recomputed.single { it.moveNumber == 1 }.pointLoss)
    }

    /**
     * ⚠️ **「변곡점」은 [MoveReviewMarker]도 사람 진영도 보지 않는다**(2026-09-20 사용자
     * 리메이크 — "실착" 개념을 대체) — [ScoreSnapshot]만으로 사람:사람·사람:AI·AI:AI
     * 어느 조합의 대국에도 똑같이 뜬다.
     */
    @Test
    fun swingsAreFoundRegardlessOfWhoMoved() {
        // 백 리드: 0 → -8(1수, 흑에게 유리해짐) → -6(2수, 임계 밑) → 4(3수, 백에게 유리해짐).
        val snapshots = listOf(
            snapshot(0, 0.0),
            snapshot(1, -8.0),
            snapshot(2, -6.0),
            snapshot(3, 4.0),
        )

        val highlights = deriveScoreSwingHighlights(snapshots)

        assertEquals(listOf(1, 3), highlights.map { it.moveNumber })
        assertEquals(-8.0, highlights.single { it.moveNumber == 1 }.swing)
        assertEquals(10.0, highlights.single { it.moveNumber == 3 }.swing)
    }

    /** 부호와 무관하게 **변동폭의 절댓값**으로 임계·순위를 매긴다 — 흑이 유리해져도 변곡점이다. */
    @Test
    fun bothDirectionsOfSwingCanCrossTheThreshold() {
        val snapshots = listOf(
            snapshot(0, 0.0),
            snapshot(1, ScoreSwingThreshold - 0.1),
            snapshot(2, ScoreSwingThreshold - 0.1 - ScoreSwingThreshold),
        )

        // 1수는 임계 바로 밑이라 빠지고, 2수는 음의 방향으로 정확히 임계라 잡힌다.
        assertEquals(listOf(2), deriveScoreSwingHighlights(snapshots).map { it.moveNumber })
    }

    @Test
    fun moreThanMaxCountKeepsTheBiggestSwingsOnly() {
        val snapshots = listOf(
            snapshot(0, 0.0),
            snapshot(1, 6.0), // |swing| 6
            snapshot(2, 46.0), // |swing| 40
            snapshot(3, 26.0), // |swing| 20
            snapshot(4, 34.0), // |swing| 8
            snapshot(5, 22.0), // |swing| 12
        )

        // 5개가 임계를 넘지만 상한은 3 — 변동폭이 가장 작은 둘(1수:6.0, 4수:8.0)이 잘려 나간다.
        val highlights = deriveScoreSwingHighlights(snapshots, maxCount = 3)

        assertEquals(listOf(2, 3, 5), highlights.map { it.moveNumber })
    }

    /** 이전 수의 스냅샷이 아예 없으면(구멍) 그 수는 후보에서 빠진다 — 0으로 메우지 않는다. */
    @Test
    fun aMoveWithoutAnAdjacentSnapshotIsNeverACandidate() {
        val snapshots = listOf(snapshot(0, 0.0), snapshot(5, 40.0))

        assertEquals(emptyList(), deriveScoreSwingHighlights(snapshots))
    }

    /**
     * ⚠️ **앞뒤가 둘 다 신경망 평가일 때만 잰다**(백로그 #200). 엔진 평가가 끊긴 수의 로컬 영역 계산·종국 점수는
     * 척도가 달라 섞어 빼면 **가짜 변곡점**이 생긴다 — 아래 2·3·5수는 전부 수십 집이 "흔들린" 것처럼 보이지만
     * 한쪽이 신경망 평가가 아니라 후보가 아니다.
     */
    @Test
    fun swingsAcrossALocalOrFinalSnapshotAreIgnored() {
        val snapshots = listOf(
            snapshot(0, 0.0),
            snapshot(1, -8.0), // 엔진→엔진, |8| — 진짜 변곡점
            ScoreSnapshot(moveNumber = 2, whiteScoreLead = 30.0, source = ScoreSnapshotSource.LocalAreaEstimate),
            snapshot(3, -6.0),
            snapshot(4, -5.0),
            ScoreSnapshot(moveNumber = 5, whiteScoreLead = -40.0, source = ScoreSnapshotSource.FinalScore),
        )

        assertEquals(listOf(1), deriveScoreSwingHighlights(snapshots).map { it.moveNumber })
    }

    /**
     * ⚠️ **변곡점 임계는 「복기 하기」 추천과 같은 값이다**(2026-10-01 사용자 — 3점에서 5점으로 통일, 백로그 #207).
     * 한쪽만 바꾸면 이 단언이 깨진다 — 다시 갈라야 한다면 먼저 사용자에게 묻는다.
     */
    @Test
    fun theSwingThresholdIsTheReviewRecommendationThreshold() {
        assertEquals(ReviewRecommendationMistakeThreshold, ScoreSwingThreshold)
        assertEquals(5.0, ScoreSwingThreshold)

        // 옛 임계(3점)였다면 잡혔을 4점 변동은 이제 변곡점이 아니다.
        val snapshots = listOf(snapshot(0, 0.0), snapshot(1, 4.0), snapshot(2, -1.0))
        assertEquals(listOf(2), deriveScoreSwingHighlights(snapshots).map { it.moveNumber })
    }

    /**
     * ⚠️ **스냅샷이 있다고 잴 수 있는 것이 아니다**(백로그 #207) — 연속한 두 수가 모두 신경망 평가인 곳이 있어야
     * 한다. 아니면 화면이 「안정적으로 진행」 대신 「기록이 부족하다」고 말한다.
     */
    @Test
    fun swingsAreMeasurableOnlyWithAnAdjacentEnginePair() {
        fun local(moveNumber: Int) =
            ScoreSnapshot(moveNumber = moveNumber, whiteScoreLead = 0.0, source = ScoreSnapshotSource.LocalAreaEstimate)

        assertFalse(canMeasureScoreSwings(emptyList()))
        // 엔진이 준비되지 않은 판 — 그래프는 그려지지만 변곡점은 잴 수 없다.
        assertFalse(canMeasureScoreSwings(listOf(local(0), local(1), local(2))))
        // 신경망 평가가 있어도 서로 붙어 있지 않으면 잴 수 없다.
        assertFalse(canMeasureScoreSwings(listOf(snapshot(0, 0.0), local(1), snapshot(2, 1.0))))
        // 붙은 한 쌍이면 잴 수 있다 — 흔들림이 없으면 「변곡점 없이 안정적으로 진행」이 맞는 판이다.
        val steady = listOf(snapshot(0, 0.0), snapshot(1, 1.0), snapshot(2, 0.5))
        assertTrue(canMeasureScoreSwings(steady))
        assertEquals(emptyList(), deriveScoreSwingHighlights(steady))
    }
}
