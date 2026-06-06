package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
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
import com.worksoc.goaicoach.shared.StoneColor

@Composable
internal fun KaTrainUxMenuButton(
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
) {
    OutlinedButton(
        onClick = { onMenuExpandedChange(!menuExpanded) },
    ) {
        Text(if (menuExpanded) "Close" else "\u2630")
    }
}

@Composable
internal fun KaTrainUxMenuPanel(
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
            Text("Display menu", fontWeight = FontWeight.SemiBold)
            OptionSwitchRow("Coords", options.showCoordinates) {
                onOptionsChange(options.copy(showCoordinates = it))
            }
            OptionSwitchRow("Move nums", options.showMoveNumbers) {
                onOptionsChange(options.copy(showMoveNumbers = it))
            }
            OptionSwitchRow("Last ring", options.showLastMoveRing) {
                onOptionsChange(options.copy(showLastMoveRing = it))
            }
            OptionSwitchRow("Engine badge", options.showEngineStatusBadge) {
                onOptionsChange(options.copy(showEngineStatusBadge = it))
            }
            OptionSwitchRow("Ownership", options.showOwnershipOverlay) {
                onOptionsChange(options.copy(showOwnershipOverlay = it))
            }
        }
    }
}

@Composable
internal fun KaTrainUxQuickOptionsPanel(
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
            Text("Quick display", fontWeight = FontWeight.SemiBold)
            QuickOptionRow {
                OptionSwitchTile(
                    label = "Score graph",
                    checked = options.showScoreGraph,
                    onCheckedChange = {
                        onOptionsChange(options.copy(showScoreGraph = it))
                    },
                    modifier = Modifier.weight(1f),
                )
                OptionSwitchTile(
                    label = "Game strip",
                    checked = options.showGameStatusStrip,
                    onCheckedChange = {
                        onOptionsChange(options.copy(showGameStatusStrip = it))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuickOptionRow(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
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
private fun OptionSwitchTile(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
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

private data class StatusTone(
    val label: String,
    val color: Color,
)
