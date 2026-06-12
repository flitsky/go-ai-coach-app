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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.turnStatus
import com.worksoc.goaicoach.shared.StoneColor

@Composable
internal fun EngineResponsePanel(
    nextPlayer: StoneColor,
    moveCount: Int,
    capturedByBlack: Int,
    capturedByWhite: Int,
    turnTimeText: String,
    lastMoveText: String,
    isEngineBusy: Boolean,
    playerSetup: PlayerSetup,
    engineMessage: String,
    candidateText: String,
    scoreText: String,
    moveReviewText: String,
) {
    val analysisLogScrollState = rememberScrollState()

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(turnStatus(nextPlayer, isEngineBusy, playerSetup), fontWeight = FontWeight.SemiBold)
                Text("Moves: $moveCount", color = MaterialTheme.colorScheme.secondary)
            }

            Text(
                text = "Last: $lastMoveText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            Text(
                text = "Captured by Black: $capturedByBlack / White: $capturedByWhite",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            Text(
                text = turnTimeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                fontFamily = FontFamily.Monospace,
            )

            Text(
                text = engineMessage,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )

            Text(
                text = scoreText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                fontFamily = FontFamily.Monospace,
            )

            Text(
                text = moveReviewText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                fontFamily = FontFamily.Monospace,
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = AnalysisLogMaxHeight),
                shape = RoundedCornerShape(6.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ) {
                Text(
                    text = candidateText,
                    modifier = Modifier
                        .verticalScroll(analysisLogScrollState)
                        .padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private val AnalysisLogMaxHeight = 180.dp
