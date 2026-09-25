package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.movereview.MoveReviewTone
import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameStateReplayer
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **대국 기록(리플레이) 좌표 골든 — 번들 기보와 리터럴 기록을 정수 좌표로 고정한다**(refactor backlog #36).
 *
 * ## 번들 기보
 * `assets/reference_games/handicap2_top_tier_ai.json`은 13x13 접바둑 2점 대국 137수(착수 135, 통과 2)이고
 * 열 `A`~`N`을 **전부** 쓴다 — `I`를 건너뛰는 규칙에 기대고 있다는 뜻이다. 알파벳에 `I`를 넣으면
 * `N`이 판 밖이 돼 [GameReplayCodec.decode]가 `null`을 돌려주고, 대국 기록 화면 맨 위의 예시 판이
 * **아무 오류 없이** 사라진다. 지금까지 이 에셋을 읽는 테스트는 하나도 없었다.
 *
 * ## 리터럴 기록
 * `GameHistoryCodecTest`는 왕복만 본다. 여기서는 `moves[].coordinate`와 `moveEvaluations[].coordinate`를
 * 리터럴 JSON과 정수 좌표 사이에서 양방향으로 고정한다. 입력은 정수 생성자로 만든다.
 */
class GameReplayCoordinateGoldenTest {

    @Test
    fun bundledReferenceGameDecodesAndReplaysLegallyToTheEnd() {
        val entry = referenceGameHistoryEntry()
        val boardSize = BoardSize(entry.boardSize)
        val raw = RepoPaths.appAndroidMain(ReferenceGameAsset).readContractSource()

        val replay = GameReplayCodec.decode(raw, boardSize)

        assertNotNull("번들 기보가 디코드되지 않는다 — 대국 기록 맨 위 예시 판이 조용히 사라진다", replay)
        val moves = replay!!.moves
        assertEquals(BoardSize.Thirteen, boardSize)
        assertEquals(137, moves.size)
        assertEquals(entry.moveCount, moves.size)
        val plays = moves.filterIsInstance<Move.Play>()
        assertEquals(135, plays.size)
        assertEquals(
            listOf(
                Move.Play(StoneColor.White, BoardCoordinate(10, 2)), // C3
                Move.Play(StoneColor.Black, BoardCoordinate(9, 2)), // C4
                Move.Play(StoneColor.White, BoardCoordinate(3, 2)), // C10
            ),
            moves.take(3),
        )
        assertEquals(listOf(Move.Pass(StoneColor.Black), Move.Pass(StoneColor.White)), moves.takeLast(2))
        assertEquals("13개 열(A~N, I 없음)을 전부 쓴다", (0..12).toSet(), plays.map { it.coordinate.column }.toSet())
        assertEquals("13개 행을 전부 쓴다", (0..12).toSet(), plays.map { it.coordinate.row }.toSet())

        // 접바둑 2점·덤 그대로 처음부터 끝까지 **합법으로** 다시 둘 수 있어야 한다 — 한 점이라도 다르게
        // 읽히면 착점이 겹치거나 차례가 어긋나 BoardRules가 예외를 던진다.
        val finalState = GameStateReplayer.replay(
            boardSize = boardSize,
            ruleset = entry.ruleset,
            moves = moves,
            handicapCount = entry.handicapCount,
            komi = entry.komi,
        )
        assertEquals(137, finalState.moves.size)
        assertTrue(finalState.hasConsecutivePasses())
        assertEquals(
            listOf(FinalBlackStones, FinalWhiteStones, FinalCapturedByBlack, FinalCapturedByWhite),
            listOf(
                finalState.stones.count { it.value == StoneColor.Black },
                finalState.stones.count { it.value == StoneColor.White },
                finalState.capturedByBlack,
                finalState.capturedByWhite,
            ),
        )
    }

    @Test
    fun literalReplayRecordDecodesToIntegerCoordinates() {
        val replay = GameReplayCodec.decode(ReplayRecord9x9, BoardSize.Nine)

        assertNotNull("리터럴 대국 기록이 디코드되지 않는다", replay)
        assertEquals(GoldenMoves, replay!!.moves)
        assertEquals(GoldenEvaluations, replay.moveEvaluations)
    }

    @Test
    fun integerBuiltReplayRecordEncodesGoldenCoordinateStrings() {
        val json = JSONObject(
            GameReplayCodec.encode(
                GameReplayData(moves = GoldenMoves, moveEvaluations = GoldenEvaluations),
                BoardSize.Nine,
            ),
        )

        assertEquals(1, json.getInt("schema"))
        val moves = json.getJSONArray("moves")
        assertEquals(
            listOf("J9", "A1", "<없음>"),
            List(moves.length()) { index -> moves.getJSONObject(index).optString("coordinate", "<없음>") },
        )
        val evaluations = json.getJSONArray("moveEvaluations")
        assertEquals(
            listOf("J9", "A1"),
            List(evaluations.length()) { index -> evaluations.getJSONObject(index).getString("coordinate") },
        )
    }

    private companion object {
        const val ReferenceGameAsset = "assets/reference_games/handicap2_top_tier_ai.json"

        // 번들 기보를 끝까지 둔 결과(2026-09-25 실측). 서로 맞물린다 — 백 착수 68 - 흑이 딴 3 = 65,
        // 흑 접바둑 2 + 착수 67 - 백이 딴 9 = 60. 에셋이나 착수 규칙이 바뀌면 달라진다.
        const val FinalBlackStones = 60
        const val FinalWhiteStones = 65
        const val FinalCapturedByBlack = 3
        const val FinalCapturedByWhite = 9

        /** J9=(0,8)은 `I`를 건너뛴 열, A1=(8,0)은 행 원점의 반대쪽 끝이다. */
        val GoldenMoves: List<Move> = listOf(
            Move.Play(StoneColor.Black, BoardCoordinate(0, 8)),
            Move.Play(StoneColor.White, BoardCoordinate(8, 0)),
            Move.Resign(StoneColor.Black),
        )

        val GoldenEvaluations: List<MoveReviewMarker> = listOf(
            MoveReviewMarker(BoardCoordinate(0, 8), moveNumber = 1, tone = MoveReviewTone.Good, pointLoss = 0.5),
            MoveReviewMarker(BoardCoordinate(8, 0), moveNumber = 2, tone = MoveReviewTone.Blunder, pointLoss = 12.0),
        )

        /** `GameReplayCodec.encode`가 쓰는 모양 그대로의 9x9 대국 기록. */
        val ReplayRecord9x9 = """
            {
              "schema": 1,
              "moves": [
                {"type": "play", "player": "Black", "coordinate": "J9"},
                {"type": "play", "player": "White", "coordinate": "A1"},
                {"type": "resign", "player": "Black"}
              ],
              "scoreSnapshots": [],
              "moveEvaluations": [
                {"moveNumber": 1, "coordinate": "J9", "tone": "Good", "pointLoss": 0.5},
                {"moveNumber": 2, "coordinate": "A1", "tone": "Blunder", "pointLoss": 12.0}
              ]
            }
        """.trimIndent()
    }
}
