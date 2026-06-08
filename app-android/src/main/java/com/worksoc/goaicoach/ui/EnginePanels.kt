package com.worksoc.goaicoach.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.match.AiEngineChoice
import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.match.turnStatus
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor

@Composable
internal fun PlayerSetupPanel(
    playerSetup: PlayerSetup,
    engineName: String,
    enabled: Boolean,
    onPlayerSetupChange: (PlayerSetup) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Player Setup", fontWeight = FontWeight.SemiBold)
            PlayerSetupSideRow(
                color = StoneColor.Black,
                side = playerSetup.black,
                engineName = engineName,
                enabled = enabled,
                onSideChange = { side -> onPlayerSetupChange(playerSetup.updateSide(StoneColor.Black, side)) },
            )
            PlayerSetupSideRow(
                color = StoneColor.White,
                side = playerSetup.white,
                engineName = engineName,
                enabled = enabled,
                onSideChange = { side -> onPlayerSetupChange(playerSetup.updateSide(StoneColor.White, side)) },
            )
            Text(
                text = playerSetup.summary(engineName),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PlayerSetupSideRow(
    color: StoneColor,
    side: SidePlayerSetup,
    engineName: String,
    enabled: Boolean,
    onSideChange: (SidePlayerSetup) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (color == StoneColor.Black) "흑" else "백",
                modifier = Modifier.weight(0.38f),
                fontWeight = FontWeight.SemiBold,
            )
            SetupDropdown(
                selectedText = side.controller.label,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                options = SeatController.entries,
                optionLabel = { it.label },
                onSelected = { controller ->
                    onSideChange(side.copy(controller = controller))
                },
            )
            when (side.controller) {
                SeatController.Human -> {
                    SetupDropdown(
                        selectedText = side.humanGameType.label,
                        enabled = enabled,
                        modifier = Modifier.weight(1.08f),
                        options = HumanGameType.entries,
                        optionLabel = { it.label },
                        onSelected = { gameType ->
                            onSideChange(side.copy(humanGameType = gameType))
                        },
                    )
                    SetupStaticBox(
                        text = "-",
                        modifier = Modifier.weight(0.8f),
                    )
                }

                SeatController.Ai -> {
                    SetupDropdown(
                        selectedText = side.playLevel.group.label,
                        enabled = enabled,
                        modifier = Modifier.weight(1.08f),
                        options = PlayLevelGroup.entries,
                        optionLabel = { it.label },
                        onSelected = { group ->
                            onSideChange(side.copy(playLevel = side.playLevel.withGroup(group)))
                        },
                    )
                    SetupDropdown(
                        selectedText = "${side.playLevel.safeLevel}단계",
                        enabled = enabled,
                        modifier = Modifier.weight(0.8f),
                        options = (1..side.playLevel.group.maxLevel).toList(),
                        optionLabel = { "${it}단계" },
                        onSelected = { level ->
                            onSideChange(side.copy(playLevel = side.playLevel.withLevel(level)))
                        },
                    )
                }
            }
        }
        if (side.controller == SeatController.Ai) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "엔진",
                    modifier = Modifier.weight(0.38f),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                SetupDropdown(
                    selectedText = side.aiEngine.label.ifBlank { engineName },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    options = AiEngineChoice.entries,
                    optionLabel = { it.label },
                    onSelected = { engineChoice ->
                        onSideChange(side.copy(aiEngine = engineChoice))
                    },
                )
                Text(
                    text = "${side.playLevel.group.difficulty.label} / ${side.playLevel.group.visits} visits",
                    modifier = Modifier.weight(1.88f),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun <T> SetupDropdown(
    selectedText: String,
    enabled: Boolean,
    modifier: Modifier,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selectedText, maxLines = 1)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun SetupStaticBox(
    text: String,
    modifier: Modifier,
) {
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = modifier,
    ) {
        Text(text, maxLines = 1)
    }
}

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
