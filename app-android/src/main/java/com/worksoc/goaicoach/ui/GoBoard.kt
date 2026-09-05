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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.engine.operation.EngineActivityIndicator
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 진행 중인 돋보기 드래그(#39). 좌표와 **그 드래그 동안 고정된 말풍선 방향**을 함께 들고 있다.
 */
private data class MagnifierDrag(val touch: Offset, val below: Boolean)

/**
 * 손가락과 말풍선 사이 간격. **판 크기가 아니라 dp로 잡는다** — 가려지는 것은 손끝이라는
 * 물리적 크기이고, 판이 작아진다고 손가락이 작아지지는 않는다.
 */
private val MagnifierFingerGap = 28.dp

/** 드래그 중 1배 판에 남기는 가늠돌 — 판세를 가리지 않을 만큼 흐리게(#39). */
private const val DragGhostAlphaOnBoard = 0.5f

/** 확대창 안의 가늠돌 — 여기서는 조준이 목적이라 또렷하게. */
private const val DragGhostAlphaInMagnifier = 0.85f

private const val EngineActivityFrameIntervalMillis = 1_000L
private val ActivityIndicatorDots = listOf("", " .", " ..", " ...")

@Composable
internal fun GoBoard(
    gameState: GameState,
    candidateMoves: List<CandidateMove>,
    moveReviews: List<MoveReviewMarker>,
    ownershipEstimate: OwnershipEstimate?,
    uxOptions: KaTrainUxOptions,
    inputEnabled: Boolean,
    engineActivityIndicator: EngineActivityIndicator?,
    modifier: Modifier = Modifier,
    tentativeMove: BoardCoordinate? = null,
    onCoordinateTap: (BoardCoordinate) -> Unit,
    isGameEnded: Boolean = false,
    isEngineBusy: Boolean = false,
    colors: GoBoardColors = GoBoardColors.Default,
) {
    val premium = LocalPremiumUiState.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 돋보기 드래그(#39). `null`이면 평소 상태다. ⚠️ 이 값은 **그리기 람다 안에서만** 읽는다 —
    // 컴포지션 본문에서 읽으면 손가락이 움직일 때마다 화면 전체가 리컴포즈된다.
    var magnifierDrag by remember { mutableStateOf<MagnifierDrag?>(null) }
    var activityFrame by remember { mutableStateOf(0) }

    LaunchedEffect(engineActivityIndicator) {
        activityFrame = 0
        while (engineActivityIndicator != null) {
            delay(EngineActivityFrameIntervalMillis)
            activityFrame += 1
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
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textAlpha"
    )
    // 마지막 착수 링이 눈에 잘 띄도록 -20%(어둡게)~+20%(밝게)를 0.5초 간격으로 오가는 박동 효과.
    val lastMovePulse by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lastMovePulse"
    )

    val hapticContext = LocalContext.current
    val haptics = remember(hapticContext) { PlayHaptics(hapticContext) }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val boardSide = if (maxWidth < maxHeight) maxWidth else maxHeight
        Box(
            modifier = Modifier
                .size(boardSide)
                .background(if (isGameEnded) colors.boardBackgroundEnded else colors.boardBackgroundActive, RoundedCornerShape(8.dp))
                .border(1.dp, colors.boardBorder, RoundedCornerShape(8.dp))
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TestTags.GoBoard)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(
                        gameState.boardSize,
                        inputEnabled,
                        uxOptions.showCoordinates,
                        uxOptions.isDirectPlayEnabled,
                        uxOptions.isPlayHapticEnabled,
                        uxOptions.isPlayMagnifierEnabled,
                    ) {
                        val holdThresholdMillis = viewConfiguration.longPressTimeoutMillis
                        fun coordinateAt(offset: Offset) =
                            coordinateFromTap(offset, canvasSize, gameState.boardSize, uxOptions.showCoordinates)

                        // ⚠️ **`detectTapGestures`와 `detectDragGesturesAfterLongPress`를 나란히
                        // 두는 방식은 쓸 수 없다.** 앞의 것이 꾹 누름 뒤에 `consumeUntilUp()`으로
                        // 이후 이벤트를 전부 삼켜, 뒤의 드래그 감지기가 굶는다(#39 착수 시 확인).
                        // 그래서 **제스처 루프 하나**가 세 갈래를 직접 가른다:
                        //   ⓐ 임계 전에 뗐다      → 탭(기존 동작 그대로)
                        //   ⓑ 임계 전에 취소됐다   → 아무것도 안 한다. 세로 스크롤이 가져간 경우다
                        //   ⓒ 임계를 넘겼다       → 돋보기 + 드래그, **떼는 순간**에만 착수
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()

                            // 손가락이 **닿는 순간** 약하게 한 번 울린다(#36). 손을 뗄 때가
                            // 아니라 닿을 때인 이유: 이 진동은 "착수됐다"가 아니라 "눌린 것이
                            // 전달됐다"는 신호다. 드래그 착수(#39)가 붙은 뒤에도 이 뜻은 그대로다
                            // — 착수 확정 신호는 **뗄 때** 따로 울린다.
                            //
                            // 반상 위 유효한 교차점을 눌렀을 때만 울린다 — 판 바깥 여백을
                            // 스치는 것까지 울리면 신호가 아니라 소음이 된다. 입력이 막힌
                            // 상황(AI 차례·종국)에서도 울리지 않는다.
                            //
                            // 세기와 그 근거는 `PlayHaptics.kt`에 모여 있다 — 설정 토글이
                            // 같은 함수를 써서 "앞으로 이만큼 울린다"를 미리 들려준다.
                            if (inputEnabled && uxOptions.isPlayHapticEnabled && coordinateAt(down.position) != null) {
                                haptics.play()
                            }

                            // 돋보기가 꺼져 있으면 **예전 동작 그대로**다(#39 토글) — 누른
                            // 자리에서 떼면 그 자리에 두고, 꾹 눌러도 아무 일도 없다.
                            if (!uxOptions.isPlayMagnifierEnabled) {
                                waitForUpOrCancellation()?.let { up ->
                                    up.consume()
                                    if (inputEnabled) coordinateAt(down.position)?.let(onCoordinateTap)
                                }
                                return@awaitEachGesture
                            }

                            var heldPastThreshold = false
                            val releasedEarly = try {
                                withTimeout(holdThresholdMillis) { waitForUpOrCancellation() }
                            } catch (_: PointerEventTimeoutCancellationException) {
                                heldPastThreshold = true
                                null
                            }

                            if (!heldPastThreshold) {
                                // `null`이면 다른 제스처(세로 스크롤)가 가져갔다 — 착수하지 않는다.
                                releasedEarly?.let { up ->
                                    up.consume()
                                    if (inputEnabled) coordinateAt(down.position)?.let(onCoordinateTap)
                                }
                                return@awaitEachGesture
                            }

                            // ⚠️ 입력이 막힌 상황에서는 돋보기도 띄우지 않는다 — 놓을 수 없는
                            // 자리를 확대해 보여주면 놓을 수 있다고 오해한다.
                            if (!inputEnabled) return@awaitEachGesture

                            // ⚠️ 말풍선의 위/아래는 **여기서 한 번만** 정한다 — 매 프레임 다시
                            // 판단하면 손가락이 경계선을 지날 때 창이 반대편으로 순간이동한다.
                            val canvas = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                            // 캔버스가 아직 0이면 계산할 것이 없다 — 손가락이 닿았다면 사실상
                            // 일어나지 않지만, 여기서 터지게 두지는 않는다.
                            val spacing = boardTapGeometry(
                                canvasWidth = canvas.width,
                                canvasHeight = canvas.height,
                                boardSize = gameState.boardSize,
                                showCoordinates = uxOptions.showCoordinates,
                            )?.spacing ?: return@awaitEachGesture
                            val gapPx = MagnifierFingerGap.toPx()
                            val below = magnifierPrefersBelow(
                                down.position,
                                canvas,
                                spacing,
                                gapPx,
                                sizeScale = uxOptions.magnifierSizeScale,
                                zoom = uxOptions.magnifierZoom,
                            )

                            magnifierDrag = MagnifierDrag(down.position, below)
                            var last = down.position
                            // ⚠️ **누르고 있는 동안 착수가 확정되면 안 된다**(사용자 확정).
                            // 여기서는 좌표만 따라가고, 확정은 아래 `completed` 분기에서만 한다.
                            val completed = drag(down.id) { change ->
                                change.consume()
                                last = change.position
                                magnifierDrag = MagnifierDrag(last, below)
                            }
                            magnifierDrag = null
                            if (!completed) return@awaitEachGesture

                            // 판 밖에서 떼면 좌표가 없다 → 조용히 취소된다. 그것이 이 제스처의
                            // 취소 경로다(별도 취소 버튼을 두지 않는 이유).
                            coordinateAt(last)?.let { coordinate ->
                                if (uxOptions.isPlayHapticEnabled) haptics.play()
                                onCoordinateTap(coordinate)
                            }
                        }
                    },
            ) {
                val geometry = BoardGeometry.from(size, gameState.boardSize, uxOptions.showCoordinates)
                drawBoardGrid(geometry, gameState.boardSize, colors.gridLine)
                if (uxOptions.showCoordinates) {
                    drawBoardCoordinates(geometry, gameState.boardSize)
                }
                // 그릴지 말지의 권한 판정은 호출부(GamePlaySection)가 한다 — 여기서 premium.isActive를
                // 다시 보면 1회권으로 켠 표시가 걸러진다(1회권 사용자는 정의상 프리미엄이 비활성이라
                // 티켓만 차감되고 아무것도 안 보이던 버그, 2026-08-29 실기 확인). 보드는 넘어온
                // 데이터가 있으면 그린다.
                if (ownershipEstimate != null) {
                    drawOwnershipOverlay(geometry, gameState, ownershipEstimate)
                }
                drawCandidateMoves(geometry, gameState, candidateMoves)

                for ((coordinate, stone) in gameState.stones) {
                    drawStone(geometry.pointFor(coordinate), geometry.spacing * 0.42f, stone, isGameEnded)
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
                    // 착수 평가가 켜져 있으면 실제 품질 색으로, 그렇지 않으면 중립색으로 그린다 —
                    // 항상 고정된 빨강이면 모든 마지막 착수가 나쁜 수처럼 보이는 문제가 있었다.
                    val reviewTone = if (uxOptions.showMoveReview && premium.isActive) {
                        moveReviews.firstOrNull { marker ->
                            marker.coordinate == lastMove.coordinate && gameState.hasCurrentStoneFor(marker)
                        }?.tone
                    } else {
                        null
                    }
                    // 흑돌 위에서는 어두운 돌 색 때문에 링이 묻혀 보이므로, 흑 착수일 때 기본
                    // 색 자체를 더 크게 밝힌다. 백돌 위에서도 중립색이 상대적으로 어둡게(검게)
                    // 도드라져 보여, 흑만큼은 아니지만 소폭 밝혀 대비를 완화한다.
                    val neutralOrToneColor = reviewTone?.let(::candidateToneColor) ?: colors.lastMoveNeutral
                    val baseRingColor = if (lastMove.player == StoneColor.Black) {
                        neutralOrToneColor.brighten(0.68f)
                    } else {
                        neutralOrToneColor.brighten(0.15f)
                    }
                    val pulsedRingColor = if (lastMovePulse >= 0f) {
                        baseRingColor.brighten(lastMovePulse * 0.2f)
                    } else {
                        baseRingColor.darken(-lastMovePulse * 0.2f)
                    }
                    drawCircle(
                        color = pulsedRingColor,
                        radius = geometry.spacing * 0.48f,
                        center = geometry.pointFor(lastMove.coordinate),
                        style = Stroke(width = 5f),
                    )
                }

                if (uxOptions.showMoveReview && premium.isActive) {
                    drawMoveReviews(geometry, gameState, moveReviews)
                }
                if (uxOptions.showMoveNumbers) {
                    drawMoveNumbers(geometry, gameState)
                }

                // 돋보기(#39) — **맨 마지막에** 그려 판 위에 얹는다.
                val drag = magnifierDrag
                if (drag != null) {
                    val touch = drag.touch
                    val dragCoordinate = coordinateFromTap(
                        touch,
                        canvasSize,
                        gameState.boardSize,
                        uxOptions.showCoordinates,
                    )
                    val stoneRadius = geometry.spacing * 0.42f
                    // 1배 판에도 가늠돌을 남긴다 — 확대창만 보고 두게 되면 판 전체의 모양을
                    // 놓치기 때문이다. 확대창은 조준용, 이쪽은 판세 확인용이다.
                    if (dragCoordinate != null) {
                        drawGhostStone(
                            center = geometry.pointFor(dragCoordinate),
                            radius = stoneRadius,
                            stone = gameState.nextPlayer,
                            alpha = DragGhostAlphaOnBoard,
                        )
                    }
                    drawMagnifier(
                        placement = magnifierPlacement(
                            touch = touch,
                            canvasSize = size,
                            cellSpacing = geometry.spacing,
                            fingerGapPx = MagnifierFingerGap.toPx(),
                            below = drag.below,
                            // ⚠️ **두 곳에 같은 값을 줘야 한다** — 위 `magnifierPrefersBelow`도
                            // 같은 반지름을 계산한다. 한쪽만 주면 말풍선이 뜰 자리를 잘못 재서
                            // 손가락 위/아래 판정이 어긋난다(#85).
                            sizeScale = uxOptions.magnifierSizeScale,
                            zoom = uxOptions.magnifierZoom,
                        ),
                        touch = touch,
                        background = if (isGameEnded) colors.boardBackgroundEnded else colors.boardBackgroundActive,
                        border = colors.boardBorder,
                    ) {
                        // ⚠️ 확대창은 **격자·돌·가늠돌만** 그린다. 형세 오버레이나 추천 수 번호까지
                        // 2배로 들어오면 정작 조준해야 할 교차점이 그 안에 묻힌다.
                        drawBoardGrid(geometry, gameState.boardSize, colors.gridLine)
                        for ((coordinate, stone) in gameState.stones) {
                            drawStone(geometry.pointFor(coordinate), stoneRadius, stone, isGameEnded)
                        }
                        if (dragCoordinate != null) {
                            drawGhostStone(
                                center = geometry.pointFor(dragCoordinate),
                                radius = stoneRadius,
                                stone = gameState.nextPlayer,
                                alpha = DragGhostAlphaInMagnifier,
                            )
                        }
                    }
                }
            }

            val strings = LocalUiStrings.current

            if (engineActivityIndicator != null && engineActivityIndicator != EngineActivityIndicator.Preparing) {
                val label = strings.engineActivityLabel(engineActivityIndicator)
                Text(
                    text = label + ActivityIndicatorDots[activityFrame.mod(ActivityIndicatorDots.size)],
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp),
                    color = colors.engineActivityText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            if (engineActivityIndicator == EngineActivityIndicator.Preparing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White.copy(alpha = textAlpha),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = strings.enginePreparingTitle,
                            color = Color.White.copy(alpha = textAlpha),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.enginePreparingSubtitle,
                            color = Color.White.copy(alpha = textAlpha * 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (gameState.hasConsecutivePasses() && isEngineBusy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White.copy(alpha = textAlpha),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = strings.scoringPreparingTitle,
                            color = Color.White.copy(alpha = textAlpha),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.enginePreparingSubtitle,
                            color = Color.White.copy(alpha = textAlpha * 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

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

internal fun candidateToneColor(tone: MoveReviewTone): Color =
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

/** [fraction]만큼 흰색 쪽으로 섞어 밝게 만든다 (0f = 원래 색, 1f = 흰색). */
private fun Color.brighten(fraction: Float): Color =
    Color(
        red = red + (1f - red) * fraction,
        green = green + (1f - green) * fraction,
        blue = blue + (1f - blue) * fraction,
        alpha = alpha,
    )

/** [fraction]만큼 어둡게 만든다 (0f = 원래 색, 1f = 검정). */
private fun Color.darken(fraction: Float): Color =
    Color(
        red = red * (1f - fraction),
        green = green * (1f - fraction),
        blue = blue * (1f - fraction),
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBoardGrid(
    geometry: BoardGeometry,
    boardSize: BoardSize,
    lineColor: Color,
) {

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
    isGameEnded: Boolean,
) {
    drawCircle(
        color = Color(0x33000000),
        radius = radius * 1.03f,
        center = Offset(center.x + radius * 0.05f, center.y + radius * 0.07f),
    )
    drawCircle(
        brush = stoneBrush(stone, center, radius, isGameEnded),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = stoneEdgeColor(stone, isGameEnded),
        radius = radius,
        center = center,
        style = Stroke(width = 2.2f),
    )
}

// 대국 중(진행 중) 돌의 그라디언트 — 반투명 "고스트" 미리보기(drawGhostStone)가 같은 색을
// alpha만 낮춰 재사용한다. 색을 바꿀 땐 두 곳이 아니라 여기 한 곳만 고치면 된다.
private fun activeStoneGradientColors(stone: StoneColor): List<Color> =
    when (stone) {
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

private fun stoneBrush(
    stone: StoneColor,
    center: Offset,
    radius: Float,
    isGameEnded: Boolean,
): Brush {
    val colors = when (stone) {
        StoneColor.Black -> if (isGameEnded) {
            listOf(
                Color(0xFF787878),
                Color(0xFF393939),
                Color(0xFF131313),
                Color(0xFF030303),
            )
        } else {
            activeStoneGradientColors(stone)
        }

        StoneColor.White -> if (isGameEnded) {
            listOf(
                Color(0xFFCCCCCC),
                Color(0xFFC2C0BB),
                Color(0xFFB3B0A8),
                Color(0xFF9F9B91),
            )
        } else {
            activeStoneGradientColors(stone)
        }
    }
    return Brush.radialGradient(
        colors = colors,
        center = center,
        radius = radius,
    )
}

private fun stoneEdgeColor(stone: StoneColor, isGameEnded: Boolean): Color =
    when (stone) {
        StoneColor.Black -> if (isGameEnded) Color(0xFF707070) else Color(0xFF5E5E5E)
        StoneColor.White -> if (isGameEnded) Color(0xFF726E63) else Color(0xFF8F8A7C)
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
    val mappedColors = activeStoneGradientColors(stone).map { it.copy(alpha = alpha) }
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
        color = stoneEdgeColor(stone, isGameEnded = false).copy(alpha = alpha * 0.9f),
        radius = radius,
        center = center,
        style = Stroke(width = 2.2f),
    )
}
