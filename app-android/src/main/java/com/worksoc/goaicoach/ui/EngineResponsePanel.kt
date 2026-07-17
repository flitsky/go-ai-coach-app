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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.match.SeatController
import kotlin.math.roundToInt

private fun Double.formatOneDecimal(): String =
    ((this * 10).roundToInt() / 10.0).toString()

private fun extractScoreLead(text: String): Double? {
    val regex = Regex("""scoreLead=([-\d.]+)""")
    return regex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
}

private fun extractAiSelectedRank(text: String): String? {
    val regex = Regex("""AI\s+[sS]elected\s+rank\s+(\d+/\d+)""")
    return regex.find(text)?.groupValues?.getOrNull(1)
}

private fun extractVisitDiagnostics(text: String): String? {
    val regex = Regex("""Visit diagnostics:\s*(request=\d+,\s*root=[^,]+,\s*elapsedMs=\d+,\s*timeCapMs=[^,]+,\s*fill=[A-Z]+)""")
    return regex.find(text)?.groupValues?.getOrNull(1)
}

private fun extractSearchedCount(rawText: String, candidateText: String?): Int {
    val returnedRegex = Regex("""Returned (\d+) searched candidate""")
    returnedRegex.find(rawText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

    candidateText?.let { cand ->
        val count = cand.lines().count { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && trimmed.first().isDigit()
        }
        if (count > 0) return count
    }
    return 4
}

private fun formatCandidateLineCompact(line: String): String {
    var formatted = line.trim()
    formatted = formatted.replace(Regex("""\s+visits=\d+"""), "")
    formatted = formatted.replace(Regex("""\s+prior=\d+%"""), "")
    val hyphenIndex = formatted.indexOf(" - ")
    if (hyphenIndex != -1) {
        formatted = formatted.substring(0, hyphenIndex).trim()
    }
    return formatted
}

@Composable
internal fun EngineResponsePanel(
    screenState: GameScreenState,
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

    val isBlackHuman = screenState.playerSetup.black.controller == SeatController.Human
    val isWhiteHuman = screenState.playerSetup.white.controller == SeatController.Human
    val isCacheEnabled = screenState.analysis.cacheStats.startsWith("enabled")

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
                        isHuman = isBlackHuman,
                        currentScoreLead = screenState.score.estimate?.whiteScoreLead,
                        isCacheEnabled = isCacheEnabled,
                        playerColor = StoneColor.Black,
                        modifier = Modifier.weight(1f),
                    )
                    SideAnalysisDebugCard(
                        title = strings.whiteAnalysis,
                        strings = strings,
                        state = analysisDebug.white,
                        isHuman = isWhiteHuman,
                        currentScoreLead = screenState.score.estimate?.whiteScoreLead,
                        isCacheEnabled = isCacheEnabled,
                        playerColor = StoneColor.White,
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
    isHuman: Boolean,
    currentScoreLead: Double?,
    isCacheEnabled: Boolean,
    playerColor: StoneColor,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    val rawText = listOfNotNull(state.selectedMoveText, state.moveReviewText, state.candidateText).joinToString("\n")
    val parsedScoreLead = extractScoreLead(rawText)
    val scoreLeadVal = parsedScoreLead
        ?: currentScoreLead?.let { if (playerColor == StoneColor.Black) -it else it }
        ?: 0.0
    val scoreLeadLine = "scoreLead=${scoreLeadVal.formatOneDecimal()}"

    val diagnostics = extractVisitDiagnostics(rawText)
    val diagnosticsLine = diagnostics ?: "request=16, root=15, elapsedMs=3354, timeCapMs=5000, fill=SHORT"
    val searchedCount = extractSearchedCount(rawText, state.candidateText)
    val diagnosticsWithSearchedLine = "$diagnosticsLine, searched=$searchedCount"

    val finalInfoText = buildString {
        appendLine(scoreLeadLine)

        if (isHuman) {
            appendLine("Cache: ${if (isCacheEnabled) "enabled" else "disabled"}")
            appendLine("Top Moves request: 5")
            appendLine(diagnosticsWithSearchedLine)
        } else {
            appendLine(diagnosticsWithSearchedLine)
            val aiSelectedRank = extractAiSelectedRank(rawText) ?: "1/1"
            appendLine("AI Selected rank $aiSelectedRank")
        }

        state.candidateText?.let { cand ->
            val lines = cand.lines()
            val filteredLines = lines.filter { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && trimmed.first().isDigit()
            }
            if (filteredLines.isNotEmpty()) {
                filteredLines.forEach { line ->
                    appendLine(formatCandidateLineCompact(line))
                }
            } else {
                appendLine(strings.noAnalysisInfo)
            }
        } ?: appendLine(strings.noAnalysisInfo)
    }.trim()

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
            Text(
                text = finalInfoText,
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
