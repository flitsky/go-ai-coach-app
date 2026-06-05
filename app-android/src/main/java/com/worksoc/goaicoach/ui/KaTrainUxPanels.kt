package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun KaTrainUxOptionsPanel(
    options: KaTrainUxOptions,
    onOptionsChange: (KaTrainUxOptions) -> Unit,
) {
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
            Text("KaTrain UX", fontWeight = FontWeight.SemiBold)
            OptionSwitchRow("Coords", options.showCoordinates) {
                onOptionsChange(options.copy(showCoordinates = it))
            }
            OptionSwitchRow("Move nums", options.showMoveNumbers) {
                onOptionsChange(options.copy(showMoveNumbers = it))
            }
            OptionSwitchRow("Last ring", options.showLastMoveRing) {
                onOptionsChange(options.copy(showLastMoveRing = it))
            }
            OptionSwitchRow("Candidate list", options.showCandidateList) {
                onOptionsChange(options.copy(showCandidateList = it))
            }
            OptionSwitchRow("Spot legend", options.showHintLegend) {
                onOptionsChange(options.copy(showHintLegend = it))
            }
            OptionSwitchRow("Engine badge", options.showEngineStatusBadge) {
                onOptionsChange(options.copy(showEngineStatusBadge = it))
            }
            OptionSwitchRow("Game strip", options.showGameStatusStrip) {
                onOptionsChange(options.copy(showGameStatusStrip = it))
            }
            OptionSwitchRow("Ownership", options.showOwnershipOverlay) {
                onOptionsChange(options.copy(showOwnershipOverlay = it))
            }
        }
    }
}

@Composable
private fun OptionSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun EngineStatusBadge(
    engineName: String,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    engineDiagnostic: String,
) {
    val tone = when {
        isEngineBusy -> StatusTone("Busy", Color(0xFFF9A825))
        isEngineReady -> StatusTone("Ready", Color(0xFF2E7D32))
        else -> StatusTone("Down", Color(0xFFC62828))
    }
    val engineType = if (engineDiagnostic.contains("local process", ignoreCase = true)) {
        "KataGo"
    } else if (engineDiagnostic.contains("stub", ignoreCase = true)) {
        "Stub"
    } else {
        engineName
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(tone.color, CircleShape),
            )
            Text("$engineType ${tone.label}", fontWeight = FontWeight.SemiBold)
            Text(
                text = if (isEngineBusy) "analysis or move in progress" else "engine boundary isolated",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun GameStatusStrip(
    nextPlayer: StoneColor,
    moveCount: Int,
    capturedByBlack: Int,
    capturedByWhite: Int,
    lastMoveText: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Turn ${nextPlayer.label}", fontWeight = FontWeight.SemiBold)
                Text("Moves $moveCount", color = MaterialTheme.colorScheme.secondary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Captures B $capturedByBlack / W $capturedByWhite")
                Text("Last $lastMoveText", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
internal fun HintLegendPanel() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDot(Color(0xFF2E7D32), "good")
            LegendDot(Color(0xFFF9A825), "ok")
            LegendDot(Color(0xFFC62828), "bad")
            LegendDot(Color(0xFF607D8B), "policy")
        }
    }
}

@Composable
private fun LegendDot(
    color: Color,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun CandidateMovesPanel(
    candidates: List<CandidateMove>,
    boardSize: BoardSize,
) {
    if (candidates.isEmpty()) {
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Candidates", fontWeight = FontWeight.SemiBold)
            candidates.forEachIndexed { index, candidate ->
                CandidateMoveRow(index + 1, candidate, boardSize)
            }
        }
    }
}

@Composable
private fun CandidateMoveRow(
    rank: Int,
    candidate: CandidateMove,
    boardSize: BoardSize,
) {
    val tone = candidateToneFor(candidate.pointLoss)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(tone, CircleShape),
        )
        Text(
            text = "$rank. ${candidate.move.describeCompact(boardSize)}",
            modifier = Modifier.weight(1.1f),
            fontWeight = if (rank == 1) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = candidate.toCompactStats(),
            modifier = Modifier.weight(2f),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private data class StatusTone(
    val label: String,
    val color: Color,
)

private fun candidateToneFor(pointLoss: Double?): Color =
    when (moveReviewToneFor(pointLoss)) {
        MoveReviewTone.Good -> Color(0xFF2E7D32)
        MoveReviewTone.Inaccuracy -> Color(0xFFF9A825)
        MoveReviewTone.Mistake -> Color(0xFFC62828)
        MoveReviewTone.Unknown -> Color(0xFF607D8B)
    }

private fun CandidateMove.toCompactStats(): String =
    buildList {
        pointLoss?.let { add("loss ${it.formatOneDecimal()}") }
        playerPerspectiveScoreLead()?.let { add("lead ${it.formatSignedOneDecimal()}") }
        visits?.let { add("v $it") }
        policyPrior?.let { add("p ${(it * 100).roundToInt()}%") }
    }.joinToString(" / ").ifEmpty { note ?: "no stats" }

private fun CandidateMove.playerPerspectiveScoreLead(): Double? {
    val player = when (val candidateMove = move) {
        is Move.Play -> candidateMove.player
        is Move.Pass -> candidateMove.player
        is Move.Resign -> candidateMove.player
    }
    return scoreLead?.let { scoreLead ->
        when (player) {
            StoneColor.Black -> -scoreLead
            StoneColor.White -> scoreLead
        }
    }
}

private fun Move.describeCompact(boardSize: BoardSize): String =
    when (this) {
        is Move.Play -> coordinate.label(boardSize)
        is Move.Pass -> "pass"
        is Move.Resign -> "resign"
    }

private fun Double.formatOneDecimal(): String =
    ((this * 10).roundToInt() / 10.0).toString()

private fun Double.formatSignedOneDecimal(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    val normalized = if (abs(rounded) < 0.05) 0.0 else rounded
    return if (normalized > 0.0) "+$normalized" else normalized.toString()
}
