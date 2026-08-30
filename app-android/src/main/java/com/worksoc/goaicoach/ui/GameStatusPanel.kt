package com.worksoc.goaicoach.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.math.abs

@Composable
internal fun GameStatusPanel(
    screenState: GameScreenState,
    turnTimeState: GameSessionTurnTimeState,
    tentativeMove: BoardCoordinate?,
    blackTotalMillis: Long,
    whiteTotalMillis: Long,
    onEvent: (GameUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val currentTurnPlayer = turnTimeState.currentTurnPlayer
    val capturedByBlack = screenState.gameState.capturedBy(StoneColor.Black)
    val capturedByWhite = screenState.gameState.capturedBy(StoneColor.White)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerSeatCard(
            modifier = Modifier.weight(1.3f),
            isActiveTurn = currentTurnPlayer == StoneColor.Black && !screenState.isGameEnded,
            stoneGlyph = "●",
            stoneGlyphColor = Color.Black,
            label = strings.sideLabel(screenState.playerSetup.black, StoneColor.Black),
            elapsedMillisText = formatMillis(blackTotalMillis),
            capturedCount = capturedByBlack,
            capturesLabel = strings.captures,
            alignEnd = false,
        )

        // 중앙: [착수 모드 스위치] + [착수] 버튼. `수순 N수`가 헤더로 올라가며 비운 자리를
        // 스위치가 받았다(#35 → #37).
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val isDirectPlay = screenState.uxOptions.isDirectPlayEnabled
            PlayModeSwitch(
                isDirectPlay = isDirectPlay,
                // **바로 착수일 때 스위치가 주인공이다**(#37 피드백, 2026-08-30). 그 모드에서는
                // 아래 `착수` 버튼이 할 일이 없으므로 크기를 서로 맞바꾼다 — 지금 누를 수 있는
                // 것이 커야 한다.
                prominent = isDirectPlay,
                enabled = !screenState.isGameEnded,
                onToggle = {
                    onEvent(
                        GameUiEvent.ChangeUxOptions(
                            screenState.uxOptions.copy(isDirectPlayEnabled = !isDirectPlay),
                        ),
                    )
                },
            )
            if (isDirectPlay) {
                // 이 모드에서 `착수` 버튼은 쓸 일이 없다. 그렇다고 **지워 버리면** 모드를
                // 바꿨을 때 버튼이 난데없이 튀어나온 것처럼 보이고, 평소처럼 **꽉 찬 회색
                // 버튼**으로 두면 "왜 안 눌리지"가 된다. 점선 자리표시는 "여기 버튼이 있고,
                // 모드를 바꾸면 살아난다"를 한 번에 말한다.
                PlayButtonGhost(label = strings.playMove)
            } else {
                Button(
                    onClick = {
                        tentativeMove?.let {
                            onEvent(GameUiEvent.SubmitMove(Move.Play(screenState.gameState.nextPlayer, it)))
                        }
                    },
                    enabled = tentativeMove != null && !screenState.isGameEnded,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PlayButtonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFECEFF1),
                        disabledContentColor = Color(0xFFB0BEC5),
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(strings.playMove, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        PlayerSeatCard(
            modifier = Modifier.weight(1.3f),
            isActiveTurn = currentTurnPlayer == StoneColor.White && !screenState.isGameEnded,
            stoneGlyph = "○",
            stoneGlyphColor = Color.Gray,
            label = strings.sideLabel(screenState.playerSetup.white, StoneColor.White),
            elapsedMillisText = formatMillis(whiteTotalMillis),
            capturedCount = capturedByWhite,
            capturesLabel = strings.captures,
            alignEnd = true,
        )
    }
}

/**
 * 흑/백 진영 정보 카드. 대국 차례일 때 프라이머리 색으로 강조된다.
 */
@Composable
private fun PlayerSeatCard(
    modifier: Modifier,
    isActiveTurn: Boolean,
    stoneGlyph: String,
    stoneGlyphColor: Color,
    label: String,
    elapsedMillisText: String,
    capturedCount: Int,
    capturesLabel: String,
    alignEnd: Boolean,
) {
    val bg = if (isActiveTurn) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val border = if (isActiveTurn) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, Color(0xFFCFD8DC))
    }
    val timeColor = if (isActiveTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    Surface(
        modifier = modifier,
        color = bg,
        border = border,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
        ) {
            // 상단: 진영 표시와 대국 시간을 분리해 좁은 카드에서도 읽기 쉽게 유지한다.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!alignEnd) {
                    Text(stoneGlyph, style = MaterialTheme.typography.titleMedium, color = stoneGlyphColor)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F1F1F),
                )
                if (alignEnd) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stoneGlyph, style = MaterialTheme.typography.titleMedium, color = stoneGlyphColor)
                }
            }
            Text(
                text = elapsedMillisText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActiveTurn) FontWeight.Bold else FontWeight.Normal,
                color = timeColor,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$capturesLabel: $capturedCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatMillis(millis: Long): String {
    val seconds = (millis + 50L) / 1000L
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

/**
 * `착수` 버튼 바로 위에서 **착수 모드를 직접 뒤집는** 스위치(#37).
 *
 * 이 설정은 원래 대국 메뉴(☰) 안에만 있었다. 대국 중 가장 자주 오가는 축인데 두 뎁스를
 * 들어가야 했고, 마침 `수순 N수`가 헤더로 올라가며(#35) 이 자리가 비었다.
 *
 * **반 바퀴 뒤집기**(사용자 지정 이펙트): `rotationX`로 카드가 뒤집히고, 90°를 넘는 순간
 * 뒷면 라벨로 갈아탄다. 뒷면은 그대로 두면 거꾸로 서므로 180°를 되돌려 세운다 —
 * 이 되돌리기가 없으면 글자가 뒤집힌 채 멈춘다.
 *
 * **위/아래 스와이프와 탭을 모두 받는다.** 사용자가 지정한 조작은 스와이프지만, 탭이 훨씬
 * 발견하기 쉽고 접근성 도구도 탭만 보낸다 — 스와이프만 받으면 못 쓰는 사용자가 생긴다.
 *
 * ⚠️ 세로 드래그를 **소비**해야 한다. 대국 화면 루트에 `verticalScroll`이 있어서
 * (`GoCoachContent.kt`), 소비하지 않으면 이 위젯 위에서 스와이프해도 화면만 스크롤된다.
 */
@Composable
private fun PlayModeSwitch(
    isDirectPlay: Boolean,
    /** 이 모드에서 스위치가 주인공인가. 참이면 `착수` 버튼과 같은 크기·무게로 그린다. */
    prominent: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val rotation by animateFloatAsState(
        targetValue = if (isDirectPlay) 0f else 180f,
        animationSpec = tween(durationMillis = 280),
        label = "PlayModeSwitchFlip",
    )
    val showingConfirmSide = rotation > 90f
    val density = LocalDensity.current.density

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationX = rotation
                // 없으면 원근이 과장돼 뒤집힐 때 카드가 화면 밖으로 튀어나온 것처럼 보인다.
                cameraDistance = 12f * density
            }
            .alpha(if (enabled) 1f else 0.5f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var dragged = 0f
                // ⚠️ **한 제스처에 한 번만** 뒤집는다. 누적만 0으로 되돌리는 방식은 부족했다 —
                // 임계(48px)의 두 배를 넘게 끌면 두 번 뒤집혀 제자리로 돌아온다(실기 확인:
                // 120px 스와이프가 무반응처럼 보였다). 그래서 이번 제스처에서 이미 뒤집었는지를
                // 따로 기억하고, 손을 떼야 다시 열린다.
                var toggledThisGesture = false
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f; toggledThisGesture = false },
                    onDragEnd = { dragged = 0f; toggledThisGesture = false },
                    onDragCancel = { dragged = 0f; toggledThisGesture = false },
                ) { change, dragAmount ->
                    change.consume()
                    dragged += dragAmount
                    if (!toggledThisGesture && abs(dragged) > SwipeToggleThresholdPx) {
                        toggledThisGesture = true
                        onToggle()
                    }
                }
            }
            .clickable(enabled = enabled, onClick = onToggle),
        shape = RoundedCornerShape(if (prominent) 24.dp else 14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (prominent) 2.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier
                .then(if (prominent) Modifier.height(PlayButtonHeight) else Modifier)
                .padding(horizontal = 6.dp, vertical = 5.dp)
                // 뒷면을 다시 세운다.
                .graphicsLayer { rotationX = if (showingConfirmSide) 180f else 0f },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "\u21C5",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (showingConfirmSide) strings.playModeConfirm else strings.playModeDirect,
                style = if (prominent) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 스와이프 한 번으로 뒤집히는 최소 이동량. 스크롤과 헷갈리지 않을 만큼은 커야 한다. */
private const val SwipeToggleThresholdPx = 48f

/** `착수` 버튼의 정상 높이. 스위치가 주인공일 때 그 높이를 물려받는다(#37). */
private val PlayButtonHeight = 48.dp

/** 점선 자리표시의 높이 — 정상 버튼보다 확실히 낮아 "지금은 쉬는 자리"로 읽힌다. */
private val GhostPlayButtonHeight = 26.dp

/**
 * 바로 착수 모드에서 `착수` 버튼이 있던 자리를 지키는 **점선 자리표시**(#37 피드백).
 *
 * 누를 수 없고, 누를 수 있는 척도 하지 않는다. 점선 테두리와 낮은 높이가 "여기 버튼이 하나
 * 있는데 지금은 쉬고 있다 — 모드를 바꾸면 살아난다"를 말한다. 아예 지우지 않는 이유는
 * 레이아웃이 출렁이고, 모드를 바꿨을 때 버튼이 난데없이 생긴 것처럼 보이기 때문이다.
 */
@Composable
private fun PlayButtonGhost(label: String) {
    val ghostColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val dashOn = with(LocalDensity.current) { 6.dp.toPx() }
    val dashOff = with(LocalDensity.current) { 5.dp.toPx() }
    val strokeWidth = with(LocalDensity.current) { 1.dp.toPx() }
    val corner = with(LocalDensity.current) { 24.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(GhostPlayButtonHeight)
            .drawBehind {
                drawRoundRect(
                    color = ghostColor,
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashOn, dashOff)),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ghostColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
