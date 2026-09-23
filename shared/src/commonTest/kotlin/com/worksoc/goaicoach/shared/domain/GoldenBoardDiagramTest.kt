package com.worksoc.goaicoach.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **다이어그램 파서를 먼저 고정한다.**
 *
 * 아래 골든 테스트들은 전부 `goldenBoard(...)`가 그린 판 위에서 기대값을 단언한다.
 * 파서가 위아래를 뒤집거나 열을 밀면 **기대값 전부가 조용히 다른 국면의 답**이 되고,
 * 그런 테스트는 초록이어도 아무것도 지키지 않는다(함정 24).
 *
 * 그래서 여기서는 파서의 방향을 `BoardCoordinate.label`과 대조하고, 잘못된 다이어그램이
 * **반드시 예외로 죽는지**를 본다.
 */
class GoldenBoardDiagramTest {

    @Test
    fun topLeftCornerOfTheDiagramIsTheTopLeftCornerOfTheBoard() {
        val board = goldenBoard(
            "X . . . . . . . O",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . X . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            "O . . . . . . . X",
        )

        assertEquals(BoardSize.Nine, board.boardSize)
        assertEquals(StoneColor.Black, board.stones[goldenPoint("A9")])
        assertEquals(StoneColor.White, board.stones[goldenPoint("J9")])
        assertEquals(StoneColor.Black, board.stones[goldenPoint("E5")])
        assertEquals(StoneColor.White, board.stones[goldenPoint("A1")])
        assertEquals(StoneColor.Black, board.stones[goldenPoint("J1")])
        assertEquals(5, board.stones.size)
    }

    @Test
    fun emptyPointsAreAbsentRatherThanPresentWithNoColor() {
        val board = goldenBoard(
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . X . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
            ". . . . . . . . .",
        )

        assertEquals(1, board.stones.size)
        assertTrue(goldenPoint("E6") !in board.stones)
        assertEquals(null, board.toState().stoneAt(goldenPoint("E6")))
    }

    @Test
    fun aRowWithTheWrongNumberOfPointsIsRejected() {
        val failure = assertFailsWith<IllegalArgumentException> {
            goldenBoard(
                ". . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
            )
        }
        assertTrue(failure.message.orEmpty().contains("9선"), failure.message.orEmpty())
    }

    @Test
    fun anUnknownSymbolIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            goldenBoard(
                "B . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
                ". . . . . . . . .",
            )
        }
    }

    @Test
    fun anUnsupportedRowCountIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            goldenBoard(". . .", ". . .", ". . .")
        }
    }
}
