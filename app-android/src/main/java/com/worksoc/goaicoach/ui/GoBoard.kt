package com.worksoc.goaicoach.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.movereview.MoveReviewTone
import com.worksoc.goaicoach.application.movereview.topMoveDisplayToneFor
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.OwnershipEstimate
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.topMoveDeltaScoreLabel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun GoBoard(
    gameState: GameState,
    candidateMoves: List<CandidateMove>,
    moveReviews: List<MoveReviewMarker>,
    ownershipEstimate: OwnershipEstimate?,
    uxOptions: KaTrainUxOptions,
    inputEnabled: Boolean,
    engineBusy: Boolean,
    modifier: Modifier = Modifier,
    tentativeMove: BoardCoordinate? = null,
    onCoordinateTap: (BoardCoordinate) -> Unit,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var thinkingFrame by remember { mutableStateOf(0) }

    LaunchedEffect(engineBusy) {
        thinkingFrame = 0
        while (engineBusy) {
            delay(200)
            thinkingFrame = (thinkingFrame + 1) % ThinkingFrames.size
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val ghostAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ghostAlpha"
    )

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val boardSide = if (maxWidth < maxHeight) maxWidth else maxHeight
        Box(
            modifier = Modifier
                .size(boardSide)
                .background(Color(0xFFD7A85E), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF7A4D20), RoundedCornerShape(8.dp))
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(
                        gameState.boardSize,
                        inputEnabled,
                        uxOptions.showCoordinates,
                        uxOptions.isDirectPlayEnabled,
                    ) {
                        detectTapGestures { offset ->
                            if (!inputEnabled) {
                                return@detectTapGestures
                            }
                            coordinateFromTap(offset, canvasSize, gameState.boardSize, uxOptions.showCoordinates)
                                ?.let(onCoordinateTap)
                        }
                    },
            ) {
                val geometry = BoardGeometry.from(size, gameState.boardSize, uxOptions.showCoordinates)
                drawBoardGrid(geometry, gameState.boardSize)
                if (uxOptions.showCoordinates) {
                    drawBoardCoordinates(geometry, gameState.boardSize)
                }
                if (ownershipEstimate != null) {
                    drawOwnershipOverlay(geometry, gameState, ownershipEstimate)
                }
                drawCandidateMoves(geometry, gameState, candidateMoves)

                for ((coordinate, stone) in gameState.stones) {
                    drawStone(geometry.pointFor(coordinate), geometry.spacing * 0.42f, stone)
                }

                if (tentativeMove != null) {
                    drawGhostStone(
                        center = geometry.pointFor(tentativeMove),
                        radius = geometry.spacing * 0.42f,
                        stone = gameState.nextPlayer,
                        alpha = ghostAlpha
                    )
                }

                val lastMove = gameState.moves.lastOrNull() as? Move.Play
                if (lastMove != null && uxOptions.showLastMoveRing) {
                    drawCircle(
                        color = Color(0xFFE53935),
                        radius = geometry.spacing * 0.48f,
                        center = geometry.pointFor(lastMove.coordinate),
                        style = Stroke(width = 3.5f),
                    )
                }
                if (lastMove != null && !uxOptions.showMoveNumbers) {
                    drawCircle(
                        color = Color(0xFFE53935),
                        radius = geometry.spacing * 0.12f,
                        center = geometry.pointFor(lastMove.coordinate),
                    )
                }

                drawMoveReviews(geometry, gameState, moveReviews)
                if (uxOptions.showMoveNumbers) {
                    drawMoveNumbers(geometry, gameState)
                }
            }

            if (engineBusy) {
                val thinkingText = ThinkingFrames[thinkingFrame]
                if (thinkingText.isNotEmpty()) {
                    Text(
                        text = thinkingText,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 2.dp),
                        color = Color(0xFF3F2612),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private val ThinkingFrames = listOf(
    "Thinking",
    "Thinking .",
    "Thinking ..",
    "Thinking ...",
    "",
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOwnershipOverlay(
    geometry: BoardGeometry,
    gameState: GameState,
    ownershipEstimate: OwnershipEstimate,
) {
    ownershipEstimate.points.forEach { point ->
        if (!point.coordinate.isInside(gameState.boardSize)) {
            return@forEach
        }
        val strength = abs(point.value).toFloat().coerceIn(0.0f, 1.0f)
        if (strength < ownershipEstimate.threshold.toFloat()) {
            return@forEach
        }
        val center = geometry.pointFor(point.coordinate)
        val baseColor = if (point.value < 0.0) {
            Color(0xFF1F2327)
        } else {
            Color(0xFFFFFFFF)
        }
        val radius = geometry.spacing * (0.68f + strength * 0.42f)
        val centerAlpha = 0.22f + strength * 0.38f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    baseColor.copy(alpha = centerAlpha),
                    baseColor.copy(alpha = centerAlpha * 0.42f),
                    baseColor.copy(alpha = 0.0f),
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBoardCoordinates(
    geometry: BoardGeometry,
    boardSize: BoardSize,
) {
    val textSize = geometry.spacing * 0.24f
    val columnLabels = boardColumnLabels(boardSize)
    for (index in 0 until boardSize.value) {
        val bottomPoint = geometry.pointFor(BoardCoordinate(boardSize.value - 1, index))
        drawBoardLabel(
            label = columnLabels[index].toString(),
            center = Offset(bottomPoint.x, bottomPoint.y + geometry.boardPadding / 2f),
            textSize = textSize,
        )

        val rowLabel = (boardSize.value - index).toString()
        val rightPoint = geometry.pointFor(BoardCoordinate(index, boardSize.value - 1))
        drawBoardLabel(
            label = rowLabel,
            center = Offset(rightPoint.x + geometry.boardPadding / 2f, rightPoint.y),
            textSize = textSize,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMoveNumbers(
    geometry: BoardGeometry,
    gameState: GameState,
) {
    gameState.stones.forEach { (coordinate, stone) ->
        val moveNumber = gameState.currentMoveNumberAt(coordinate) ?: return@forEach
        drawMoveNumberLabel(
            center = geometry.pointFor(coordinate),
            label = moveNumber.toString(),
            stone = stone,
            textSize = geometry.spacing * if (moveNumber < 100) 0.28f else 0.23f,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCandidateMoves(
    geometry: BoardGeometry,
    gameState: GameState,
    candidateMoves: List<CandidateMove>,
) {
    val bestShownPointLoss = candidateMoves.minOfOrNull { candidate ->
        candidate.pointLoss ?: Double.POSITIVE_INFINITY
    }?.takeIf { it.isFinite() }
    val worstShownPointLoss = candidateMoves.maxOfOrNull { candidate ->
        candidate.pointLoss ?: Double.NEGATIVE_INFINITY
    }?.takeIf { it.isFinite() }

    candidateMoves.forEachIndexed { index, candidate ->
        val play = candidate.move as? Move.Play ?: return@forEachIndexed
        val pointLoss = candidate.pointLoss ?: return@forEachIndexed
        if (!play.coordinate.isInside(gameState.boardSize) || gameState.stoneAt(play.coordinate) != null) {
            return@forEachIndexed
        }

        val center = geometry.pointFor(play.coordinate)
        val radius = geometry.spacing * if (index == 0) 0.24f else 0.18f
        val fillAlpha = if (index == 0) 0.76f else 0.48f
        val color = candidateToneColor(
            topMoveDisplayToneFor(pointLoss, bestShownPointLoss, worstShownPointLoss),
        )
        drawCircle(
            color = color.copy(alpha = fillAlpha),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = color.darken().copy(alpha = 0.9f),
            radius = radius,
            center = center,
            style = Stroke(width = if (index == 0) 4f else 2f),
        )
        candidate.topMoveDeltaScoreLabel()
            ?.let { drawSpotLabel(center, it, geometry.spacing * 0.28f) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMoveReviews(
    geometry: BoardGeometry,
    gameState: GameState,
    markers: List<MoveReviewMarker>,
) {
    markers
        .filter { marker -> gameState.hasCurrentStoneFor(marker) }
        .forEach { marker ->
            val center = geometry.pointFor(marker.coordinate)
            val radius = geometry.spacing * 0.12f
            val color = candidateToneColor(marker.tone)
            drawCircle(
                color = color.copy(alpha = 0.92f),
                radius = radius,
                center = center,
            )
            drawCircle(
                color = color.darken(),
                radius = radius,
                center = center,
                style = Stroke(width = 2.5f),
            )
        }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpotLabel(
    center: Offset,
    label: String,
    textSize: Float,
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.nativeCanvas.drawText(
            label,
            center.x,
            center.y - (paint.descent() + paint.ascent()) / 2f,
            paint,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBoardLabel(
    center: Offset,
    label: String,
    textSize: Float,
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(74, 47, 23)
            textAlign = Paint.Align.CENTER
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.nativeCanvas.drawText(
            label,
            center.x,
            center.y - (paint.descent() + paint.ascent()) / 2f,
            paint,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMoveNumberLabel(
    center: Offset,
    label: String,
    stone: StoneColor,
    textSize: Float,
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (stone) {
                StoneColor.Black -> android.graphics.Color.WHITE
                StoneColor.White -> android.graphics.Color.rgb(24, 24, 24)
            }
            textAlign = Paint.Align.CENTER
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val outline = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = textSize * 0.11f
            color = when (stone) {
                StoneColor.Black -> android.graphics.Color.rgb(18, 18, 18)
                StoneColor.White -> android.graphics.Color.WHITE
            }
        }
        val baseline = center.y - (paint.descent() + paint.ascent()) / 2f
        canvas.nativeCanvas.drawText(label, center.x, baseline, outline)
        canvas.nativeCanvas.drawText(label, center.x, baseline, paint)
    }
}

private fun candidateToneColor(tone: MoveReviewTone): Color =
    when (tone) {
        MoveReviewTone.Excellent -> Color(0xFF2E7D32)
        MoveReviewTone.Good -> Color(0xFF8BC34A)
        MoveReviewTone.Inaccuracy -> Color(0xFFFDD835)
        MoveReviewTone.Mistake -> Color(0xFFEF6C00)
        MoveReviewTone.Blunder -> Color(0xFFC62828)
        MoveReviewTone.Unknown -> Color(0xFF607D8B)
    }

private fun Color.darken(): Color =
    Color(
        red = red * 0.62f,
        green = green * 0.62f,
        blue = blue * 0.62f,
        alpha = alpha,
    )

private fun GameState.hasCurrentStoneFor(marker: MoveReviewMarker): Boolean {
    if (stoneAt(marker.coordinate) == null) {
        return false
    }
    val latestMoveIndex = moves.indexOfLast { move ->
        move is Move.Play && move.coordinate == marker.coordinate
    }
    return latestMoveIndex >= 0 && latestMoveIndex + 1 == marker.moveNumber
}

private fun GameState.currentMoveNumberAt(coordinate: BoardCoordinate): Int? {
    if (stoneAt(coordinate) == null) {
        return null
    }
    val latestMoveIndex = moves.indexOfLast { move ->
        move is Move.Play && move.coordinate == coordinate
    }
    return if (latestMoveIndex >= 0) latestMoveIndex + 1 else null
}

private fun boardColumnLabels(boardSize: BoardSize): List<Char> {
    val columns = "ABCDEFGHJKLMNOPQRSTUVWXYZ"
    return columns.take(boardSize.value).toList()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBoardGrid(
    geometry: BoardGeometry,
    boardSize: BoardSize,
) {
    val lineColor = Color(0xFF4A2F17)

    // 1. 내부 격자선 그리기 (굵기 1.5f)
    for (index in 0 until boardSize.value) {
        val startHorizontal = geometry.pointFor(BoardCoordinate(index, 0))
        val endHorizontal = geometry.pointFor(BoardCoordinate(index, boardSize.value - 1))
        drawLine(lineColor, startHorizontal, endHorizontal, strokeWidth = 1.5f)

        val startVertical = geometry.pointFor(BoardCoordinate(0, index))
        val endVertical = geometry.pointFor(BoardCoordinate(boardSize.value - 1, index))
        drawLine(lineColor, startVertical, endVertical, strokeWidth = 1.5f)
    }

    // 2. 바둑판 최외곽 테두리 사각형 선 그리기 (굵기 3.5f) - 바둑돌보다 아래 레이어
    val topLeft = geometry.pointFor(BoardCoordinate(0, 0))
    val bottomRight = geometry.pointFor(BoardCoordinate(boardSize.value - 1, boardSize.value - 1))
    drawRect(
        color = lineColor,
        topLeft = topLeft,
        size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
        style = Stroke(width = 3.5f)
    )

    // 3. 화점(Star Points) 그리기
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
    drawCircle(
        color = Color(0x33000000),
        radius = radius * 1.03f,
        center = Offset(center.x + radius * 0.05f, center.y + radius * 0.07f),
    )
    drawCircle(
        brush = stoneBrush(stone, center, radius),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = stoneEdgeColor(stone),
        radius = radius,
        center = center,
        style = Stroke(width = 2.2f),
    )
}

private fun stoneBrush(
    stone: StoneColor,
    center: Offset,
    radius: Float,
): Brush {
    val colors = when (stone) {
        StoneColor.Black -> listOf(
            Color(0xFF646464),
            Color(0xFF303030),
            Color(0xFF101010),
            Color(0xFF030303),
        )

        StoneColor.White -> listOf(
            Color(0xFFFFFFFF),
            Color(0xFFF3F1EA),
            Color(0xFFE0DDD3),
            Color(0xFFC7C2B6),
        )
    }
    return Brush.radialGradient(
        colors = colors,
        center = center,
        radius = radius,
    )
}

private fun stoneEdgeColor(stone: StoneColor): Color =
    when (stone) {
        StoneColor.Black -> Color(0xFF5E5E5E)
        StoneColor.White -> Color(0xFF8F8A7C)
    }

private data class BoardGeometry(
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

private fun coordinateFromTap(
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGhostStone(
    center: Offset,
    radius: Float,
    stone: StoneColor,
    alpha: Float,
) {
    drawCircle(
        color = Color(0x11000000).copy(alpha = 0.11f * (alpha / 0.65f)),
        radius = radius * 1.03f,
        center = Offset(center.x + radius * 0.05f, center.y + radius * 0.07f),
    )
    val baseColors = when (stone) {
        StoneColor.Black -> listOf(
            Color(0xFF646464),
            Color(0xFF303030),
            Color(0xFF101010),
            Color(0xFF030303),
        )
        StoneColor.White -> listOf(
            Color(0xFFFFFFFF),
            Color(0xFFF3F1EA),
            Color(0xFFE0DDD3),
            Color(0xFFC7C2B6),
        )
    }
    val mappedColors = baseColors.map { it.copy(alpha = alpha) }
    drawCircle(
        brush = Brush.radialGradient(
            colors = mappedColors,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = stoneEdgeColor(stone).copy(alpha = alpha * 0.9f),
        radius = radius,
        center = center,
        style = Stroke(width = 2.2f),
    )
}
