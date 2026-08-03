package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.score.FinalScoreJudgement

internal fun FinalScoreJudgement.dialogKey(moveCount: Int): String =
    listOf(
        moveCount.toString(),
        winner?.name.orEmpty(),
        margin?.toString().orEmpty(),
        ruleset.name,
        isEstimatedDisplay.toString(),
        removedBlack.toString(),
        removedWhite.toString(),
    ).joinToString("|")

@Composable
internal fun FinalJudgementDialog(
    judgement: FinalScoreJudgement,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onReview: () -> Unit,
    onNewGame: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.finalJudgementTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(judgement.resultText(strings))
                Text(judgement.scoringRuleLine(strings))
                Text(judgement.removedStonesLine(strings))
                judgement.blackLine(strings)?.let { Text(it) }
                judgement.whiteLine(strings)?.let { Text(it) }
                judgement.note(strings)?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onReview) {
                Text(strings.reviewJudgement)
            }
        },
        dismissButton = {
            TextButton(onClick = onNewGame) {
                Text(strings.newGameAction)
            }
        },
    )
}
