package com.worksoc.goaicoach.application.gamehistory

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 대국 뒤 「복기 하기」 추천이 세는 「5집 이상 실착」(백로그 #200). */
class ReviewRecommendationTest {

    private fun black(row: Int = 0, column: Int = 0): Move = Move.Play(StoneColor.Black, BoardCoordinate(row, column))

    private fun white(row: Int = 0, column: Int = 0): Move = Move.Play(StoneColor.White, BoardCoordinate(row, column))

    private fun engine(moveNumber: Int, whiteLead: Double?) =
        ScoreSnapshot(moveNumber = moveNumber, whiteScoreLead = whiteLead, source = ScoreSnapshotSource.EngineEstimate)

    private fun local(moveNumber: Int, whiteLead: Double) =
        ScoreSnapshot(moveNumber = moveNumber, whiteScoreLead = whiteLead, source = ScoreSnapshotSource.LocalAreaEstimate)

    private fun final(moveNumber: Int, whiteLead: Double) =
        ScoreSnapshot(moveNumber = moveNumber, whiteScoreLead = whiteLead, source = ScoreSnapshotSource.FinalScore)

    /** 흑이 두어 **백 리드가 늘면** 흑의 실착이다 — 줄면(흑에게 유리) 세지 않는다. */
    @Test
    fun aBlackMoveIsAMistakeWhenWhitesLeadGrows() {
        val moves = listOf(black(), white(1, 1), black(2, 2))
        val snapshots = listOf(
            engine(0, 0.0),
            engine(1, 6.0), // 흑이 두고 백 +6 — 흑의 실착
            engine(2, 6.0),
            engine(3, -1.0), // 흑이 두고 백 -7 — 흑에게 유리, 실착이 아니다
        )

        assertEquals(1, countReviewRecommendationMistakes(moves, snapshots))
    }

    /** 백이 두어 **백 리드가 줄면** 백의 실착이다 — 늘면(백에게 유리) 세지 않는다. */
    @Test
    fun aWhiteMoveIsAMistakeWhenWhitesLeadShrinks() {
        val moves = listOf(black(), white(1, 1), black(2, 2), white(3, 3))
        val snapshots = listOf(
            engine(0, 0.0),
            engine(1, 0.0),
            engine(2, -8.0), // 백이 두고 백 -8 — 백의 실착
            engine(3, -8.0),
            engine(4, 4.0), // 백이 두고 백 +12 — 백에게 유리, 실착이 아니다
        )

        assertEquals(1, countReviewRecommendationMistakes(moves, snapshots))
    }

    /** ⚠️ **누가 두었든 센다**(U-57) — 이 함수는 사람·AI 좌석을 받지도 않는다. 흑백 각각의 실착이 모두 세어진다. */
    @Test
    fun mistakesOfBothSidesAreCounted() {
        val moves = listOf(black(), white(1, 1))
        val snapshots = listOf(engine(0, 0.0), engine(1, 5.0), engine(2, -1.0))

        assertEquals(2, countReviewRecommendationMistakes(moves, snapshots))
    }

    /** 임계는 **5집 이상**이다 — 4.9는 세지 않고 정확히 5.0은 센다. */
    @Test
    fun theThresholdIsFivePointsInclusive() {
        assertEquals(5.0, ReviewRecommendationMistakeThreshold)
        val moves = listOf(black(), white(1, 1))

        assertEquals(
            0,
            countReviewRecommendationMistakes(moves, listOf(engine(0, 0.0), engine(1, 4.9), engine(2, 0.0))),
        )
        assertEquals(
            1,
            countReviewRecommendationMistakes(moves, listOf(engine(0, 0.0), engine(1, 5.0), engine(2, 5.0))),
        )
        assertEquals(
            1,
            countReviewRecommendationMistakes(moves, listOf(engine(0, 0.0), engine(1, 0.0), engine(2, -5.0))),
        )
        assertEquals(
            0,
            countReviewRecommendationMistakes(moves, listOf(engine(0, 0.0), engine(1, 0.0), engine(2, -4.9))),
        )
    }

    /**
     * ⚠️ **출처가 섞인 수는 건너뛴다.** 로컬 영역 계산·종국 점수는 신경망 우세와 척도가 달라, 섞어 빼면 가짜 대형
     * 실착이 생긴다(엔진 평가가 끊긴 중반의 로컬 계산은 수십 집씩 튄다).
     */
    @Test
    fun aMoveNextToALocalOrFinalSnapshotIsSkipped() {
        val moves = listOf(black(), white(1, 1), black(2, 2), white(3, 3))
        val snapshots = listOf(
            engine(0, 0.0),
            local(1, 40.0), // 흑 1수: 앞은 엔진, 뒤는 로컬 — 건너뛴다
            engine(2, 0.0), // 백 2수: 앞이 로컬 — 건너뛴다
            engine(3, 1.0), // 흑 3수: 엔진→엔진, +1 — 실착 아님(잰 수는 이것 하나)
            final(4, -30.0), // 백 4수: 뒤가 종국 점수 — 건너뛴다
        )

        assertEquals(0, countReviewRecommendationMistakes(moves, snapshots))
    }

    /** 잴 수 있는 쌍이 **로컬·종국뿐**이면 `0`이 아니라 `null`이다 — 배지·말풍선을 아예 띄우지 않는다. */
    @Test
    fun onlyNonEnginePairsMeansNoData() {
        val moves = listOf(black(), white(1, 1))

        assertNull(countReviewRecommendationMistakes(moves, listOf(local(0, 0.0), local(1, 30.0), final(2, -20.0))))
    }

    /**
     * ⚠️ **둔 쪽은 수순에서 읽는다** — "홀수 수 = 흑"으로 가정하면 접바둑(백이 먼저)에서 방향이 통째로 뒤집힌다.
     * 접바둑에서 백이 통과한 뒤 흑이 둔 4수가 백 리드를 키웠다면 그것은 **흑의** 실착이다.
     */
    @Test
    fun theMoverComesFromTheMoveListNotFromMoveNumberParity() {
        val moves = listOf(white(4, 4), black(2, 2), Move.Pass(StoneColor.White), black(6, 6))
        val snapshots = listOf(
            engine(0, -20.0),
            engine(1, -20.0),
            engine(2, -20.0),
            engine(3, -20.0),
            engine(4, -13.0), // 흑이 두고 백 +7 — 흑의 실착(짝수 = 백으로 가정하면 "백에게 유리"로 읽혀 놓친다)
        )

        assertEquals(1, countReviewRecommendationMistakes(moves, snapshots))
    }

    /** 접바둑은 **백이 먼저** 둔다 — 1수가 백의 수로 잰다. */
    @Test
    fun aHandicapGameStartsWithWhitesMove() {
        val moves = listOf(white(4, 4), black(2, 2))
        val snapshots = listOf(
            engine(0, -20.0),
            engine(1, -26.0), // 백이 두고 백 -6 — 백의 실착(번갈아 가정이면 흑 1수로 읽혀 "흑에게 유리"가 된다)
            engine(2, -26.0),
        )

        assertEquals(1, countReviewRecommendationMistakes(moves, snapshots))
    }

    /** 통과도 수다 — 둘 곳이 있는데 통과해 5집 이상 잃었다면 실착이다. */
    @Test
    fun aCostlyPassIsAMistakeToo() {
        val moves = listOf(black(), Move.Pass(StoneColor.White))
        val snapshots = listOf(engine(0, 0.0), engine(1, 0.0), engine(2, -9.0))

        assertEquals(1, countReviewRecommendationMistakes(moves, snapshots))
    }

    /** 기권은 잴 수가 아니다 — 그 뒤에 무엇이 적혀 있든 세지 않는다. */
    @Test
    fun aResignationIsNeverCounted() {
        val moves = listOf(black(), Move.Resign(StoneColor.White))
        val snapshots = listOf(engine(0, 0.0), engine(1, 0.0), engine(2, -40.0))

        assertEquals(0, countReviewRecommendationMistakes(moves, snapshots))
    }

    /** 기록이 **하나도 없으면** `null` — 0으로 읽지 않는다. 앞뒤가 없는 외톨이 스냅샷·값 없는 스냅샷도 같다. */
    @Test
    fun noUsableDataIsNullNotZero() {
        val moves = listOf(black(), white(1, 1), black(2, 2))

        assertNull(countReviewRecommendationMistakes(moves, emptyList()))
        assertNull(countReviewRecommendationMistakes(emptyList(), listOf(engine(0, 0.0))))
        assertNull(countReviewRecommendationMistakes(moves, listOf(engine(0, 0.0), engine(2, 9.0))))
        assertNull(countReviewRecommendationMistakes(moves, listOf(engine(0, 0.0), engine(1, null))))
    }

    /** 재 봤는데 없었다면 `0`이다 — `null`과 다르다. */
    @Test
    fun measuredButCleanIsZero() {
        val moves = listOf(black(), white(1, 1))

        assertEquals(0, countReviewRecommendationMistakes(moves, listOf(engine(0, 0.0), engine(1, 1.0), engine(2, 0.5))))
    }

    /** 개수는 **자르지 않는다** — `9+`는 화면의 몫이다. */
    @Test
    fun largeCountsAreNotCapped() {
        val moveCount = 40
        val moves = (1..moveCount).map { number -> if (number % 2 == 1) black() else white() }
        // 흑이 두면 백 +10, 백이 두면 백 -10 — 매 수가 둔 쪽의 10집 실착이다.
        val snapshots = (0..moveCount).map { number -> engine(number, if (number % 2 == 1) 10.0 else 0.0) }

        assertEquals(moveCount, countReviewRecommendationMistakes(moves, snapshots))
    }
}
