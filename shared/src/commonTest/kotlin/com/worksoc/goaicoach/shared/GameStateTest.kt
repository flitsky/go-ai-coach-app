package com.worksoc.goaicoach.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GameStateTest {
    @Test
    fun playMoveAddsStoneAndAlternatesTurn() {
        val coordinate = BoardCoordinate(row = 4, column = 4)

        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, coordinate))

        assertEquals(StoneColor.Black, state.stoneAt(coordinate))
        assertEquals(StoneColor.White, state.nextPlayer)
    }

    @Test
    fun occupiedPointIsRejected() {
        val coordinate = BoardCoordinate(row = 4, column = 4)
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, coordinate))

        assertFailsWith<IllegalArgumentException> {
            state.play(Move.Play(StoneColor.White, coordinate))
        }
    }

    @Test
    fun passAlternatesTurnWithoutAddingStone() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))

        assertEquals(StoneColor.White, state.nextPlayer)
        assertEquals(0, state.stones.size)
        assertEquals(1, state.moves.size)
    }

    @Test
    fun fullBoardIsDetected() {
        val boardSize = BoardSize.Nine
        val stones = buildMap {
            for (row in 0 until boardSize.value) {
                for (column in 0 until boardSize.value) {
                    put(BoardCoordinate(row, column), StoneColor.Black)
                }
            }
        }

        assertTrue(
            GameState.empty(boardSize = boardSize)
                .copy(stones = stones)
                .isBoardFull(),
        )
        assertFalse(GameState.empty(boardSize = boardSize).isBoardFull())
    }

    @Test
    fun coordinateLabelRoundTrips() {
        val boardSize = BoardSize.Nine
        val coordinate = BoardCoordinate(row = 4, column = 4)

        assertEquals("E5", coordinate.label(boardSize))
        assertEquals(coordinate, BoardCoordinate.fromLabel("E5", boardSize))
    }

    @Test
    fun surroundedSingleStoneIsCaptured() {
        val state = GameState.empty()
            .play(play(StoneColor.Black, "D5"))
            .play(play(StoneColor.White, "E5"))
            .play(play(StoneColor.Black, "E4"))
            .play(play(StoneColor.White, "A9"))
            .play(play(StoneColor.Black, "F5"))
            .play(play(StoneColor.White, "A8"))
            .play(play(StoneColor.Black, "E6"))

        assertEquals(null, state.stoneAt(point("E5")))
        assertEquals(StoneColor.Black, state.stoneAt(point("E6")))
        assertEquals(1, state.capturedBy(StoneColor.Black))
    }

    @Test
    fun surroundedGroupIsCaptured() {
        val state = GameState.empty().copy(
            nextPlayer = StoneColor.Black,
            stones = mapOf(
                point("E5") to StoneColor.White,
                point("F5") to StoneColor.White,
                point("D5") to StoneColor.Black,
                point("E4") to StoneColor.Black,
                point("F4") to StoneColor.Black,
                point("G5") to StoneColor.Black,
                point("E6") to StoneColor.Black,
            ),
        ).play(play(StoneColor.Black, "F6"))

        assertEquals(null, state.stoneAt(point("E5")))
        assertEquals(null, state.stoneAt(point("F5")))
        assertEquals(2, state.capturedBy(StoneColor.Black))
    }

    @Test
    fun suicideMoveIsRejected() {
        val state = GameState.empty().copy(
            nextPlayer = StoneColor.White,
            stones = mapOf(
                point("D5") to StoneColor.Black,
                point("F5") to StoneColor.Black,
                point("E4") to StoneColor.Black,
                point("E6") to StoneColor.Black,
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            state.play(play(StoneColor.White, "E5"))
        }
    }

    @Test
    fun captureMoveIsNotRejectedAsSuicide() {
        val state = GameState.empty().copy(
            nextPlayer = StoneColor.Black,
            stones = mapOf(
                point("E5") to StoneColor.White,
                point("D5") to StoneColor.Black,
                point("F5") to StoneColor.Black,
                point("E4") to StoneColor.Black,
            ),
        ).play(play(StoneColor.Black, "E6"))

        assertEquals(null, state.stoneAt(point("E5")))
        assertEquals(StoneColor.Black, state.stoneAt(point("E6")))
    }

    @Test
    fun immediateKoRecaptureIsRejected() {
        val afterCapture = GameState.empty().copy(
            nextPlayer = StoneColor.Black,
            stones = mapOf(
                point("D5") to StoneColor.White,
                point("E4") to StoneColor.White,
                point("E6") to StoneColor.White,
                point("F5") to StoneColor.White,
                point("C5") to StoneColor.Black,
                point("D4") to StoneColor.Black,
                point("D6") to StoneColor.Black,
            ),
        ).play(play(StoneColor.Black, "E5"))

        assertEquals(point("D5"), afterCapture.koPoint)
        assertEquals(StoneColor.White, afterCapture.koForbiddenFor)
        assertFailsWith<IllegalArgumentException> {
            afterCapture.play(play(StoneColor.White, "D5"))
        }
    }

    @Test
    fun replayWithoutLastMoveRestoresCapturedStone() {
        val beforeCapture = GameState.empty()
            .play(play(StoneColor.Black, "D5"))
            .play(play(StoneColor.White, "E5"))
            .play(play(StoneColor.Black, "E4"))
            .play(play(StoneColor.White, "A9"))
            .play(play(StoneColor.Black, "F5"))
            .play(play(StoneColor.White, "A8"))
        val afterCapture = beforeCapture.play(play(StoneColor.Black, "E6"))

        val undone = afterCapture.replayWithoutLastMoves(1)

        assertEquals(beforeCapture, undone)
        assertEquals(StoneColor.White, undone.stoneAt(point("E5")))
        assertEquals(0, undone.capturedBy(StoneColor.Black))
        assertEquals(StoneColor.Black, undone.nextPlayer)
    }

    @Test
    fun replayWithoutLastTwoMovesRestoresPreviousTurnPair() {
        val state = GameState.empty()
            .play(play(StoneColor.Black, "E5"))
            .play(play(StoneColor.White, "C5"))
            .play(play(StoneColor.Black, "D5"))
            .play(play(StoneColor.White, "C6"))

        val undone = state.replayWithoutLastMoves(2)

        assertEquals(2, undone.moves.size)
        assertEquals(StoneColor.Black, undone.stoneAt(point("E5")))
        assertEquals(StoneColor.White, undone.stoneAt(point("C5")))
        assertEquals(StoneColor.Black, undone.nextPlayer)
    }

    @Test
    fun replayWithoutMoreMovesThanHistoryResetsBoard() {
        val state = GameState.empty()
            .play(play(StoneColor.Black, "E5"))
            .play(play(StoneColor.White, "C5"))

        val undone = state.replayWithoutLastMoves(99)

        assertEquals(GameState.empty(), undone)
    }

    private fun play(
        player: StoneColor,
        label: String,
    ): Move.Play = Move.Play(player, point(label))

    private fun point(label: String): BoardCoordinate =
        BoardCoordinate.fromLabel(label, BoardSize.Nine)
}
