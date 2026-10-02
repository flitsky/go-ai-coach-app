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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.designsystem.VisionPalette
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.boardScanStringsFor
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class ActiveCorner {
    TopLeft, TopRight, BottomRight, BottomLeft
}

/**
 * 네 모서리 핀으로 바둑판 영역을 지정한다.
 *
 * ⚠️ **사진은 핀 좌표 변환과 똑같은 맞춤(FitInside)으로 그린다**(백로그 #210). 옛 화면은 사진을
 * `(displayedWidth / 2.54f * 72f / 160f).dp` — 뜻 없는 단위 변환에 정사각 강제 — 로 그렸는데, 핀의 화면↔사진 좌표 변환은
 * 사진이 표시 영역을 가득 채운다고 가정했다. 그래서 **보이는 판 모서리에 핀을 정확히 놓아도 엉뚱한 사진 좌표가 넘어갔다**
 * (*"맞춰 설정해도 인식률이 매우 낮다"* 의 1순위 원인). 지금은 사진도 [ContentScale.Fit]으로 같은 상자를 채운다.
 *
 * [initialCorners]가 있으면 자동 인식([com.worksoc.goaicoach.vision.BoardLocator])이 찾은 모서리에서 시작한다.
 */
@Composable
internal fun CornerPinAdjustmentOverlay(
    bitmap: Bitmap,
    initialCorners: BoardCornerPoints?,
    initialBoardSize: BoardSize,
    onCornersConfirmed: (BoardCornerPoints, BoardSize) -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = boardScanStringsFor(LocalUiStrings.current.language)
    var boardSize by remember(bitmap, initialBoardSize) { mutableStateOf(initialBoardSize) }

    // 비트맵 상의 실제 픽셀 좌표계 기준 4개 꼭짓점
    val bmpWidth = bitmap.width.toFloat()
    val bmpHeight = bitmap.height.toFloat()
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    var cornerPoints by remember(bitmap, initialCorners) {
        mutableStateOf(initialCorners ?: BoardCornerPoints.defaultForSize(bmpWidth, bmpHeight, insetRatio = 0.08f))
    }

    var activeDragCorner by remember { mutableStateOf<ActiveCorner?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VisionPalette.EditorBackdrop)
            // targetSdk 36부터 앱이 시스템 바 영역까지 그린다(#25) — 제목이 상태 표시줄에, 버튼이 제스처 바에 깔리지 않게.
            .systemBarsPadding()
            .padding(AppSpacing.Space16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. 헤더 안내
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text.pinTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VisionPalette.OnBackdrop,
                )
                Text(
                    text = if (initialCorners != null) text.pinFoundAutomatically else text.pinNotFound,
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionPalette.OnBackdropMuted,
                )
            }

            Spacer(modifier = Modifier.width(AppSpacing.Space8))
            OutlinedButton(
                onClick = onRetake,
                shape = RoundedCornerShape(AppRadius.Corner8),
            ) {
                Text(text.retake, color = VisionPalette.OnBackdrop)
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.Space12))

        // 2. 바둑판 크기 선택 칩
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${text.boardSizeLabel}: ", color = VisionPalette.OnBackdrop, fontSize = AppTextSize.Text14, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(AppSpacing.Space8))
            listOf(BoardSize.Nine, BoardSize.Thirteen, BoardSize.Nineteen).forEach { size ->
                FilterChip(
                    selected = boardSize == size,
                    onClick = { boardSize = size },
                    label = { Text(text.boardSizeChip(size.value)) },
                    modifier = Modifier.padding(horizontal = AppSpacing.Space4),
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.Space12))

        // 3. 사진 및 4점 핀 터치/드래그 캔버스 영역
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()

            // 사진의 화면 표시 스케일 및 오프셋 계산 (FitInside) — 아래 Image의 ContentScale.Fit과 똑같은 계산이다.
            val scale = minOf(containerWidth / bmpWidth, containerHeight / bmpHeight)
            val displayedWidth = bmpWidth * scale
            val displayedHeight = bmpHeight * scale
            val offsetX = (containerWidth - displayedWidth) / 2f
            val offsetY = (containerHeight - displayedHeight) / 2f

            // 화면 좌표 <-> 비트맵 좌표 변환
            val frame = PinFrame(scale, offsetX, offsetY, bmpWidth, bmpHeight)
            fun bmpToScreen(pt: PointF2D): Offset = frame.toScreen(pt)

            val touchHitRadius = 40.dp.value * 2.5f
            // ⚠️ **끄는 동안 핀 위치가 바뀌어도 제스처를 다시 시작하지 않는다**(백로그 #210). 옛 화면은 `pointerInput`의
            // 키에 `cornerPoints`를 넣어서, 핀이 한 번 움직일 때마다 제스처 감지가 끊기고 새로 시작했다 — 손가락을 계속 끌어도
            // 핀이 조금 가다 멈췄고, `onDragEnd`가 불리지 않아 활성 핀(과 돋보기)이 남았다. 최신 값은 아래 State로 읽는다.
            // ⚠️ 변환은 **값(PinFrame)으로** 넘긴다 — 지역 함수 참조(`::bmpToScreen`)는 새 클로저여도 `==`라 첫 컴포지션 것에
            // 얼어붙는다(함정 46).
            val latestCorners by rememberUpdatedState(cornerPoints)
            val latestFrame by rememberUpdatedState(frame)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val tl = latestFrame.toScreen(latestCorners.topLeft)
                                val tr = latestFrame.toScreen(latestCorners.topRight)
                                val br = latestFrame.toScreen(latestCorners.bottomRight)
                                val bl = latestFrame.toScreen(latestCorners.bottomLeft)

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
                                val currentPos = latestFrame.toScreen(latestCorners.at(corner))
                                val newScreenPos = Offset(currentPos.x + dragAmount.x, currentPos.y + dragAmount.y)
                                cornerPoints = latestCorners.with(corner, latestFrame.toBitmap(newScreenPos))
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
                // 비트맵 이미지 표시 — ⚠️ 위 FitInside 계산과 같은 상자·같은 맞춤이어야 핀 좌표가 맞는다.
                Image(
                    bitmap = imageBitmap,
                    contentDescription = text.photoDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
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
                        color = VisionPalette.Guide,
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
                        val color = if (isActive) VisionPalette.GuideHighlight else VisionPalette.Guide
                        drawCircle(color = VisionPalette.OnBackdrop, radius = handleRadius + 2.dp.toPx(), center = pt)
                        drawCircle(color = color, radius = handleRadius, center = pt)
                        drawCircle(color = VisionPalette.PinCenter, radius = 4.dp.toPx(), center = pt)
                    }

                    // 돋보기 — 끄는 동안 손가락이 모서리를 가린다. 핀 둘레를 크게 보여 교차점에 정확히 맞추게 한다.
                    // 손가락 반대쪽 위 귀에 띄운다(왼쪽 핀이면 오른쪽 위, 오른쪽 핀이면 왼쪽 위).
                    activeDragCorner?.let { corner ->
                        val pinBmp = cornerPoints.at(corner)
                        val loupe = 112.dp.toPx()
                        val zoom = 3f
                        val margin = 8.dp.toPx()
                        val onLeft = corner == ActiveCorner.TopRight || corner == ActiveCorner.BottomRight
                        val center = Offset(if (onLeft) margin + loupe / 2 else size.width - margin - loupe / 2, margin + loupe / 2)
                        // 화면 돋보기 지름이 담는 사진 영역(사진 픽셀)
                        val srcSize = (loupe / (scale * zoom)).roundToInt().coerceAtLeast(8)
                        val srcLeft = (pinBmp.x - srcSize / 2f).roundToInt().coerceIn(0, (bitmap.width - srcSize).coerceAtLeast(0))
                        val srcTop = (pinBmp.y - srcSize / 2f).roundToInt().coerceIn(0, (bitmap.height - srcSize).coerceAtLeast(0))
                        val circle = Path().apply {
                            addOval(androidx.compose.ui.geometry.Rect(center, loupe / 2))
                        }
                        clipPath(circle) {
                            drawImage(
                                image = imageBitmap,
                                srcOffset = IntOffset(srcLeft, srcTop),
                                srcSize = IntSize(minOf(srcSize, bitmap.width), minOf(srcSize, bitmap.height)),
                                dstOffset = IntOffset((center.x - loupe / 2).roundToInt(), (center.y - loupe / 2).roundToInt()),
                                dstSize = IntSize(loupe.roundToInt(), loupe.roundToInt()),
                            )
                        }
                        drawCircle(color = VisionPalette.OnBackdrop, radius = loupe / 2, center = center, style = Stroke(width = 3.dp.toPx()))
                        // 핀이 돋보기 안에서 놓인 자리(사진 경계에 붙어 가운데가 아닐 수 있다)
                        val k = loupe / srcSize
                        val mark = Offset(center.x - loupe / 2 + (pinBmp.x - srcLeft) * k, center.y - loupe / 2 + (pinBmp.y - srcTop) * k)
                        drawLine(VisionPalette.GuideHighlight, Offset(mark.x - 10.dp.toPx(), mark.y), Offset(mark.x + 10.dp.toPx(), mark.y), 2.dp.toPx())
                        drawLine(VisionPalette.GuideHighlight, Offset(mark.x, mark.y - 10.dp.toPx()), Offset(mark.x, mark.y + 10.dp.toPx()), 2.dp.toPx())
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.Space16))

        // 4. 완료 CTA 버튼
        Button(
            onClick = { onCornersConfirmed(cornerPoints, boardSize) },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(AppRadius.Corner12),
        ) {
            Text(
                text = text.recognize,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 사진이 화면에 놓인 자리(ContentScale.Fit) — 비트맵 좌표와 화면 좌표를 오간다. 값 비교가 되도록 data class다. */
private data class PinFrame(val scale: Float, val offsetX: Float, val offsetY: Float, val bmpWidth: Float, val bmpHeight: Float) {
    fun toScreen(pt: PointF2D): Offset = Offset(offsetX + pt.x * scale, offsetY + pt.y * scale)

    fun toBitmap(offset: Offset): PointF2D = PointF2D(
        ((offset.x - offsetX) / scale).coerceIn(0f, bmpWidth),
        ((offset.y - offsetY) / scale).coerceIn(0f, bmpHeight),
    )
}

private fun BoardCornerPoints.at(corner: ActiveCorner): PointF2D = when (corner) {
    ActiveCorner.TopLeft -> topLeft
    ActiveCorner.TopRight -> topRight
    ActiveCorner.BottomRight -> bottomRight
    ActiveCorner.BottomLeft -> bottomLeft
}

private fun BoardCornerPoints.with(corner: ActiveCorner, point: PointF2D): BoardCornerPoints = when (corner) {
    ActiveCorner.TopLeft -> copy(topLeft = point)
    ActiveCorner.TopRight -> copy(topRight = point)
    ActiveCorner.BottomRight -> copy(bottomRight = point)
    ActiveCorner.BottomLeft -> copy(bottomLeft = point)
}
