package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor

@Composable
internal fun EngineResponsePanel(
    screenState: GameScreenState,
    turnStatusText: String,
    moveCount: Int,
    engineMessage: String,
    candidateText: String,
    moveReviewText: String,
) {
    val strings = LocalUiStrings.current
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
                Text("${strings.moveCountPrefix}: $moveCount${strings.moveCountSuffix}", color = MaterialTheme.colorScheme.secondary)
            }

            if (analysisDebug.hasAnyContent) {
                Text(
                    text = strings.kataGoAnalysis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SideAnalysisDebugCard(
                        title = strings.blackAnalysis,
                        strings = strings,
                        state = analysisDebug.black,
                        modifier = Modifier.weight(1f),
                    )
                    SideAnalysisDebugCard(
                        title = strings.whiteAnalysis,
                        strings = strings,
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
    strings: UiStrings,
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
                    text = "${strings.selected}: ${state.selectedMoveText}",
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
                text = state.candidateText ?: strings.noAnalysisInfo,
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
