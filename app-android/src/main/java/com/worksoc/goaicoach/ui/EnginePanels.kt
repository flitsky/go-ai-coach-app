package com.worksoc.goaicoach.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.turnStatus
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor

@Composable
internal fun GameMenuActionsPanel(
    mode: MatchMode,
    ruleset: Ruleset,
    canStartNew: Boolean,
    canChangeRuleset: Boolean,
    onNewGame: () -> Unit,
    onCopyLog: () -> Unit,
    onRulesetChange: (Ruleset) -> Unit,
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
            Text("Game", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onNewGame,
                    enabled = canStartNew,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("New")
                }
                OutlinedButton(
                    onClick = onCopyLog,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Copy Log")
                }
            }
            Text(
                text = "Current mode: ${mode.label}",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Scoring rule: Area | Territory", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ruleset == Ruleset.Chinese) {
                    Button(
                        onClick = { onRulesetChange(Ruleset.Chinese) },
                        enabled = canChangeRuleset,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Area")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onRulesetChange(Ruleset.Chinese) },
                        enabled = canChangeRuleset,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Area")
                    }
                }
                if (ruleset == Ruleset.Japanese) {
                    Button(
                        onClick = { onRulesetChange(Ruleset.Japanese) },
                        enabled = canChangeRuleset,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Territory")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onRulesetChange(Ruleset.Japanese) },
                        enabled = canChangeRuleset,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Territory")
                    }
                }
            }
            Text(
                text = "Scoring rule: ${ruleset.scoringLabel}",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun EngineResponsePanel(
    nextPlayer: StoneColor,
    moveCount: Int,
    capturedByBlack: Int,
    capturedByWhite: Int,
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
