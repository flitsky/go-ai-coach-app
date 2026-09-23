package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridStoneDetectorTest {

    @Test
    fun detectsBlackAndWhiteStonesOnSyntheticNineByNineBoard() {
        val size = 900
        val boardColor = 0xFFD2B48C.toInt() // Tan/Wood color
        val gridLineColor = 0xFF332211.toInt()
        val blackStoneColor = 0xFF151515.toInt()
        val whiteStoneColor = 0xFFF0F0F0.toInt()

        val pixels = IntArray(size * size) { boardColor }

        val n = 9
        val step = size.toFloat() / (n - 1)
        val stoneRadius = step * 0.35f

        fun drawCircle(col: Int, row: Int, color: Int) {
            val cx = col * step
            val cy = row * step
            val rInt = stoneRadius.toInt()
            for (dy in -rInt..rInt) {
                for (dx in -rInt..rInt) {
                    if (dx * dx + dy * dy <= stoneRadius * stoneRadius) {
                        val px = (cx + dx).toInt().coerceIn(0, size - 1)
                        val py = (cy + dy).toInt().coerceIn(0, size - 1)
                        pixels[py * size + px] = color
                    }
                }
            }
        }

        // 흑돌: 천원 (E5 = row 4, col 4), 좌상귀 (B8 = row 1, col 1)
        // 백돌: 우하귀 (G3 = row 6, col 6), 좌하귀 (B2 = row 7, col 1)
        drawCircle(col = 4, row = 4, color = blackStoneColor)
        drawCircle(col = 1, row = 1, color = blackStoneColor)
        drawCircle(col = 6, row = 6, color = whiteStoneColor)
        drawCircle(col = 1, row = 7, color = whiteStoneColor)

        val source = ArrayPixelSource(size, size, pixels)
        val detected = GridStoneDetector.detect(source, BoardSize.Nine)

        assertEquals(BoardSize.Nine, detected.boardSize)
        assertEquals(4, detected.stones.size)

        assertEquals(StoneColor.Black, detected.stones[BoardCoordinate(4, 4)])
        assertEquals(StoneColor.Black, detected.stones[BoardCoordinate(1, 1)])
        assertEquals(StoneColor.White, detected.stones[BoardCoordinate(6, 6)])
        assertEquals(StoneColor.White, detected.stones[BoardCoordinate(7, 1)])
    }

    @Test
    fun detectsEmptyBoardWhenNoStonesPresent() {
        val size = 500
        val boardColor = 0xFFD2A060.toInt()
        val pixels = IntArray(size * size) { boardColor }
        val source = ArrayPixelSource(size, size, pixels)

        val detected = GridStoneDetector.detect(source, BoardSize.Thirteen)

        assertEquals(BoardSize.Thirteen, detected.boardSize)
        assertTrue(detected.stones.isEmpty())
    }
}
