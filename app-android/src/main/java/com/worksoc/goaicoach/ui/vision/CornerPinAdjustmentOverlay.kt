package com.worksoc.goaicoach.ui.vision

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import kotlin.math.hypot

enum class ActiveCorner {
    TopLeft, TopRight, BottomRight, BottomLeft
}

@Composable
internal fun CornerPinAdjustmentOverlay(
    bitmap: Bitmap,
    initialBoardSize: BoardSize = BoardSize.Nineteen,
    onCornersConfirmed: (BoardCornerPoints, BoardSize) -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var boardSize by remember { mutableStateOf(initialBoardSize) }

    // 비트맵 상의 실제 픽셀 좌표계 기준 4개 꼭짓점
    val bmpWidth = bitmap.width.toFloat()
    val bmpHeight = bitmap.height.toFloat()

    var cornerPoints by remember(bitmap) {
        mutableStateOf(BoardCornerPoints.defaultForSize(bmpWidth, bmpHeight, insetRatio = 0.08f))
    }

    var activeDragCorner by remember { mutableStateOf<ActiveCorner?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. 헤더 안내
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "바둑판 영역 지정",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "네 모서리(귀)의 교차점에 핀을 드래그해 맞추세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                )
            }

            OutlinedButton(
                onClick = onRetake,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("재촬영", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. 바둑판 크기 선택 칩
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("판 크기: ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(8.dp))
            listOf(BoardSize.Nine, BoardSize.Thirteen, BoardSize.Nineteen).forEach { size ->
                FilterChip(
                    selected = boardSize == size,
                    onClick = { boardSize = size },
                    label = { Text("${size.value}줄") },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. 사진 및 4점 핀 터치/드래그 캔버스 영역
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()

            // 사진의 화면 표시 스케일 및 오프셋 계산 (FitInside)
            val scale = minOf(containerWidth / bmpWidth, containerHeight / bmpHeight)
            val displayedWidth = bmpWidth * scale
            val displayedHeight = bmpHeight * scale
            val offsetX = (containerWidth - displayedWidth) / 2f
            val offsetY = (containerHeight - displayedHeight) / 2f

            // 화면 좌표 <-> 비트맵 좌표 변환 헬퍼
            fun bmpToScreen(pt: PointF2D): Offset =
                Offset(offsetX + pt.x * scale, offsetY + pt.y * scale)

            fun screenToBmp(offset: Offset): PointF2D {
                val clampedX = ((offset.x - offsetX) / scale).coerceIn(0f, bmpWidth)
                val clampedY = ((offset.y - offsetY) / scale).coerceIn(0f, bmpHeight)
                return PointF2D(clampedX, clampedY)
            }

            val touchHitRadius = 40.dp.value * 2.5f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cornerPoints, scale, offsetX, offsetY) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val tl = bmpToScreen(cornerPoints.topLeft)
                                val tr = bmpToScreen(cornerPoints.topRight)
                                val br = bmpToScreen(cornerPoints.bottomRight)
                                val bl = bmpToScreen(cornerPoints.bottomLeft)

                                val dTL = hypot(startOffset.x - tl.x, startOffset.y - tl.y)
                                val dTR = hypot(startOffset.x - tr.x, startOffset.y - tr.y)
                                val dBR = hypot(startOffset.x - br.x, startOffset.y - br.y)
                                val dBL = hypot(startOffset.x - bl.x, startOffset.y - bl.y)

                                val minDist = minOf(dTL, dTR, dBR, dBL)
                                if (minDist < touchHitRadius) {
                                    activeDragCorner = when (minDist) {
                                        dTL -> ActiveCorner.TopLeft
                                        dTR -> ActiveCorner.TopRight
                                        dBR -> ActiveCorner.BottomRight
                                        else -> ActiveCorner.BottomLeft
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val corner = activeDragCorner ?: return@detectDragGestures
                                val currentPos = bmpToScreen(
                                    when (corner) {
                                        ActiveCorner.TopLeft -> cornerPoints.topLeft
                                        ActiveCorner.TopRight -> cornerPoints.topRight
                                        ActiveCorner.BottomRight -> cornerPoints.bottomRight
                                        ActiveCorner.BottomLeft -> cornerPoints.bottomLeft
                                    }
                                )
                                val newScreenPos = Offset(currentPos.x + dragAmount.x, currentPos.y + dragAmount.y)
                                val newBmpPt = screenToBmp(newScreenPos)

                                cornerPoints = when (corner) {
                                    ActiveCorner.TopLeft -> cornerPoints.copy(topLeft = newBmpPt)
                                    ActiveCorner.TopRight -> cornerPoints.copy(topRight = newBmpPt)
                                    ActiveCorner.BottomRight -> cornerPoints.copy(bottomRight = newBmpPt)
                                    ActiveCorner.BottomLeft -> cornerPoints.copy(bottomLeft = newBmpPt)
                                }
                            },
                            onDragEnd = {
                                activeDragCorner = null
                            },
                            onDragCancel = {
                                activeDragCorner = null
                            },
                        )
                    },
            ) {
                // 비트맵 이미지 표시
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Board Photo",
                    modifier = Modifier
                        .size((displayedWidth / 2.54f * 72f / 160f).dp) // Display size
                        .align(Alignment.Center),
                    contentScale = ContentScale.Fit,
                )

                // 폴리곤 라인 및 4개 핀 그리기
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pTL = bmpToScreen(cornerPoints.topLeft)
                    val pTR = bmpToScreen(cornerPoints.topRight)
                    val pBR = bmpToScreen(cornerPoints.bottomRight)
                    val pBL = bmpToScreen(cornerPoints.bottomLeft)

                    // 4개 점을 잇는 영역 폴리곤
                    val polyPath = Path().apply {
                        moveTo(pTL.x, pTL.y)
                        lineTo(pTR.x, pTR.y)
                        lineTo(pBR.x, pBR.y)
                        lineTo(pBL.x, pBL.y)
                        close()
                    }

                    // 외곽 테두리 (골드/그린 하이라이트)
                    drawPath(
                        path = polyPath,
                        color = Color(0xFF4CAF50),
                        style = Stroke(width = 3.dp.toPx()),
                    )

                    // 4개 코너 핀 원형 핸들
                    val handleRadius = 14.dp.toPx()
                    val pinPoints = listOf(
                        pTL to (activeDragCorner == ActiveCorner.TopLeft),
                        pTR to (activeDragCorner == ActiveCorner.TopRight),
                        pBR to (activeDragCorner == ActiveCorner.BottomRight),
                        pBL to (activeDragCorner == ActiveCorner.BottomLeft),
                    )

                    for ((pt, isActive) in pinPoints) {
                        val color = if (isActive) Color(0xFFFFD700) else Color(0xFF4CAF50)
                        drawCircle(color = Color.White, radius = handleRadius + 2.dp.toPx(), center = pt)
                        drawCircle(color = color, radius = handleRadius, center = pt)
                        drawCircle(color = Color.Black, radius = 4.dp.toPx(), center = pt)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. 완료 CTA 버튼
        Button(
            onClick = { onCornersConfirmed(cornerPoints, boardSize) },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = "바둑판 인식하기",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
