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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.match.SeatController

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
