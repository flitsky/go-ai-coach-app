package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **GTP 수 토큰 골든 — 엔진과 주고받는 `D4`·`pass`·`resign`을 글자 그대로 고정한다**(refactor backlog #36).
 *
 * KataGo에게 보내는 `play`/`set_free_handicap`과 KataGo가 돌려주는 `genmove`·분석 응답은 모두
 * [BoardCoordinate.label]/[BoardCoordinate.fromLabel] 표기를 쓴다. 두 함수가 **함께** 바뀌면
 * (알파벳에 `I`를 넣기, 행 원점 뒤집기) 앱 안의 왕복은 맞아도 **엔진은 다른 점을 두고 다른 점을
 * 분석한다.** 그래서 토큰을 문자열 리터럴로, 수를 정수 좌표로 두고 양방향을 고정한다.
 *
 * [toGtpMoveOrNull]은 [toGtpVertex]의 역함수다 — 611점 전부와 통과·기권을 두 색으로 왕복한다.
 */
class GtpMoveTokenGoldenTest {

    @Test
    fun tokenTableReadsToGoldenMoves() {
        val failures = ReadTable.mapNotNull { (boardSize, token, expected) ->
            val read = token.toGtpMoveOrNull(StoneColor.White, boardSize)
            if (read == expected) null else "${boardSize.value}x${boardSize.value} \"$token\" → $read (기대 $expected)"
        }

        assertEquals(emptyList<String>(), failures, "GTP 토큰 읽기 골든 표가 어긋났다:\n" + failures.joinToString("\n"))
    }

    @Test
    fun movesWriteGoldenTokens() {
        val nineteen = BoardSize.Nineteen
        assertEquals("Q16", Move.Play(StoneColor.Black, BoardCoordinate(3, 15)).toGtpVertex(nineteen))
        assertEquals("D4", Move.Play(StoneColor.Black, BoardCoordinate(15, 3)).toGtpVertex(nineteen))
        assertEquals("J10", Move.Play(StoneColor.Black, BoardCoordinate(9, 8)).toGtpVertex(nineteen))
        assertEquals("T1", Move.Play(StoneColor.Black, BoardCoordinate(18, 18)).toGtpVertex(nineteen))
        assertEquals("A19", Move.Play(StoneColor.Black, BoardCoordinate(0, 0)).toGtpVertex(nineteen))
        assertEquals("pass", Move.Pass(StoneColor.Black).toGtpVertex(nineteen))
        assertEquals("resign", Move.Resign(StoneColor.White).toGtpVertex(nineteen))

        assertEquals("play B Q16", KataGoProtocolCommands.play(Move.Play(StoneColor.Black, BoardCoordinate(3, 15)), nineteen))
        assertEquals("play W J9", KataGoProtocolCommands.play(Move.Play(StoneColor.White, BoardCoordinate(0, 8)), BoardSize.Nine))
        // 13x13 접바둑 2점 화점(우상귀 K10, 좌하귀 D4)
        assertEquals(
            "set_free_handicap K10 D4",
            KataGoProtocolCommands.setFreeHandicap(
                listOf(BoardCoordinate(3, 9), BoardCoordinate(9, 3)),
                BoardSize.Thirteen,
            ),
        )
    }

    /** 611점 전부와 통과·기권을 두 색으로 — `toGtpVertex` → `toGtpMoveOrNull`이 원래 수로 돌아온다. */
    @Test
    fun readingInvertsWritingOnEveryPointOfEverySupportedBoard() {
        var checked = 0
        val failures = mutableListOf<String>()
        for (boardSize in BoardSize.supported()) {
            for (player in StoneColor.entries) {
                val moves = buildList {
                    for (row in 0 until boardSize.value) {
                        for (column in 0 until boardSize.value) {
                            add(Move.Play(player, BoardCoordinate(row, column)))
                        }
                    }
                    add(Move.Pass(player))
                    add(Move.Resign(player))
                }
                for (move in moves) {
                    val token = move.toGtpVertex(boardSize)
                    val read = token.toGtpMoveOrNull(player, boardSize)
                    if (read != move) failures += "${boardSize.value}x${boardSize.value} $move → \"$token\" → $read"
                    if (move is Move.Play) checked++
                }
            }
        }

        assertEquals(emptyList<String>(), failures, "GTP 토큰 왕복이 어긋났다:\n" + failures.take(20).joinToString("\n"))
        assertEquals(2 * (81 + 169 + 361), checked)
    }

    private data class ReadCase(
        val boardSize: BoardSize,
        val token: String,
        val expected: Move?,
    )

    private companion object {
        private val White = StoneColor.White

        val ReadTable = listOf(
            // 통과·기권 — 대소문자를 가리지 않는다(분석 파서의 현행 규칙)
            ReadCase(BoardSize.Nineteen, "pass", Move.Pass(White)),
            ReadCase(BoardSize.Nineteen, "PASS", Move.Pass(White)),
            ReadCase(BoardSize.Nineteen, "Pass", Move.Pass(White)),
            ReadCase(BoardSize.Nineteen, "resign", Move.Resign(White)),
            ReadCase(BoardSize.Nineteen, "RESIGN", Move.Resign(White)),
            // 좌표 — 정수 좌표가 기대값
            ReadCase(BoardSize.Nineteen, "Q16", Move.Play(White, BoardCoordinate(3, 15))),
            ReadCase(BoardSize.Nineteen, "D4", Move.Play(White, BoardCoordinate(15, 3))),
            ReadCase(BoardSize.Nineteen, "J10", Move.Play(White, BoardCoordinate(9, 8))),
            ReadCase(BoardSize.Nineteen, "T19", Move.Play(White, BoardCoordinate(0, 18))),
            ReadCase(BoardSize.Nineteen, "A1", Move.Play(White, BoardCoordinate(18, 0))),
            ReadCase(BoardSize.Nineteen, "q16", Move.Play(White, BoardCoordinate(3, 15))), // fromLabel의 관대 파싱 그대로
            ReadCase(BoardSize.Thirteen, "N13", Move.Play(White, BoardCoordinate(0, 12))),
            ReadCase(BoardSize.Thirteen, "K10", Move.Play(White, BoardCoordinate(3, 9))),
            ReadCase(BoardSize.Nine, "E5", Move.Play(White, BoardCoordinate(4, 4))),
            ReadCase(BoardSize.Nine, "J9", Move.Play(White, BoardCoordinate(0, 8))),
            // 못 읽는 토큰 — 예외가 아니라 null
            ReadCase(BoardSize.Nine, "I5", null),
            ReadCase(BoardSize.Nine, "K1", null),
            ReadCase(BoardSize.Nine, "A10", null),
            ReadCase(BoardSize.Nineteen, "U1", null),
            ReadCase(BoardSize.Nineteen, "T20", null),
            ReadCase(BoardSize.Nineteen, "", null),
            ReadCase(BoardSize.Nineteen, "p", null),
            ReadCase(BoardSize.Nineteen, "passs", null),
            ReadCase(BoardSize.Nineteen, "resign ", null),
            ReadCase(BoardSize.Nineteen, " pass", null),
        )
    }
}
