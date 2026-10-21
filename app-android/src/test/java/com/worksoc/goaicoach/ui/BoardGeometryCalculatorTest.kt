package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.ui.board.boardColumnLabels
import com.worksoc.goaicoach.ui.board.boardCoordinateFromTap
import com.worksoc.goaicoach.ui.board.boardTapGeometry
import com.worksoc.goaicoach.ui.board.starPoints
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

    /** 판에 그리는 열 이름 전부 — 세 판 크기 모두 `I`를 건너뛴다(refactor backlog #36). */
    @Test
    fun boardColumnLabelsSpellEveryColumnOfEverySupportedBoard() {
        assertEquals("ABCDEFGHJ".toList(), boardColumnLabels(BoardSize(9)))
        assertEquals("ABCDEFGHJKLMN".toList(), boardColumnLabels(BoardSize(13)))
        assertEquals("ABCDEFGHJKLMNOPQRST".toList(), boardColumnLabels(BoardSize(19)))
    }

    /**
     * 귀만 잘라 보인 판(백로그 #277 — 19줄 판 고전 사활): 보이는 줄 수로 칸을 나눠 크게 그리고, 보이는 칸 밖은 누를 수 없다.
     * 위 · 왼쪽 가장자리의 여백은 판 전체와 같은 반 칸이다.
     */
    @Test
    fun aCornerWindowSpacesTheVisibleLinesAndRefusesTheRest() {
        val geometry = boardTapGeometry(canvasWidth = 1000f, canvasHeight = 1000f, boardSize = BoardSize(19), showCoordinates = false, visibleLines = 10)
        assertNotNull(geometry)
        assertEquals(100f, geometry!!.spacing, 0.001f)
        assertEquals(50f, geometry.originX, 0.001f)
        val tap = { x: Float, y: Float ->
            boardCoordinateFromTap(x, y, 1000f, 1000f, BoardSize(19), showCoordinates = false, visibleLines = 10)
        }
        assertEquals(BoardCoordinate(0, 0), tap(50f, 50f))
        assertEquals(BoardCoordinate(9, 9), tap(950f, 950f))
        // 열한째 줄(보이지 않는 칸)은 캔버스 밖이라 누를 수 없다 — 잘린 쪽 반 칸 띠는 마지막 줄로 붙는다.
        assertEquals(BoardCoordinate(9, 9), tap(999f, 999f))
        // 가로가 긴 캔버스에서 열한째 줄 자리(가장 가까운 교점이 10열)를 누르면 받지 않는다.
        assertNull(boardCoordinateFromTap(1095f, 50f, 1100f, 1000f, BoardSize(19), showCoordinates = false, visibleLines = 10))
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
        // ⚠️ **정확히 반 칸 떨어진 자리는 이제 받아들인다**(U-5, 백로그 #152) — 옛 허용오차
        // 0.45에서는 여기가 `null`이었고, 그 0.05칸이 실기에서 죽은 띠로 드러났다.
        // `roundToInt`가 반올림해 **이웃 쪽**을 집는다.
        val halfCell = geometry.spacing * 0.5f
        val snappedAtHalfCell = boardCoordinateFromTap(
            targetX + halfCell,
            targetY,
            width,
            height,
            boardSize,
            showCoordinates = true
        )
        assertNotNull("두 점 한가운데가 무시된다 — 죽은 띠가 돌아왔다(#152 회귀).", snappedAtHalfCell)
        assertEquals(centerColumn + 1, snappedAtHalfCell!!.column)

        // ⚠️ 반 칸을 넘기면 여전히 무시한다 — 반상 바깥을 눌러도 놓이면 취소 경로가 죽는다.
        val outsideBoard = boardCoordinateFromTap(
            geometry.originX - geometry.spacing * 0.6f,
            targetY,
            width,
            height,
            boardSize,
            showCoordinates = true
        )
        assertNull("반 칸 바깥까지 받아들인다 — 판 밖 탭이 가장자리에 놓인다.", outsideBoard)
    }
}
