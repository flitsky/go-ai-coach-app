package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.turnStatus
import com.worksoc.goaicoach.shared.DifficultyProfile
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.StoneColor

@Composable
internal fun ModePanel(
    mode: MatchMode,
    engineName: String,
    engineDiagnostic: String,
    canStartAi: Boolean,
    canStartLocal: Boolean,
    onAiMode: () -> Unit,
    onLocalTwoPlayerMode: () -> Unit,
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
            Text("Mode", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAiMode,
                    enabled = mode != MatchMode.HumanVsAi && canStartAi,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("AI")
                }
                OutlinedButton(
                    onClick = onLocalTwoPlayerMode,
                    enabled = mode != MatchMode.LocalTwoPlayer && canStartLocal,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("2P")
                }
            }
            Text(
                text = when (mode) {
                    MatchMode.HumanVsAi -> "Black: human / White: $engineName"
                    MatchMode.LocalTwoPlayer -> "Black and White are both local players"
                },
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = engineDiagnostic,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
internal fun EngineTuningPanel(
    profile: EngineProfile,
    enabled: Boolean,
    onDifficultyChange: (DifficultyProfile) -> Unit,
    onVisitsChange: (Int) -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Engine", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "time ${profile.analysisLimit.timeMillis ?: 0}ms",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onDifficultyChange(profile.difficulty.previous()) },
                    enabled = enabled && profile.difficulty != DifficultyProfile.entries.first(),
                    modifier = Modifier.weight(0.7f),
                ) {
                    Text("-")
                }
                Text(
                    text = profile.difficulty.label,
                    modifier = Modifier.weight(2f),
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(
                    onClick = { onDifficultyChange(profile.difficulty.next()) },
                    enabled = enabled && profile.difficulty != DifficultyProfile.entries.last(),
                    modifier = Modifier.weight(0.7f),
                ) {
                    Text("+")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onVisitsChange(previousVisits(profile.analysisLimit.visits)) },
                    enabled = enabled && profile.analysisLimit.visits > VisitOptions.first(),
                    modifier = Modifier.weight(0.7f),
                ) {
                    Text("-")
                }
                Text(
                    text = "Visits ${profile.analysisLimit.visits}",
                    modifier = Modifier.weight(2f),
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(
                    onClick = { onVisitsChange(nextVisits(profile.analysisLimit.visits)) },
                    enabled = enabled && profile.analysisLimit.visits < VisitOptions.last(),
                    modifier = Modifier.weight(0.7f),
                ) {
                    Text("+")
                }
            }
        }
    }
}

@Composable
internal fun HintControlsPanel(
    hintEnabled: Boolean,
    hintCount: Int,
    enabled: Boolean,
    onHintEnabledChange: (Boolean) -> Unit,
    onHintCountChange: (Int) -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hints", fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = hintEnabled,
                    enabled = enabled,
                    onCheckedChange = onHintEnabledChange,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onHintCountChange(previousHintCount(hintCount)) },
                    enabled = enabled && hintCount > HintCountOptions.first(),
                    modifier = Modifier.weight(0.7f),
                ) {
                    Text("-")
                }
                Text(
                    text = "N $hintCount",
                    modifier = Modifier.weight(2f),
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(
                    onClick = { onHintCountChange(nextHintCount(hintCount)) },
                    enabled = enabled && hintCount < HintCountOptions.last(),
                    modifier = Modifier.weight(0.7f),
                ) {
                    Text("+")
                }
            }
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
    mode: MatchMode,
    engineMessage: String,
    candidateText: String,
    scoreText: String,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(turnStatus(nextPlayer, isEngineBusy, mode), fontWeight = FontWeight.SemiBold)
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
                text = candidateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private val VisitOptions = listOf(16, 64, 160, 400, 1_000)
private val HintCountOptions = listOf(1, 2, 3, 4, 5)

private fun previousVisits(current: Int): Int =
    VisitOptions.lastOrNull { it < current } ?: VisitOptions.first()

private fun nextVisits(current: Int): Int =
    VisitOptions.firstOrNull { it > current } ?: VisitOptions.last()

private fun previousHintCount(current: Int): Int =
    HintCountOptions.lastOrNull { it < current } ?: HintCountOptions.first()

private fun nextHintCount(current: Int): Int =
    HintCountOptions.firstOrNull { it > current } ?: HintCountOptions.last()
