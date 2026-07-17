package com.worksoc.goaicoach.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
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
    val tolerance = geometry.spacing * 0.45f
    return if (abs(tapX - snappedX) <= tolerance && abs(tapY - snappedY) <= tolerance) {
        coordinate
    } else {
        null
    }
}

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
