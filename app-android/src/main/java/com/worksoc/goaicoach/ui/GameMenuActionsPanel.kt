package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Ruleset

@Composable
internal fun ScoringAndBoardSettingsPanel(
    ruleset: Ruleset,
    boardSize: BoardSize,
    canChangeRuleset: Boolean,
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
            SettingDropdownRow(
                label = strings.scoringRule,
                selectedText = strings.rulesetLabel(ruleset),
                enabled = canChangeRuleset,
                options = Ruleset.entries,
                optionLabel = { rule -> strings.rulesetLabel(rule) },
                onSelected = onRulesetChange,
            )
            SettingDropdownRow(
                label = strings.boardSize,
                selectedText = "${boardSize.value}x${boardSize.value}",
                enabled = canChangeRuleset,
                options = listOf(BoardSize.Nine, BoardSize.Thirteen, BoardSize.Nineteen),
                optionLabel = { "${it.value}x${it.value}" },
                onSelected = onBoardSizeChange,
            )
        }
    }
}

@Composable
internal fun GameMenuActionsPanel(
    onCopyLog: () -> Unit,
    onBenchmark: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
        }
    }
}
