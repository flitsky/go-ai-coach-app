package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.StoneColor

@Composable
internal fun KaTrainUxMenuButton(
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
) {
    val strings = LocalUiStrings.current
    OutlinedButton(
        onClick = { onMenuExpandedChange(!menuExpanded) },
    ) {
        Text(if (menuExpanded) strings.close else "\u2630")
    }
}

@Composable
internal fun KaTrainUxMenuPanel(
    options: KaTrainUxOptions,
    onOptionsChange: (KaTrainUxOptions) -> Unit,
) {
    val strings = LocalUiStrings.current
    val columnGap = 12.dp
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                OptionSwitchCell(
                    label = strings.directPlay,
                    checked = options.isDirectPlayEnabled,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(isDirectPlayEnabled = it)) },
                )
                Spacer(modifier = Modifier.width(columnGap))
                OptionSwitchCell(
                    label = strings.lastMoveRing,
                    checked = options.showLastMoveRing,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(showLastMoveRing = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                OptionSwitchCell(
                    label = strings.moveNumbers,
                    checked = options.showMoveNumbers,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(showMoveNumbers = it)) },
                )
                Spacer(modifier = Modifier.width(columnGap))
                OptionSwitchCell(
                    label = strings.coordinates,
                    checked = options.showCoordinates,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(showCoordinates = it)) },
                )
            }
        }
    }
}

@Composable
private fun OptionSwitchCell(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
            maxLines = 2,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
    val strings = LocalUiStrings.current
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
                Text("${strings.turnPrefix} ${strings.colorLabel(nextPlayer)}", fontWeight = FontWeight.SemiBold)
                Text("${strings.movesPrefix} $moveCount", color = MaterialTheme.colorScheme.secondary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${strings.capturesPrefix} ${strings.colorLabel(StoneColor.Black)} $capturedByBlack / ${strings.colorLabel(StoneColor.White)} $capturedByWhite")
                Text("${strings.lastPrefix} $lastMoveText", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
