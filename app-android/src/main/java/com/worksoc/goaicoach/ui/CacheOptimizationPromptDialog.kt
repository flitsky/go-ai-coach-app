package com.worksoc.goaicoach.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun CacheOptimizationPromptDialog(
    title: String,
    message: String,
    strings: UiStrings,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    // ⚠️ 이 팝업이 떠 있는 동안 첫돌이 가이드를 **기록하지 않는다** — 뒤에 깔린 채 "봤음"으로
    //   소진되는 것을 막는다(그 사유는 `GuideBlockingOverlays`의 KDoc).
    GuideBlockingOverlays.TrackWhileShown()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(strings.analyze)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.later)
            }
        },
    )
}
