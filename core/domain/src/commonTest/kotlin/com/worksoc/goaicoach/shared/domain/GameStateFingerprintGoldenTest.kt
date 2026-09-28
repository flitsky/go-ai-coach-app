package com.worksoc.goaicoach.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **국면 지문 골든 — [analysisFingerprint]의 출력을 글자 그대로 고정한다**(refactor backlog #36).
 *
 * 지문은 분석 캐시(`JsonPositionAnalysisCacheStore`)의 **저장되는 키**다. 그 안에 좌표 표기
 * ([BoardCoordinate.label]), 수 설명([describe] — `"Black D4"`, `"White pass"`), 색 이름이 들어 있다.
 * 이 중 하나라도 바뀌면 이미 저장된 캐시 키가 전부 무효가 되는데, **캐시 미스일 뿐이라 아무 데서도
 * 실패하지 않는다.** 기존 `GameStateFingerprintTest`는 "같다/다르다"만 보므로 그런 변경을 못 잡는다.
 *
 * 국면은 **정수 좌표로** 만든다 — `fromLabel`로 만들면 표기가 바뀔 때 입력도 함께 바뀌어 상쇄된다.
 * 13x13 접바둑 2점에서 백이 먼저 두고, 흑 따냄·백 따냄·양쪽 통과를 거쳐 마지막 수가 패를 따낸다
 * (통과는 패를 지우므로 패가 살아 있으려면 마지막이어야 한다).
 */
class GameStateFingerprintGoldenTest {

    @Test
    fun handicapGameWithCapturesPassesAndALiveKoHasAGoldenFingerprint() {
        val state = GoldenMoves.fold(
            GameState.withHandicap(BoardSize.Thirteen, Ruleset.Japanese, handicapCount = 2),
        ) { current, move -> current.play(move) }

        assertEquals(
            GoldenFingerprint,
            state.analysisFingerprint(),
        )
    }

    /** 이어하기·다시보기가 수순에서 국면을 다시 세울 때도 같은 키가 나와야 캐시가 맞는다. */
    @Test
    fun replayingTheSameMovesReproducesTheGoldenFingerprint() {
        val replayed = GameStateReplayer.replay(
            boardSize = BoardSize.Thirteen,
            ruleset = Ruleset.Japanese,
            moves = GoldenMoves,
            handicapCount = 2,
        )

        assertEquals(
            GoldenFingerprint,
            replayed.analysisFingerprint(),
        )
    }

    /** 빈 판 — 패·돌·수순이 없을 때의 자리표시(`none`, 빈 목록)도 키의 일부다. */
    @Test
    fun emptyBoardHasAGoldenFingerprint() {
        assertEquals(
            "size=9|rules=Chinese|next=Black|capturedB=0|capturedW=0|ko=none|koFor=none|stones=|moves=",
            GameState.empty(BoardSize.Nine, Ruleset.Chinese).analysisFingerprint(),
        )
    }

    /** 기권도 수순에 `describe` 그대로 들어간다. */
    @Test
    fun resignIsDescribedInTheFingerprint() {
        val state = GameState.empty(BoardSize.Nineteen, Ruleset.Japanese)
            .play(Move.Play(StoneColor.Black, BoardCoordinate(3, 15)))
            .play(Move.Resign(StoneColor.White))

        assertEquals(
            "size=19|rules=Japanese|next=White|capturedB=0|capturedW=0|ko=none|koFor=none" +
                "|stones=Q16:B,|moves=Black Q16;White resign;",
            state.analysisFingerprint(),
        )
    }

    private companion object {
        const val GoldenFingerprint =
            "size=13|rules=Japanese|next=White|capturedB=2|capturedW=1|ko=G7|koFor=White" +
                "|stones=B13:B,A12:B,K10:B,G8:B,H8:W,F7:B,H7:B,J7:W,G6:B,H6:W,D4:B,N2:W,M1:W," +
                "|moves=White A13;Black B13;White M1;Black A12;White pass;Black N1;White N2;Black G8;" +
                "White H8;Black F7;White G7;Black G6;White J7;Black pass;White H6;Black H7;"

        private val Black = StoneColor.Black
        private val White = StoneColor.White

        /** 흑 접바둑 돌은 K10(3,9)·D4(9,3). 백이 먼저 둔다. */
        val GoldenMoves: List<Move> = listOf(
            Move.Play(White, BoardCoordinate(0, 0)), // A13
            Move.Play(Black, BoardCoordinate(0, 1)), // B13
            Move.Play(White, BoardCoordinate(12, 11)), // M1
            Move.Play(Black, BoardCoordinate(1, 0)), // A12 — A13 백 한 점 따냄(패 아님: 활로 셋)
            Move.Pass(White),
            Move.Play(Black, BoardCoordinate(12, 12)), // N1 — 단수로 들어간다
            Move.Play(White, BoardCoordinate(11, 12)), // N2 — N1 흑 한 점 따냄(패 아님)
            Move.Play(Black, BoardCoordinate(5, 6)), // G8
            Move.Play(White, BoardCoordinate(5, 7)), // H8
            Move.Play(Black, BoardCoordinate(6, 5)), // F7
            Move.Play(White, BoardCoordinate(6, 6)), // G7
            Move.Play(Black, BoardCoordinate(7, 6)), // G6
            Move.Play(White, BoardCoordinate(6, 8)), // J7
            Move.Pass(Black),
            Move.Play(White, BoardCoordinate(7, 7)), // H6
            Move.Play(Black, BoardCoordinate(6, 7)), // H7 — G7 백 한 점 따냄, G7에 패
        )
    }
}
