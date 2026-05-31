package com.worksoc.goaicoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun GoBoard(
    gameState: GameState,
    candidateMoves: List<CandidateMove>,
    inputEnabled: Boolean,
    modifier: Modifier = Modifier,
    onCoordinateTap: (BoardCoordinate) -> Unit,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .background(Color(0xFFD7A85E), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF7A4D20), RoundedCornerShape(8.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(gameState.boardSize, inputEnabled) {
                    detectTapGestures { offset ->
                        if (!inputEnabled) {
                            return@detectTapGestures
                        }
                        coordinateFromTap(offset, canvasSize, gameState.boardSize)
                            ?.let(onCoordinateTap)
                    }
                },
        ) {
            val geometry = BoardGeometry.from(size, gameState.boardSize)
            drawBoardGrid(geometry, gameState.boardSize)
            drawCandidateMoves(geometry, gameState, candidateMoves)

            for ((coordinate, stone) in gameState.stones) {
                drawStone(geometry.pointFor(coordinate), geometry.spacing * 0.42f, stone)
            }

            val lastMove = gameState.moves.lastOrNull() as? Move.Play
            if (lastMove != null) {
                drawCircle(
                    color = Color(0xFFE53935),
                    radius = geometry.spacing * 0.12f,
                    center = geometry.pointFor(lastMove.coordinate),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCandidateMoves(
    geometry: BoardGeometry,
    gameState: GameState,
    candidateMoves: List<CandidateMove>,
) {
    candidateMoves.forEachIndexed { index, candidate ->
        val play = candidate.move as? Move.Play ?: return@forEachIndexed
        if (!play.coordinate.isInside(gameState.boardSize) || gameState.stoneAt(play.coordinate) != null) {
            return@forEachIndexed
        }

        val center = geometry.pointFor(play.coordinate)
        val radius = geometry.spacing * if (index == 0) 0.24f else 0.18f
        val fillAlpha = if (index == 0) 0.72f else 0.38f
        drawCircle(
            color = Color(0xFF2E7D32).copy(alpha = fillAlpha),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = Color(0xFF0B4F24).copy(alpha = 0.9f),
            radius = radius,
            center = center,
            style = Stroke(width = if (index == 0) 4f else 2f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBoardGrid(
    geometry: BoardGeometry,
    boardSize: BoardSize,
) {
    val lineColor = Color(0xFF4A2F17)
    for (index in 0 until boardSize.value) {
        val startHorizontal = geometry.pointFor(BoardCoordinate(index, 0))
        val endHorizontal = geometry.pointFor(BoardCoordinate(index, boardSize.value - 1))
        drawLine(lineColor, startHorizontal, endHorizontal, strokeWidth = 2f)

        val startVertical = geometry.pointFor(BoardCoordinate(0, index))
        val endVertical = geometry.pointFor(BoardCoordinate(boardSize.value - 1, index))
        drawLine(lineColor, startVertical, endVertical, strokeWidth = 2f)
    }

    for (starPoint in starPoints(boardSize)) {
        drawCircle(
            color = lineColor,
            radius = geometry.spacing * 0.08f,
            center = geometry.pointFor(starPoint),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStone(
    center: Offset,
    radius: Float,
    stone: StoneColor,
) {
    val fillColor = when (stone) {
        StoneColor.Black -> Color(0xFF1B1B1B)
        StoneColor.White -> Color(0xFFF8F8F2)
    }
    drawCircle(fillColor, radius, center)
    drawCircle(Color(0xFF1B1B1B), radius, center, style = Stroke(width = 2f))
}

private data class BoardGeometry(
    val origin: Offset,
    val spacing: Float,
) {
    fun pointFor(coordinate: BoardCoordinate): Offset =
        Offset(
            x = origin.x + coordinate.column * spacing,
            y = origin.y + coordinate.row * spacing,
        )

    companion object {
        fun from(size: Size, boardSize: BoardSize): BoardGeometry {
            val side = min(size.width, size.height)
            val boardPadding = side * 0.08f
            val origin = Offset(
                x = (size.width - side) / 2f + boardPadding,
                y = (size.height - side) / 2f + boardPadding,
            )
            return BoardGeometry(
                origin = origin,
                spacing = (side - boardPadding * 2f) / (boardSize.value - 1),
            )
        }
    }
}

private fun coordinateFromTap(
    offset: Offset,
    canvasSize: IntSize,
    boardSize: BoardSize,
): BoardCoordinate? {
    if (canvasSize.width == 0 || canvasSize.height == 0) {
        return null
    }
    val geometry = BoardGeometry.from(
        size = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
        boardSize = boardSize,
    )
    val column = ((offset.x - geometry.origin.x) / geometry.spacing).roundToInt()
    val row = ((offset.y - geometry.origin.y) / geometry.spacing).roundToInt()
    val coordinate = BoardCoordinate(
        row = row.coerceAtLeast(0),
        column = column.coerceAtLeast(0),
    )
    if (!coordinate.isInside(boardSize)) {
        return null
    }
    val snapped = geometry.pointFor(coordinate)
    val tolerance = geometry.spacing * 0.45f
    return if (abs(offset.x - snapped.x) <= tolerance && abs(offset.y - snapped.y) <= tolerance) {
        coordinate
    } else {
        null
    }
}

private fun starPoints(boardSize: BoardSize): List<BoardCoordinate> =
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
