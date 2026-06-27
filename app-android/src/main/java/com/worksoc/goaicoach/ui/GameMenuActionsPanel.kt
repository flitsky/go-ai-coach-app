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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.BoardSize

@Composable
internal fun GameMenuActionsPanel(
    mode: MatchMode,
    ruleset: Ruleset,
    boardSize: BoardSize,
    canStartNew: Boolean,
    canChangeRuleset: Boolean,
    onNewGame: () -> Unit,
    onCopyLog: () -> Unit,
    onBenchmark: () -> Unit,
    onRulesetChange: (Ruleset) -> Unit,
    onBoardSizeChange: (BoardSize) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(strings.gameSection, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onNewGame,
                    enabled = canStartNew,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.newGame)
                }
                OutlinedButton(
                    onClick = onCopyLog,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.copyLog)
                }
                OutlinedButton(
                    onClick = onBenchmark,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.benchmark)
                }
            }
            Text(
                text = "${strings.currentModePrefix}: ${strings.matchModeLabel(mode)}",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(strings.scoringRule, fontWeight = FontWeight.SemiBold)
            SetupDropdown(
                selectedText = strings.rulesetLabel(ruleset),
                enabled = canChangeRuleset,
                modifier = Modifier.fillMaxWidth(),
                options = Ruleset.entries,
                optionLabel = { rule -> strings.rulesetLabel(rule) },
                onSelected = onRulesetChange,
            )
            Text(
                text = "${strings.scoringRule}: ${strings.rulesetLabel(ruleset)}",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(strings.boardSize, fontWeight = FontWeight.SemiBold)
            SetupDropdown(
                selectedText = "${boardSize.value}x${boardSize.value}",
                enabled = canChangeRuleset,
                modifier = Modifier.fillMaxWidth(),
                options = listOf(BoardSize.Nine, BoardSize.Thirteen, BoardSize.Nineteen),
                optionLabel = { "${it.value}x${it.value}" },
                onSelected = onBoardSizeChange,
            )
        }
    }
}
