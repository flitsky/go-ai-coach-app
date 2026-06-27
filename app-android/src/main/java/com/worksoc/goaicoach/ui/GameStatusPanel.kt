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
        // 좌측: 흑진영
        val isBlackTurn = currentTurnPlayer == StoneColor.Black && !screenState.isGameEnded
        val blackBg = if (isBlackTurn) Color(0xFFE8F5E9) else Color(0xFFF7F4EC)
        val blackBorder = if (isBlackTurn) BorderStroke(1.5.dp, Color(0xFF2F6B4F)) else BorderStroke(1.dp, Color(0xFFCFD8DC))
        Surface(
            modifier = Modifier.weight(1.3f),
            color = blackBg,
            border = blackBorder,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // 상단: 진영 표시와 대국 시간을 분리해 좁은 카드에서도 읽기 쉽게 유지한다.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("●", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.sideLabel(screenState.playerSetup.black, StoneColor.Black),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1F1F),
                    )
                }
                Text(
                    text = formatMillis(blackTotalMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isBlackTurn) FontWeight.Bold else FontWeight.Normal,
                    color = if (isBlackTurn) Color(0xFF2F6B4F) else Color(0xFF78909C),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${strings.captures}: ${capturedByBlack}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF455A64)
                )
            }
        }

        // 중앙: [착수] 버튼
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${strings.moveCountPrefix} ${screenState.gameState.moves.size}${strings.moveCountSuffix}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
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
                    containerColor = Color(0xFF2F6B4F),
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

        // 우측: 백진영
        val isWhiteTurn = currentTurnPlayer == StoneColor.White && !screenState.isGameEnded
        val whiteBg = if (isWhiteTurn) Color(0xFFE8F5E9) else Color(0xFFF7F4EC)
        val whiteBorder = if (isWhiteTurn) BorderStroke(1.5.dp, Color(0xFF2F6B4F)) else BorderStroke(1.dp, Color(0xFFCFD8DC))
        Surface(
            modifier = Modifier.weight(1.3f),
            color = whiteBg,
            border = whiteBorder,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.End
            ) {
                // 상단: 진영 표시와 대국 시간을 분리해 좁은 카드에서도 읽기 쉽게 유지한다.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = strings.sideLabel(screenState.playerSetup.white, StoneColor.White),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1F1F),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("○", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                }
                Text(
                    text = formatMillis(whiteTotalMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isWhiteTurn) FontWeight.Bold else FontWeight.Normal,
                    color = if (isWhiteTurn) Color(0xFF2F6B4F) else Color(0xFF78909C),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${strings.captures}: ${capturedByWhite}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF455A64)
                )
            }
        }
    }
}

private fun formatMillis(millis: Long): String {
    val seconds = (millis + 50L) / 1000L
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}
