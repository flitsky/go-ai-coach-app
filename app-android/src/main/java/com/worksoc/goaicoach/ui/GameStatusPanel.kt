package com.worksoc.goaicoach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor

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

        // 중앙: [착수] 버튼. 예전에는 이 위에 `수순 N수`가 있었는데 **헤더로 올라갔다**(#35) —
        // 대국 내내 보는 값이 버튼 부속처럼 보였고, 무엇보다 이 자리는 착수 모드 스위치가
        // 쓸 자리다(#37).
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = {
                    tentativeMove?.let {
                        onEvent(GameUiEvent.SubmitMove(Move.Play(screenState.gameState.nextPlayer, it)))
                    }
                },
                enabled = tentativeMove != null && !screenState.isGameEnded,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFECEFF1),
                    disabledContentColor = Color(0xFFB0BEC5)
                ),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(strings.playMove, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
