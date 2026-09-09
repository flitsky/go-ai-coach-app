package com.worksoc.goaicoach.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.shared.describe

@Composable
internal fun ResumeSavedSessionDialog(
    snapshot: SavedGameSnapshot,
    engineName: String,
    strings: UiStrings,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    // ⚠️ 이 팝업이 떠 있는 동안 첫돌이 가이드를 **기록하지 않는다** — 뒤에 깔린 채 "봤음"으로
    //   소진되는 것을 막는다(그 사유는 `GuideBlockingOverlays`의 KDoc).
    GuideBlockingOverlays.TrackWhileShown()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.resumeTitle) },
        text = {
            Text(
                text = buildString {
                    appendLine("${strings.resumeMoveCountPrefix} ${snapshot.gameState.moves.size}${strings.resumeMoveCountSuffix}")
                    appendLine(strings.resumeQuestion)
                    appendLine()
                    append("${strings.lastMovePrefix}: ")
                    append(
                        snapshot.gameState.moves
                            .lastOrNull()
                            ?.describe(snapshot.gameState.boardSize)
                            ?: strings.none,
                    )
                    appendLine()
                    append(strings.setupSummary(snapshot.playerSetup, engineName))
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text(strings.yes)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.no)
            }
        },
    )
}
