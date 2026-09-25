package com.worksoc.goaicoach.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.worksoc.goaicoach.application.preferences.PlayEffectMillis
import com.worksoc.goaicoach.application.preferences.PlayEffectPeakScale
import com.worksoc.goaicoach.platform.PlayHaptics
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.LegalMoveGenerator
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.OwnershipEstimate
import com.worksoc.goaicoach.shared.policy.topMoveDeltaScoreLabel
import kotlin.math.abs
import kotlinx.coroutines.delay

// 착수 드래그 상태(`PlayDrag`)와 임계 전 추적기는 `BoardPlayDrag.kt`에 있다(#138).

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
    /**
     * **지연 착수**(백로그 #144)로 확정을 기다리는 자리. 없으면 `null`.
     *
     * ⚠️ 진행도를 **값이 아니라 람다로** 받는다 — 컴포지션 본문에서 애니메이션 값을 읽으면 0.5초 내내
     * 매 프레임 화면 전체가 리컴포즈된다(`playDrag`가 같은 이유로 그리기 람다 안에서만 읽힌다).
     */
    pendingPlay: BoardCoordinate? = null,
    pendingPlayProgress: () -> Float = { 0f },
    /**
     * 판에 **손가락이 닿는 순간** 알린다(#144 실기 결함, 2026-09-12 사용자).
     *
     * ⚠️ 지연 착수의 0.5초는 **판에서 손이 떨어져 있을 때만** 돌아야 한다. 안 그러면 새 자리를 누르고
     * 있는 동안 0.5초가 끝나 **옛 자리가 확정**된다 — 손가락은 누르고 떼는 데 0.5초를 쉽게 넘긴다.
     * ⚠️ 합성 탭(`adb input tap`)은 down→up이 몇 ms라 이 결함을 **못 잡는다.** 실기에서만 드러났다.
     */
    onCoordinatePress: () -> Unit = {},
    onCoordinateTap: (BoardCoordinate) -> Unit,
    isGameEnded: Boolean = false,
    isEngineBusy: Boolean = false,
    colors: GoBoardColors = GoBoardColors.Default,
) {
    val premium = LocalPremiumUiState.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 착수 드래그(#39). `null`이면 평소 상태다. ⚠️ 이 값은 **그리기 람다 안에서만** 읽는다 —
    // 컴포지션 본문에서 읽으면 손가락이 움직일 때마다 화면 전체가 리컴포즈된다.
    var playDrag by remember { mutableStateOf<PlayDrag?>(null) }
    var activityFrame by remember { mutableStateOf(0) }

    // 착수 이펙트(#145) — 확정되는 순간 **그 돌 하나만** 120%까지 커졌다가 100%로 돌아온다.
    // ⚠️ 배율도 자리도 **그리기 람다 안에서만** 읽는다(`playDrag`와 같은 이유) — 컴포지션 본문에서 읽으면
    //   이펙트가 도는 내내 화면 전체가 리컴포즈된다.
    val playEffectScale = remember { Animatable(1f) }
    var playEffectAt by remember { mutableStateOf<BoardCoordinate?>(null) }
    var seenMoveCount by remember { mutableIntStateOf(gameState.moves.size) }

    // ⚠️ **새 수에만 붙는다**(#145 착수 전 결정, 사람·AI 모두). 수가 **정확히 하나** 늘고 마지막이 `Move.Play`일 때만.
    //   · 무르기는 수가 **줄어** 걸러진다.
    //   · 저장 대국 이어받기·새 판·판 크기 변경은 **점프**라 걸러진다(한 수짜리 복원은 이 앱에 없다 — `UndoLastTurn`뿐).
    //   · 통과는 `Move.Pass`라 좌표가 없어 `as?`에서 걸러진다.
    //   · 첫 컴포지션은 이전 값이 곧 현재 값이라 튀지 않는다 — 이어받기에서 돌이 뛰면 안 된다.
    LaunchedEffect(gameState.moves.size) {
        val previous = seenMoveCount
        val current = gameState.moves.size
        seenMoveCount = current
        if (!uxOptions.isPlayEffectEnabled) return@LaunchedEffect
        if (current != previous + 1) return@LaunchedEffect
        val placed = gameState.moves.lastOrNull() as? Move.Play ?: return@LaunchedEffect
        playEffectAt = placed.coordinate
        playEffectScale.snapTo(1f)
        val half = (PlayEffectMillis / 2).toInt()
        playEffectScale.animateTo(PlayEffectPeakScale, tween(durationMillis = half, easing = LinearEasing))
        playEffectScale.animateTo(1f, tween(durationMillis = half, easing = LinearEasing))
        playEffectAt = null
    }

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
                    ) {
                        val holdThresholdMillis = viewConfiguration.longPressTimeoutMillis
                        val touchSlop = viewConfiguration.touchSlop
                        fun coordinateAt(offset: Offset) =
                            coordinateFromTap(offset, canvasSize, gameState.boardSize, uxOptions.showCoordinates)

                        // ⚠️ **`detectTapGestures`와 `detectDragGesturesAfterLongPress`를 나란히
                        // 두는 방식은 쓸 수 없다.** 앞의 것이 꾹 누름 뒤에 `consumeUntilUp()`으로
                        // 이후 이벤트를 전부 삼켜, 뒤의 드래그 감지기가 굶는다(#39 착수 시 확인).
                        // 그래서 **제스처 루프 하나**가 직접 가른다(#138에서 모양이 바뀌었다):
                        //   ① 누르는 순간   → 가늠돌을 그린다. 확대창은 아직 띄우지 않는다
                        //   ② 임계 전       → 가늠돌이 손가락을 따라간다(슬롭 밖으로 움직였을 때부터).
                        //                    이벤트를 **판이 가진다** — 판이 항상 우선이다(2026-09-11
                        //                    사용자 결정, 사유는 `trackPressUntilUp`).
                        //                    떼면 **가늠돌이 있던 자리**에 놓인다
                        //   ③ 임계를 넘겼다 → 계속 따라가고, 떼는 순간에만 착수
                        // ⚠️ **확대 창은 2026-09-22에 없앴다**(백로그 #188) — 가늠돌과 끌어서 두기는
                        // 그대로 남는다. 토글로 제스처를 가르던 옛 갈래를 되살리지 말 것.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()

                            // 지연 착수(#144)의 카운트를 **멈춘다** — 0.5초는 판에서 손이 떨어져
                            // 있을 때만 돈다. 여기서 멈추지 않으면 새 자리를 누르고 있는 사이에
                            // 0.5초가 끝나 **옛 자리가 확정**된다(2026-09-12 실기 결함).
                            // ⚠️ `inputEnabled` 판정보다 **먼저** 부른다 — 멈추는 일은 놓을 수
                            // 있든 없든 똑같이 해야 한다.
                            onCoordinatePress()

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

                            // ⚠️ 입력이 막힌 상황(AI 차례·종국)에서는 가늠돌도 확대창도 띄우지 않는다 —
                            // 놓을 수 없는 자리에 돌을 그려 보이면 놓을 수 있다고 오해한다.
                            if (!inputEnabled) return@awaitEachGesture

                            // ⚠️ **어떤 경로로 끝나든 가늠돌을 지운다**(#138). 누르고 있는 동안
                            // `inputEnabled`가 바뀌면(AI 차례가 끝나는 순간) 이 `pointerInput`이 **키
                            // 변경으로 다시 시작**되고, 진행 중이던 제스처 코루틴은 **취소**된다. 그때
                            // 지우지 않으면 손을 뗀 뒤에도 판 위에 가늠돌이 남는다 — 가늠돌이 누르는
                            // 즉시 뜨게 된 뒤로 그 틈이 매 착수마다 열린다.
                            try {
                                // ① 누르는 **순간** 가늠돌(#138, 사용자 요청 *"'착수 확인'처럼 돌이 먼저
                                //   보여지게"*). 예전에는 임계(약 0.4초)를 넘겨야 채워져서, 빠른 탭은
                                //   **아무것도 안 보인 채** 처음 누른 자리에 놓였다.
                                // 띄움 폭은 **칸 기준**이므로 지금 판의 간격이 필요하다(#154).
                                // ⚠️ 제스처가 시작될 때마다 다시 읽는다 — `pointerInput`의 키에
                                // `canvasSize`가 없어서, 판 크기가 바뀌어도 블록이 다시 시작되지 않는다.
                                val liftPx = boardTapGeometry(
                                    canvasWidth = canvasSize.width.toFloat(),
                                    canvasHeight = canvasSize.height.toFloat(),
                                    boardSize = gameState.boardSize,
                                    showCoordinates = uxOptions.showCoordinates,
                                )?.let { it.spacing * PlayDragLiftCells } ?: 0f

                                var follow = DragFollow(target = down.position, following = false)
                                playDrag = PlayDrag(follow.target, below = false)

                                // 끌기 중 착수 불가 위치를 짚을 때마다 짧게 두 번 울린다(2026-09-20
                                // 사용자 지시). 좌표가 바뀔 때만 검사한다 — 매 프레임 다시 물으면 같은
                                // 자리에 머무는 동안에도 계속 울려 소음이 된다.
                                var lastHoverCoordinate: BoardCoordinate? = null
                                fun maybeSignalInvalidHover(coordinate: BoardCoordinate?) {
                                    if (!uxOptions.isPlayHapticEnabled) return
                                    if (coordinate == null || coordinate == lastHoverCoordinate) return
                                    lastHoverCoordinate = coordinate
                                    if (!LegalMoveGenerator.isLegalPlay(gameState, coordinate)) haptics.playInvalid()
                                }

                                // ② 임계 전 — 따라가며 이벤트를 판이 가진다(사유는 `trackPressUntilUp`).
                                var heldPastThreshold = false
                                val released = try {
                                    withTimeout(holdThresholdMillis) {
                                        trackPressUntilUp(down.id) { position ->
                                            follow = followDrag(
                                                down = down.position,
                                                current = position,
                                                touchSlop = touchSlop,
                                                following = follow.following,
                                                liftPx = liftPx,
                                            )
                                            playDrag = PlayDrag(follow.target, below = false)
                                            // 슬롭을 넘어 실제로 끌 때만 짚는다 — 안 그러면 누른 첫
                                            // 좌표에서 곧바로 판정이 나 손끝을 떼기 전인데 울린다.
                                            if (follow.following) maybeSignalInvalidHover(coordinateAt(follow.target))
                                        }
                                    }
                                } catch (_: PointerEventTimeoutCancellationException) {
                                    heldPastThreshold = true
                                    false
                                }

                                if (!heldPastThreshold) {
                                    // `false`면 판보다 먼저 누가 가져갔다 — 착수하지 않는다.
                                    if (!released) return@awaitEachGesture
                                    // ⚠️ **누른 자리가 아니라 가늠돌이 있던 자리에 놓는다**(#138) — 보이는 곳과
                                    // 놓이는 곳이 같아야 한다. 슬롭 안의 떨림은 `followDrag`가 처음 자리로
                                    // 붙잡아 두므로, #39가 막으려던 *"살짝 미끄러진 탭"* 회귀는 여전히 막혀 있다.
                                    coordinateAt(follow.target)?.let { coordinate ->
                                        // 끌어서 옮겼을 때만 뗄 때 한 번 더 울린다 — 탭은 닿을 때 한 번이면 된다.
                                        // 착수 불가 자리면 정상 착수음 대신 짧게 두 번 울린다.
                                        if (follow.following && uxOptions.isPlayHapticEnabled) {
                                            if (LegalMoveGenerator.isLegalPlay(gameState, coordinate)) {
                                                haptics.play()
                                            } else {
                                                haptics.playInvalid()
                                            }
                                        }
                                        onCoordinateTap(coordinate)
                                    }
                                    return@awaitEachGesture
                                }

                                // ③ 임계를 넘겼다 — 계속 따라가고, 떼는 순간에만 착수한다.

                                // ⚠️ **임계를 넘겨도 띄움을 내리지 않는다**(백로그 #188).
                                // 2026-09-18의 U-9는 *"돋보기가 뜨면 띄움을 내려라"* 였는데,
                                // **확대창을 없앴으므로 대신해 줄 것이 사라졌다** — 그 규칙이
                                // 저절로 "언제나 띄운다"로 도는 것이 그 결정의 단서였다
                                // (*"끌기가 안정화되면 돋보기를 비활성화할 수도 있다"*, 사용자).
                                val holdLiftPx = liftPx
                                var finger = follow.finger
                                var target = Offset(finger.x, finger.y - holdLiftPx)
                                playDrag = PlayDrag(target, below = false, finger = finger)
                                // ⚠️ **누르고 있는 동안 착수가 확정되면 안 된다**(사용자 확정).
                                // 여기서는 좌표만 따라가고, 확정은 아래 `completed` 분기에서만 한다.
                                val completed = drag(down.id) { change ->
                                    change.consume()
                                    finger = change.position
                                    target = Offset(finger.x, finger.y - holdLiftPx)
                                    playDrag = PlayDrag(target, below = false, finger = finger)
                                    maybeSignalInvalidHover(coordinateAt(target))
                                }
                                if (!completed) return@awaitEachGesture

                                // 판 밖에서 떼면 좌표가 없다 → 조용히 취소된다. 그것이 이 제스처의
                                // 취소 경로다(별도 취소 버튼을 두지 않는 이유).
                                coordinateAt(target)?.let { coordinate ->
                                    if (uxOptions.isPlayHapticEnabled) {
                                        if (LegalMoveGenerator.isLegalPlay(gameState, coordinate)) {
                                            haptics.play()
                                        } else {
                                            haptics.playInvalid()
                                        }
                                    }
                                    onCoordinateTap(coordinate)
                                }
                            } finally {
                                playDrag = null
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
                    // 착수 이펙트(#145)는 **방금 놓인 돌**에만 붙는다 — 나머지는 배율 1이라 그대로다.
                    val effectScale = if (coordinate == playEffectAt) playEffectScale.value else 1f
                    drawStone(
                        geometry.pointFor(coordinate),
                        geometry.spacing * 0.42f * effectScale,
                        stone,
                        isGameEnded,
                    )
                }

                if (tentativeMove != null) {
                    drawGhostStone(
                        center = geometry.pointFor(tentativeMove),
                        radius = geometry.spacing * 0.42f,
                        stone = gameState.nextPlayer,
                        alpha = ghostAlpha
                    )
                }

                // 지연 착수(#144): 기다리는 동안 **점점 진해진다.** 깜빡이는 가늠돌(위)과 일부러 다른 표현이다 —
                // 저쪽은 "여기 둘까?"이고 이쪽은 "곧 놓인다"라서, 시간이 흐르는 것이 보여야 한다.
                if (pendingPlay != null) {
                    drawGhostStone(
                        center = geometry.pointFor(pendingPlay),
                        radius = geometry.spacing * 0.42f,
                        stone = gameState.nextPlayer,
                        alpha = PendingPlayMinAlpha +
                            (1f - PendingPlayMinAlpha) * pendingPlayProgress().coerceIn(0f, 1f),
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
                    // 착수 이펙트(#145)가 도는 동안에는 **테두리도 돌과 같은 배율로** 커졌다 돌아온다
                    // (2026-09-12 사용자 요청). 돌만 부풀면 테두리가 돌 안으로 파고들어, 커지는 내내
                    // 표시가 어긋나 보인다.
                    // ⚠️ 배율은 여기서도 **그리기 람다 안에서만** 읽는다 — 돌과 같은 이유다.
                    // ⚠️ 선 두께(5f)는 **키우지 않는다** — 함께 굵어지면 커지는 동안 테두리만 도드라진다.
                    val ringScale = if (lastMove.coordinate == playEffectAt) playEffectScale.value else 1f
                    drawCircle(
                        color = pulsedRingColor,
                        radius = geometry.spacing * 0.48f * ringScale,
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

                // 착수 드래그의 되먹임(#39) — **맨 마지막에** 그려 판 위에 얹는다.
                val drag = playDrag
                if (drag != null) {
                    val touch = drag.touch
                    val dragCoordinate = coordinateFromTap(
                        touch,
                        canvasSize,
                        gameState.boardSize,
                        uxOptions.showCoordinates,
                    )
                    val stoneRadius = geometry.spacing * 0.42f
                    // ⚠️ **1배 판의 가늠돌은 돋보기와 무관하게 항상 그린다**(2026-09-09).
                    // 이것이 손을 따라 움직이는 그 돌이다 — 확대창은 조준을 돕는 덤이고,
                    // "지금 어디에 놓이는지"를 알려주는 본체는 이쪽이다. 예전에는 이 블록 전체가
                    // 확대창과 운명을 같이해서, 돋보기를 끄면 되먹임이 통째로 사라졌다.
                    // ⚠️ **'착수 확인'의 임시 돌과 같은 모습**(깜빡이는 반투명)으로 그린다(#138, 사용자
                    //   요청 *"'착수 확인'처럼"*). 두 모드에서 "여기에 놓인다"가 같은 말로 읽혀야 한다.
                    //   이미 임시 돌이 있는 자리면 겹쳐 그리지 않는다 — 겹치면 그 칸만 진해 보인다.
                    if (dragCoordinate != null && dragCoordinate != tentativeMove) {
                        drawGhostStone(
                            center = geometry.pointFor(dragCoordinate),
                            radius = stoneRadius,
                            stone = gameState.nextPlayer,
                            alpha = ghostAlpha,
                        )
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

            // ⚠️ **`!isGameEnded`가 이 오버레이의 핵심 조건이다**(백로그 #176, 2026-09-18 사용자 제보).
            //
            // `hasConsecutivePasses()`는 **양통과로 끝난 판에서 종료 뒤에도 계속 참**이다 — 수순이
            // 그대로 남아 있기 때문이다. 그래서 이 조건만 보면, 종료 화면에서 `대국 시작`을 눌러
            // **새 대국용으로 엔진이 깨어나는 동안** *"계가를 위해 준비 중입니다"* 가 뜬다.
            // 위의 `Preparing` 오버레이("새 대국을 위해 준비 중입니다")와 **둘이 동시에 떠서**
            // 나중에 그려지는 이쪽이 덮었다.
            //
            // 실측으로 두 구간이 갈린다(2026-09-18, Pixel 8):
            //   · 진짜 계가 중 — `busy=true ended=false passes=true`
            //   · 새 대국 준비 — `busy=true ended=true  passes=true ind=Preparing`
            // ⚠️ **종국 플래그는 계가가 *끝난 뒤*에 켜진다** — 그래서 `!isGameEnded`가 계가 구간을
            //   가리지 않는다. 그 순서가 바뀌면 이 오버레이가 통째로 사라지니, 순서를 건드리는
            //   사람은 여기를 함께 볼 것.
            if (gameState.hasConsecutivePasses() && isEngineBusy && !isGameEnded) {
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

/** 지연 착수가 시작될 때의 투명도. 여기서 1.0까지 진해지며 확정된다(#144). */
private const val PendingPlayMinAlpha = 0.35f
