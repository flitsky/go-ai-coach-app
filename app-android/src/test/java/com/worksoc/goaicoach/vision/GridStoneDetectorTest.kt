package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 합성 판으로 분류 규칙 셋을 하나씩 못박는다(백로그 #210). 실제 사진 인식률은 [BoardPhotoAccuracyTest]가 잰다.
 */
class GridStoneDetectorTest {

    private val step = BoardWarp.CellPx
    private val origin = (BoardWarp.MarginCells * step)

    /** 9줄 합성 판 — 바탕·격자선·돌을 그린다. */
    private class Canvas(val size: Int, background: Int) {
        val pixels = IntArray(size * size) { background }
        fun set(x: Int, y: Int, color: Int) {
            if (x in 0 until size && y in 0 until size) pixels[y * size + x] = color
        }
    }

    private fun board(
        background: Int,
        lineColor: Int,
        draw: Canvas.(center: (Int, Int) -> Pair<Int, Int>) -> Unit,
    ): RectifiedBoard {
        val n = 9
        val size = ((n - 1) * step + 2 * origin).toInt()
        val canvas = Canvas(size, background)
        val first = origin.toInt()
        val last = (origin + (n - 1) * step).toInt()
        for (k in 0 until n) {
            val p = (origin + k * step).toInt()
            for (t in first..last) {
                canvas.set(p, t, lineColor)
                canvas.set(t, p, lineColor)
            }
        }
        canvas.draw { col, row -> (origin + col * step).toInt() to (origin + row * step).toInt() }
        return RectifiedBoard(ArrayPixelSource(size, size, canvas.pixels), BoardSize.Nine, origin, step.toFloat())
    }

    private fun Canvas.disc(cx: Int, cy: Int, radius: Float, color: Int) {
        val r = radius.toInt()
        for (dy in -r..r) for (dx in -r..r) if (dx * dx + dy * dy <= radius * radius) set(cx + dx, cy + dy, color)
    }

    private fun Canvas.ring(cx: Int, cy: Int, radius: Float, color: Int) {
        val r = radius.toInt() + 1
        for (dy in -r..r) for (dx in -r..r) {
            val d2 = dx * dx + dy * dy
            if (d2 <= radius * radius && d2 >= (radius - 2) * (radius - 2)) set(cx + dx, cy + dy, color)
        }
    }

    @Test
    fun woodBoardWithFilledStones() {
        val wood = 0xFFD2A060.toInt()
        val detected = GridStoneDetector.detect(
            board(wood, 0xFF3A2A10.toInt()) { at ->
                at(4, 4).let { (x, y) -> disc(x, y, step * 0.47f, 0xFF151515.toInt()) }
                at(0, 0).let { (x, y) -> disc(x, y, step * 0.47f, 0xFF151515.toInt()) }
                at(6, 2).let { (x, y) -> disc(x, y, step * 0.47f, 0xFFF0F0F0.toInt()) }
                at(8, 7).let { (x, y) -> disc(x, y, step * 0.47f, 0xFFF0F0F0.toInt()) }
            },
        )
        assertEquals(4, detected.stones.size)
        assertEquals(StoneColor.Black, detected.stones[BoardCoordinate(row = 4, column = 4)])
        assertEquals(StoneColor.Black, detected.stones[BoardCoordinate(row = 0, column = 0)])
        assertEquals(StoneColor.White, detected.stones[BoardCoordinate(row = 2, column = 6)])
        assertEquals(StoneColor.White, detected.stones[BoardCoordinate(row = 7, column = 8)])
    }

    /** ⚠️ 인쇄 기보의 백돌은 **테두리뿐**이라 밝기가 바탕과 같다 — 격자선을 덮는지·테두리가 있는지로 가른다. */
    @Test
    fun printedDiagramWithOutlinedWhiteStones() {
        val paper = 0xFFFAFAFA.toInt()
        val ink = 0xFF202020.toInt()
        val detected = GridStoneDetector.detect(
            board(paper, ink) { at ->
                at(3, 3).let { (x, y) ->
                    disc(x, y, step * 0.47f, paper)
                    ring(x, y, step * 0.47f, ink)
                }
                at(5, 3).let { (x, y) -> disc(x, y, step * 0.47f, ink) }
            },
        )
        assertEquals(StoneColor.White, detected.stones[BoardCoordinate(row = 3, column = 3)])
        assertEquals(StoneColor.Black, detected.stones[BoardCoordinate(row = 3, column = 5)])
        assertEquals(2, detected.stones.size)
    }

    @Test
    fun emptyBoardHasNoStones() {
        val detected = GridStoneDetector.detect(board(0xFFD2A060.toInt(), 0xFF3A2A10.toInt()) { })
        assertTrue(detected.stones.isEmpty())
    }
}
