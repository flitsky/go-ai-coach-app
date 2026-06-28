package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoBoardCoordinateTest {
    @Test
    fun tapCoordinateUsesCenteredGridWhenCoordinatesAreHidden() {
        assertEquals(
            BoardCoordinate(row = 4, column = 4),
            boardCoordinateFromTap(
                tapX = 450f,
                tapY = 450f,
                canvasWidth = 900f,
                canvasHeight = 900f,
                boardSize = BoardSize.Nine,
                showCoordinates = false,
            ),
        )
    }

    @Test
    fun tapCoordinateAccountsForCoordinateLabelPadding() {
        assertEquals(
            BoardCoordinate(row = 2, column = 6),
            boardCoordinateFromTap(
                tapX = 680f,
                tapY = 270f,
                canvasWidth = 1000f,
                canvasHeight = 900f,
                boardSize = BoardSize.Nine,
                showCoordinates = true,
            ),
        )
    }

    @Test
    fun tapCoordinateRejectsTapsOutsideSnapTolerance() {
        assertNull(
            boardCoordinateFromTap(
                tapX = 500f,
                tapY = 450f,
                canvasWidth = 900f,
                canvasHeight = 900f,
                boardSize = BoardSize.Nine,
                showCoordinates = false,
            ),
        )
    }

    @Test
    fun tapCoordinateAcceptsTapsAtSnapToleranceEdge() {
        assertEquals(
            BoardCoordinate(row = 4, column = 4),
            boardCoordinateFromTap(
                tapX = 495f,
                tapY = 450f,
                canvasWidth = 900f,
                canvasHeight = 900f,
                boardSize = BoardSize.Nine,
                showCoordinates = false,
            ),
        )
    }

    @Test
    fun tapCoordinateRejectsZeroSizedCanvas() {
        assertNull(
            boardCoordinateFromTap(
                tapX = 0f,
                tapY = 0f,
                canvasWidth = 0f,
                canvasHeight = 900f,
                boardSize = BoardSize.Nine,
                showCoordinates = false,
            ),
        )
    }
}
