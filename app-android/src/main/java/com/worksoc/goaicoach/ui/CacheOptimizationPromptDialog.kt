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
