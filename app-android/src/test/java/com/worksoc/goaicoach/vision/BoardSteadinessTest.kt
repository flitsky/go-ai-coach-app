package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.vision.PointF2D
import org.junit.Assert.assertEquals
import org.junit.Test

/** 라이브 카메라의 자동 촬영 판정(백로그 #263) — 멈춘 판만, 충분히 오래, 충분히 여러 번 본 뒤에. */
class BoardSteadinessTest {
    private fun quad(dx: Float = 0f) = listOf(PointF2D(0.1f + dx, 0.2f), PointF2D(0.9f + dx, 0.2f), PointF2D(0.9f + dx, 0.8f), PointF2D(0.1f + dx, 0.8f))

    @Test
    fun aStillBoardBecomesSteadyAfterTheHoldTime() {
        val s = BoardSteadiness()
        assertEquals(BoardSteadiness.State.Found, s.feed(quad(), 0))
        assertEquals(BoardSteadiness.State.Found, s.feed(quad(0.01f), 400))
        assertEquals(BoardSteadiness.State.Found, s.feed(quad(0.005f), 800))
        assertEquals(BoardSteadiness.State.Steady, s.feed(quad(0.01f), 950))
    }

    @Test
    fun movingTheBoardOrLosingItStartsOver() {
        val s = BoardSteadiness()
        s.feed(quad(), 0)
        s.feed(quad(), 500)
        assertEquals("moved 10% — starts over", BoardSteadiness.State.Found, s.feed(quad(0.1f), 1000))
        assertEquals(BoardSteadiness.State.Found, s.feed(quad(0.1f), 1500))
        assertEquals(BoardSteadiness.State.Searching, s.feed(null, 1800))
        assertEquals(BoardSteadiness.State.Found, s.feed(quad(0.1f), 2000))
        assertEquals(BoardSteadiness.State.Found, s.feed(quad(0.1f), 2500))
        assertEquals(BoardSteadiness.State.Steady, s.feed(quad(0.1f), 2950))
    }

    @Test
    fun twoQuickSightingsAreNotEnough() {
        val s = BoardSteadiness()
        s.feed(quad(), 0)
        assertEquals("only two sightings", BoardSteadiness.State.Found, s.feed(quad(), 1000))
    }

    @Test
    fun aSmallPatchOfTheFrameIsNotABoard() {
        assertEquals(0.48f, BoardSteadiness.areaOf(quad()), 1e-4f)
        assertEquals(true, BoardSteadiness.isLargeEnough(quad()))
        val tvPatch = listOf(PointF2D(0.6f, 0.3f), PointF2D(0.8f, 0.3f), PointF2D(0.8f, 0.5f), PointF2D(0.6f, 0.5f))
        assertEquals(false, BoardSteadiness.isLargeEnough(tvPatch))
    }
}
