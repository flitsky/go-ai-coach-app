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
}
