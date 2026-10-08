package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.ui.board.BoardLineStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 판의 선·테두리·화점의 굵기(backlog #222, 사용자 피드백 「선, 화점 굵게」) — 칸 간격에 비례하고 dp 하한을 지킨다. */
class BoardLineStyleTest {
    /** S23급 화면(1dp = 2.8125픽셀, 판 폭 약 1010픽셀)의 칸 간격 — 9줄 118 · 13줄 78 · 19줄 53픽셀. */
    private val density = 2.8125f
    private val nine = 118f
    private val thirteen = 78f
    private val nineteen = 53f

    /**
     * **사용자가 폰(S23)에서 보고 정한 값**(2026-10-08) — 9줄 3픽셀, 13줄·19줄 2.4픽셀. 처음 값(4.1 · 2.8픽셀)은 「너무 굵다」였다.
     * 예전의 1.5픽셀 고정보다는 어느 판에서나 굵다.
     */
    @Test
    fun theGridLinesAreWhatTheUserChoseOnThePhone() {
        assertEquals(3.0f, BoardLineStyle.gridLineWidthPx(nine, density), 0.06f)
        assertEquals(2.4f, BoardLineStyle.gridLineWidthPx(thirteen, density), 0.02f)
        assertEquals(2.4f, BoardLineStyle.gridLineWidthPx(nineteen, density), 0.02f)
        listOf(nine, thirteen, nineteen).forEach { spacing ->
            assertTrue("칸 $spacing", BoardLineStyle.gridLineWidthPx(spacing, density) > 1.5f)
        }
    }

    /** 화점은 사용자가 그대로 둔 값이다 — 9줄 반지름 13픽셀, 19줄 7픽셀(S23). */
    @Test
    fun theStarPointsAreWhatTheUserKept() {
        assertEquals(13.0f, BoardLineStyle.starPointRadiusPx(nine, density), 0.05f)
        assertEquals(7.0f, BoardLineStyle.starPointRadiusPx(nineteen, density), 0.05f)
    }

    /** 칸이 넓으면 선도 굵다(비례). 칸이 좁은 13줄·19줄에서는 dp 하한이 받친다 — 비례만으로는 19줄이 0.47dp가 된다. */
    @Test
    fun theLineScalesWithTheCellAndNeverGoesBelowTheDpFloor() {
        assertEquals(118f * BoardLineStyle.GridLineShare, BoardLineStyle.gridLineWidthPx(nine, density), 1e-4f)
        assertEquals(BoardLineStyle.MinGridLineDp * density, BoardLineStyle.gridLineWidthPx(nineteen, density), 1e-4f)
        assertTrue(BoardLineStyle.gridLineWidthPx(nine, density) > BoardLineStyle.gridLineWidthPx(nineteen, density))
        // 밀도가 낮은 화면에서도 하한은 dp로 선다.
        assertEquals(BoardLineStyle.MinGridLineDp, BoardLineStyle.gridLineWidthPx(spacingPx = 20f, density = 1f), 1e-4f)
    }

    /**
     * 같은 그리기를 홈 메뉴 카드의 작은 판(68dp — 9줄이면 칸이 7dp쯤)도 쓴다. 거기서 dp 하한을 고집하면 칸의 1/7이 선이고 화점이
     * 돌만 해진다 — 아주 좁은 칸에서는 하한이 물러서고 칸에 대한 비율이 한도가 된다.
     */
    @Test
    fun onATinyBoardTheMinimumsYieldSoTheGridDoesNotTurnIntoABlob() {
        val tinyCell = 7f * density
        val line = BoardLineStyle.gridLineWidthPx(tinyCell, density)
        val star = BoardLineStyle.starPointRadiusPx(tinyCell, density)

        assertEquals(tinyCell * BoardLineStyle.MaxGridLineShare, line, 1e-4f)
        assertEquals(tinyCell * BoardLineStyle.MaxStarPointRadiusShare, star, 1e-4f)
        assertTrue("선이 하한(dp)보다 가늘다", line < BoardLineStyle.MinGridLineDp * density)
        assertTrue("화점이 돌(칸의 절반 가까이)보다 훨씬 작다", star < tinyCell * 0.2f)
        // 대국 화면의 19줄(칸 약 19dp)에서는 하한이 그대로 선다.
        assertEquals(BoardLineStyle.MinGridLineDp * density, BoardLineStyle.gridLineWidthPx(53f, density), 1e-4f)
        assertEquals(2.5f * density, BoardLineStyle.starPointRadiusPx(53f, density), 1e-4f)
    }

    /** 테두리는 늘 격자선의 두 배다 — 판 크기가 달라도 선과 테두리의 비가 같다. */
    @Test
    fun theBorderIsAlwaysTwiceTheGridLine() {
        listOf(nine, thirteen, nineteen).forEach { spacing ->
            assertEquals(BoardLineStyle.gridLineWidthPx(spacing, density) * 2f, BoardLineStyle.borderWidthPx(spacing, density), 1e-4f)
        }
    }

    /** 화점은 예전(칸의 0.08)보다 크고, 돌(반지름이 칸의 절반 가까이)보다는 훨씬 작다 — 돌이 놓이면 가려진다. */
    @Test
    fun theStarPointsAreBiggerThanBeforeButFarSmallerThanAStone() {
        listOf(nine, thirteen, nineteen).forEach { spacing ->
            val radius = BoardLineStyle.starPointRadiusPx(spacing, density)
            assertTrue("칸 $spacing: 예전보다 크다", radius > spacing * 0.08f)
            assertTrue("칸 $spacing: 돌보다 훨씬 작다", radius < spacing * 0.2f)
            assertTrue("칸 $spacing: 선보다 굵게 보인다", radius * 2f > BoardLineStyle.gridLineWidthPx(spacing, density) * 2f)
        }
    }
}
