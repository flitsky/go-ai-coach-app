package com.worksoc.goaicoach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor

@Composable
internal fun EngineResponsePanel(
    screenState: GameScreenState,
    blackTotalMillis: Long,
    whiteTotalMillis: Long,
    turnStatusText: String,
    moveCount: Int,
    capturedByBlack: Int,
    capturedByWhite: Int,
    lastMoveText: String,
    engineMessage: String,
    candidateText: String,
    scoreText: String,
    moveReviewText: String,
) {
    val analysisDebug = buildSideAnalysisDebugState(
        screenState = screenState,
        candidateText = candidateText,
        engineMessage = engineMessage,
        moveReviewText = moveReviewText,
        sideAnalysisTexts = screenState.analysis.sideAnalysisTexts,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 상단 타이틀 행 (현재 차례 및 진행 수순)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(turnStatusText, fontWeight = FontWeight.SemiBold)
                Text("수순: ${moveCount}수", color = MaterialTheme.colorScheme.secondary)
            }

            // 최근 착수 표시
            if (lastMoveText.isNotEmpty()) {
                Text(
                    text = "최근 착수: $lastMoveText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            // 2분할 흑/백 진영 정보 레이아웃
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 흑진영 카드
                val isBlackTurn = screenState.gameState.nextPlayer == StoneColor.Black && !screenState.isGameEnded
                val blackBgColor = if (isBlackTurn) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
                val blackBorderStroke = if (isBlackTurn) BorderStroke(1.5.dp, Color(0xFF2F6B4F)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

                Surface(
                    modifier = Modifier.weight(1f),
                    color = blackBgColor,
                    border = blackBorderStroke,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = sideLabel(screenState.playerSetup.black, StoneColor.Black),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F1F1F)
                        )
                        Text(
                            text = "시간: ${formatMillis(blackTotalMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "사석: ${capturedByBlack}개",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val lastMovePlayer = screenState.gameState.moves.lastOrNull()?.player
                        if (lastMovePlayer == StoneColor.Black && moveReviewText.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = moveReviewText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2F6B4F),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // 백진영 카드
                val isWhiteTurn = screenState.gameState.nextPlayer == StoneColor.White && !screenState.isGameEnded
                val whiteBgColor = if (isWhiteTurn) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
                val whiteBorderStroke = if (isWhiteTurn) BorderStroke(1.5.dp, Color(0xFF2F6B4F)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

                Surface(
                    modifier = Modifier.weight(1f),
                    color = whiteBgColor,
                    border = whiteBorderStroke,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = sideLabel(screenState.playerSetup.white, StoneColor.White),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F1F1F)
                        )
                        Text(
                            text = "시간: ${formatMillis(whiteTotalMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "사석: ${capturedByWhite}개",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val lastMovePlayer = screenState.gameState.moves.lastOrNull()?.player
                        if (lastMovePlayer == StoneColor.White && moveReviewText.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = moveReviewText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2F6B4F),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // 실시간 분석 정보 (scoreText 등)
            if (scoreText.isNotEmpty()) {
                Text(
                    text = scoreText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // 엔진 상세 메시지 (디버그성 메시지)
            if (engineMessage.isNotEmpty()) {
                Text(
                    text = engineMessage,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            if (analysisDebug.hasAnyContent) {
                Text(
                    text = "KataGo analysis",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SideAnalysisDebugCard(
                        title = "흑 분석",
                        state = analysisDebug.black,
                        modifier = Modifier.weight(1f),
                    )
                    SideAnalysisDebugCard(
                        title = "백 분석",
                        state = analysisDebug.white,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val AnalysisLogMaxHeight = 180.dp

@Composable
private fun SideAnalysisDebugCard(
    title: String,
    state: SideAnalysisDebugText,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier.heightIn(max = AnalysisLogMaxHeight),
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.selectedMoveText != null) {
                Text(
                    text = "선택: ${state.selectedMoveText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2F6B4F),
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (state.moveReviewText != null) {
                Text(
                    text = state.moveReviewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2F6B4F),
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                text = state.candidateText ?: "아직 표시할 분석 정보가 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private data class SideAnalysisDebugState(
    val black: SideAnalysisDebugText,
    val white: SideAnalysisDebugText,
) {
    val hasAnyContent: Boolean
        get() = black.hasContent || white.hasContent
}

private data class SideAnalysisDebugText(
    val candidateText: String? = null,
    val selectedMoveText: String? = null,
    val moveReviewText: String? = null,
) {
    val hasContent: Boolean
        get() = candidateText != null || selectedMoveText != null || moveReviewText != null
}

private fun buildSideAnalysisDebugState(
    screenState: GameScreenState,
    candidateText: String,
    engineMessage: String,
    moveReviewText: String,
    sideAnalysisTexts: Map<StoneColor, String>,
): SideAnalysisDebugState {
    val candidateOwner = inferAnalysisOwner(candidateText)
    val analysisTextsByPlayer = sideAnalysisTexts
        .filterValues { text -> text.isNotBlank() }
        .toMutableMap()
    if (candidateOwner != null && candidateText.isNotBlank()) {
        analysisTextsByPlayer[candidateOwner] = candidateText
    }
    val selectedMoveText = extractAiSelectedLine(
        listOf(engineMessage, candidateText).joinToString("\n"),
    )
    val selectedOwner = selectedMoveText?.let {
        candidateOwner ?: lastMovePlayer(screenState)
    }
    val reviewOwner = moveReviewText
        .takeIf { it.isNotBlank() }
        ?.let { lastMovePlayer(screenState) }

    fun stateFor(player: StoneColor): SideAnalysisDebugText {
        val sideText = analysisTextsByPlayer[player]
        return SideAnalysisDebugText(
            candidateText = sideText,
            selectedMoveText = extractAiSelectedLine(sideText.orEmpty())
                ?: selectedMoveText.takeIf { selectedOwner == player },
            moveReviewText = moveReviewText
                .takeIf { it.isNotBlank() && reviewOwner == player },
        )
    }

    return SideAnalysisDebugState(
        black = stateFor(StoneColor.Black),
        white = stateFor(StoneColor.White),
    )
}

private fun inferAnalysisOwner(candidateText: String): StoneColor? {
    val explicit = Regex("""\bfor\s+(Black|White)\b""")
        .find(candidateText)
        ?.groupValues
        ?.getOrNull(1)
        ?.toStoneColorOrNull()
    if (explicit != null) return explicit

    return Regex("""(?m)^\d+\.\s+(Black|White)\b""")
        .find(candidateText)
        ?.groupValues
        ?.getOrNull(1)
        ?.toStoneColorOrNull()
}

private fun extractAiSelectedLine(engineMessage: String): String? =
    engineMessage
        .lines()
        .firstOrNull { line -> line.contains("AI selected") }
        ?.trim()

private fun lastMovePlayer(screenState: GameScreenState): StoneColor? =
    when (val lastMove = screenState.gameState.moves.lastOrNull()) {
        is Move.Play -> lastMove.player
        is Move.Pass -> lastMove.player
        is Move.Resign -> lastMove.player
        null -> null
    }

private fun String.toStoneColorOrNull(): StoneColor? =
    when (this) {
        "Black" -> StoneColor.Black
        "White" -> StoneColor.White
        else -> null
    }

private fun formatMillis(millis: Long): String {
    val seconds = (millis + 50L) / 1000L
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

private fun sideLabel(setup: SidePlayerSetup, color: StoneColor): String {
    val colorPrefix = if (color == StoneColor.Black) "흑" else "백"
    val controllerLabel = when (setup.controller) {
        SeatController.Human -> "유저"
        SeatController.Ai -> "AI"
    }
    return "$colorPrefix ($controllerLabel)"
}
