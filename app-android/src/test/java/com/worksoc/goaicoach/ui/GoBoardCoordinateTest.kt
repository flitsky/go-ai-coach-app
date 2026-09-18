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

    /**
     * ⚠️ **옛 계약이 뒤집혔다**(2026-09-18 사용자 결정 U-5, 백로그 #152). 예전에는 이 탭이
     * `null`이었다 — 9x9/900px에서 간격 100px이라 `x=500`은 col4(=450)에서 **50px, 정확히 반 칸**
     * 떨어져 옛 허용오차 `0.45`(45px)를 넘겼기 때문이다.
     *
     * 실기에서 그 0.05칸이 **죽은 띠**로 드러났다 — 인접 두 점 한가운데를 누르면 돌도 진동도
     * 가늠돌도 나오지 않았다(갤럭시 S23 실측 5/5 실패). 이제 **반상 위면 반드시 어딘가에 놓인다.**
     */
    @Test
    fun tapCoordinateBetweenTwoPointsNowSnapsToTheNearerOne() {
        assertEquals(
            "두 점 한가운데가 여전히 무시된다 — 죽은 띠가 돌아왔다(#152 회귀).",
            BoardCoordinate(row = 4, column = 5),
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

    /**
     * ⚠️ **반 칸을 넘기면 여전히 무시한다 — 울타리가 사라진 것이 아니다.**
     * 반상 **바깥** 여백을 눌러도 가장자리에 돌이 놓이면, 판을 벗어나 떼서 취소하는 경로가 죽는다.
     */
    @Test
    fun tapCoordinateStillRejectsTapsBeyondHalfACellOutsideTheBoard() {
        // col0 = x=50, 간격 100 → 반 칸(50px) 바깥인 x=-5는 어떤 교점에도 닿지 않는다.
        assertNull(
            boardCoordinateFromTap(
                tapX = -5f,
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
