package com.worksoc.goaicoach.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LegalMoveGeneratorTest {
    @Test
    fun emptyNineByNineHasEveryIntersectionAvailable() {
        val state = GameState.empty(BoardSize.Nine, Ruleset.Chinese)

        assertEquals(81, LegalMoveGenerator.legalPlayCount(state))
    }

    @Test
    fun occupiedAndSuicidePointsAreExcluded() {
        val state = GameState.empty().copy(
            nextPlayer = StoneColor.White,
            stones = mapOf(
                point("D5") to StoneColor.Black,
                point("F5") to StoneColor.Black,
                point("E4") to StoneColor.Black,
                point("E6") to StoneColor.Black,
            ),
        )

        val legalMoves = LegalMoveGenerator.legalPlayCoordinates(state)

        assertFalse(point("D5") in legalMoves)
        assertFalse(point("E5") in legalMoves)
    }

    @Test
    fun koRecapturePointIsExcluded() {
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
        ).play(Move.Play(StoneColor.Black, point("E5")))

        assertFalse(point("D5") in LegalMoveGenerator.legalPlayCoordinates(afterCapture))
    }

    private fun point(label: String): BoardCoordinate =
        BoardCoordinate.fromLabel(label, BoardSize.Nine)
}
