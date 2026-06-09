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
