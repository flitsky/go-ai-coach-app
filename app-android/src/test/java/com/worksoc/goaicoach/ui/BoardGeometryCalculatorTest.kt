package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardGeometryCalculatorTest {

    @Test
    fun testStarPointsForVariousBoardSizes() {
        val starPoints9 = starPoints(BoardSize(9))
        assertEquals(5, starPoints9.size)
        assertTrue(starPoints9.contains(BoardCoordinate(4, 4))) // 천원(중앙)

        val starPoints13 = starPoints(BoardSize(13))
        assertEquals(5, starPoints13.size)
        assertTrue(starPoints13.contains(BoardCoordinate(6, 6)))

        val starPoints19 = starPoints(BoardSize(19))
        assertEquals(9, starPoints19.size)
        assertTrue(starPoints19.contains(BoardCoordinate(9, 9)))
        assertTrue(starPoints19.contains(BoardCoordinate(3, 3))) // 좌상귀 화점
        assertTrue(starPoints19.contains(BoardCoordinate(15, 15))) // 우하귀 화점
    }

    @Test
    fun testBoardColumnLabels() {
        val labels9 = boardColumnLabels(BoardSize(9))
        assertEquals(9, labels9.size)
        // 바둑 좌표계는 I(아이)를 생략하므로 J가 9번째가 됨
        // ABCDEFGHJ
        assertEquals('A', labels9[0])
        assertEquals('H', labels9[7])
        assertEquals('J', labels9[8])

        val labels19 = boardColumnLabels(BoardSize(19))
        assertEquals(19, labels19.size)
        assertEquals('T', labels19[18]) // ABCDEFGHJKLMNOPQRST
    }

    @Test
    fun testBoardTapGeometryCalculations() {
        val geometryWithCoords = boardTapGeometry(
            canvasWidth = 1000f,
            canvasHeight = 1000f,
            boardSize = BoardSize(19),
            showCoordinates = true
        )
        assertNotNull(geometryWithCoords)
        // 1000f 해상도에 19줄 + 좌표 표시 영역 공간 계산 검증
        val g = geometryWithCoords!!
        assertEquals(1000f / 20f, g.spacing) // 1000 / (19 + 1)
        assertEquals(g.spacing, g.boardPadding)

        val geometryNoCoords = boardTapGeometry(
            canvasWidth = 1000f,
            canvasHeight = 1000f,
            boardSize = BoardSize(19),
            showCoordinates = false
        )
        assertNotNull(geometryNoCoords)
        val gNo = geometryNoCoords!!
        assertEquals(1000f / 19f, gNo.spacing)
        assertEquals(gNo.spacing * 0.5f, gNo.boardPadding)
    }

    @Test
    fun testCoordinateFromTapSnapping() {
        val width = 1000f
        val height = 1000f
        val boardSize = BoardSize(19)

        // 가상의 정가운데 터치 테스트 (좌표 오프셋이 정확히 천원 9, 9 에 부합하는지)
        val geometry = boardTapGeometry(width, height, boardSize, showCoordinates = true)!!
        val centerColumn = 9
        val centerRow = 9
        val targetX = geometry.originX + centerColumn * geometry.spacing
        val targetY = geometry.originY + centerRow * geometry.spacing

        // 정확한 포인트 터치
        val snappedExact = boardCoordinateFromTap(targetX, targetY, width, height, boardSize, showCoordinates = true)
        assertNotNull(snappedExact)
        assertEquals(centerRow, snappedExact!!.row)
        assertEquals(centerColumn, snappedExact.column)

        // 오차 범위(tolerance = spacing * 0.45) 내 터치
        val toleranceOffset = geometry.spacing * 0.3f
        val snappedWithinTolerance = boardCoordinateFromTap(
            targetX + toleranceOffset,
            targetY - toleranceOffset,
            width,
            height,
            boardSize,
            showCoordinates = true
        )
        assertNotNull(snappedWithinTolerance)
        assertEquals(centerRow, snappedWithinTolerance!!.row)
        assertEquals(centerColumn, snappedWithinTolerance.column)

        // 오차 범위 밖의 터치 (스냅 실패하여 null 반환해야 함)
        val outOfToleranceOffset = geometry.spacing * 0.5f
        val snappedOutOfTolerance = boardCoordinateFromTap(
            targetX + outOfToleranceOffset,
            targetY,
            width,
            height,
            boardSize,
            showCoordinates = true
        )
        assertNull(snappedOutOfTolerance)
    }
}
