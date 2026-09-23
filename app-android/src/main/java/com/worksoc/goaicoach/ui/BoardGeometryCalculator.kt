package com.worksoc.goaicoach.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

internal data class BoardGeometry(
    val origin: Offset,
    val spacing: Float,
    val boardPadding: Float,
) {
    fun pointFor(coordinate: BoardCoordinate): Offset =
        Offset(
            x = origin.x + coordinate.column * spacing,
            y = origin.y + coordinate.row * spacing,
        )

    companion object {
        fun from(size: Size, boardSize: BoardSize, showCoordinates: Boolean): BoardGeometry {
            val tapGeometry = boardTapGeometry(
                canvasWidth = size.width,
                canvasHeight = size.height,
                boardSize = boardSize,
                showCoordinates = showCoordinates,
            ) ?: BoardTapGeometry(originX = 0f, originY = 0f, spacing = 0f, boardPadding = 0f)
            return BoardGeometry(
                origin = Offset(tapGeometry.originX, tapGeometry.originY),
                spacing = tapGeometry.spacing,
                boardPadding = tapGeometry.boardPadding,
            )
        }
    }
}

internal data class BoardTapGeometry(
    val originX: Float,
    val originY: Float,
    val spacing: Float,
    val boardPadding: Float,
)

internal fun boardTapGeometry(
    canvasWidth: Float,
    canvasHeight: Float,
    boardSize: BoardSize,
    showCoordinates: Boolean,
): BoardTapGeometry? {
    if (canvasWidth == 0f || canvasHeight == 0f) {
        return null
    }
    val side = min(canvasWidth, canvasHeight)
    val spacing = if (showCoordinates) {
        side / (boardSize.value + 1f)
    } else {
        side / boardSize.value
    }
    val boardPadding = if (showCoordinates) {
        spacing * 1.0f
    } else {
        spacing * 0.5f
    }
    return BoardTapGeometry(
        originX = (canvasWidth - side) / 2f + boardPadding,
        originY = (canvasHeight - side) / 2f + boardPadding,
        spacing = spacing,
        boardPadding = boardPadding,
    )
}

internal fun boardCoordinateFromTap(
    tapX: Float,
    tapY: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    boardSize: BoardSize,
    showCoordinates: Boolean,
): BoardCoordinate? {
    val geometry = boardTapGeometry(
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        boardSize = boardSize,
        showCoordinates = showCoordinates,
    ) ?: return null
    val column = ((tapX - geometry.originX) / geometry.spacing).roundToInt()
    val row = ((tapY - geometry.originY) / geometry.spacing).roundToInt()
    val coordinate = BoardCoordinate(
        row = row.coerceAtLeast(0),
        column = column.coerceAtLeast(0),
    )
    if (!coordinate.isInside(boardSize)) {
        return null
    }
    val snappedX = geometry.originX + coordinate.column * geometry.spacing
    val snappedY = geometry.originY + coordinate.row * geometry.spacing
    return if (abs(tapX - snappedX) <= geometry.snapTolerance && abs(tapY - snappedY) <= geometry.snapTolerance) {
        coordinate
    } else {
        null
    }
}

/**
 * 교점에서 이만큼까지 벗어난 탭을 그 교점으로 받아들인다 — **반 칸**(2026-09-18 사용자 결정 U-5,
 * 백로그 #152). 곧 *"반상 위를 눌렀으면 언제나 어딘가에 놓인다"* 는 뜻이다.
 *
 * ## ⚠️ 0.45였을 때 무슨 일이 있었는가 (실기 측정, 갤럭시 S23 · 19x19 · 간격 50.7px)
 * 판정이 교점마다 **한 변 0.9칸의 정사각형**이라, 그 사이에 **죽은 띠가 격자 모양으로 판 전체에
 * 깔려 있었다.** 실측으로 확인한 것:
 * - **인접한 두 점 한가운데**(양쪽에서 25.4px)는 **가로·세로 5곳 모두 실패**했다. 폭 약 1.7dp.
 * - 맨 윗줄 바깥 **21px을 넘기면** 실패했는데, **반상 나무는 30px까지 더 뻗어 있다** —
 *   즉 **눈에 보이는 반상 위 약 3dp가 죽어 있었다.**
 * - ⚠️ **그리고 셋 다 침묵했다** — 돌도, 진동도, 가늠돌도 전부 이 판정이 `null`이면 안 나온다
 *   (`GoBoard.kt`의 세 자리가 모두 이 함수에 걸려 있다). 사용자에게는 *"앱이 나를 무시했다"* 로 읽힌다.
 *
 * ## ⚠️ 0.5보다 크게 잡지 말 것
 * 반 칸이 **자연스러운 최대치**다. `roundToInt`가 이미 가장 가까운 교점을 고르므로, 반 칸을 넘기면
 * 더 가까운 이웃이 있는데도 먼 점을 집게 된다. 그리고 판 바깥에서는 이 값이 **유일한 울타리**라
 * (`coerceAtLeast(0)`에는 위쪽 짝이 없고 `isInside`가 반대편을 막는다), 키우면 **반상 밖 여백을
 * 눌러도 가장자리에 돌이 놓인다.**
 *
 * ⚠️ **취소 경로는 그대로 살아 있다** — 반상 **바깥**에서 떼면 좌표가 없어 조용히 취소된다
 * (`GoBoard.kt`가 별도 취소 버튼을 두지 않는 이유). 다만 이제 **반상 위 빈 곳**을 눌러 취소할 수는
 * 없다 — 그 자리에도 가장 가까운 교점이 있기 때문이다(U-5가 고른 맞바꿈).
 */
internal const val SnapTolerance: Float = 0.5f

private val BoardTapGeometry.snapTolerance: Float get() = spacing * SnapTolerance

internal fun coordinateFromTap(
    offset: Offset,
    canvasSize: IntSize,
    boardSize: BoardSize,
    showCoordinates: Boolean,
): BoardCoordinate? {
    return boardCoordinateFromTap(
        tapX = offset.x,
        tapY = offset.y,
        canvasWidth = canvasSize.width.toFloat(),
        canvasHeight = canvasSize.height.toFloat(),
        boardSize = boardSize,
        showCoordinates = showCoordinates,
    )
}

internal fun boardColumnLabels(boardSize: BoardSize): List<Char> {
    val columns = "ABCDEFGHJKLMNOPQRSTUVWXYZ"
    return columns.take(boardSize.value).toList()
}

internal fun starPoints(boardSize: BoardSize): List<BoardCoordinate> =
    when (boardSize.value) {
        9 -> listOf(
            BoardCoordinate(2, 2),
            BoardCoordinate(2, 6),
            BoardCoordinate(4, 4),
            BoardCoordinate(6, 2),
            BoardCoordinate(6, 6),
        )

        13 -> listOf(
            BoardCoordinate(3, 3),
            BoardCoordinate(3, 9),
            BoardCoordinate(6, 6),
            BoardCoordinate(9, 3),
            BoardCoordinate(9, 9),
        )

        19 -> listOf(
            BoardCoordinate(3, 3),
            BoardCoordinate(3, 9),
            BoardCoordinate(3, 15),
            BoardCoordinate(9, 3),
            BoardCoordinate(9, 9),
            BoardCoordinate(9, 15),
            BoardCoordinate(15, 3),
            BoardCoordinate(15, 9),
            BoardCoordinate(15, 15),
        )

        else -> emptyList()
    }
