package com.worksoc.goaicoach.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.worksoc.goaicoach.shared.BoardSize

@Composable
internal fun DirectPlayRecommendationDialog(
    boardSize: BoardSize,
    isDirectPlayEnabled: Boolean,
    onConfirm: (Boolean) -> Unit
) {
    val strings = LocalUiStrings.current
    var lastAskedSize by remember { mutableStateOf<BoardSize?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var recommendOn by remember { mutableStateOf(true) }

    LaunchedEffect(boardSize) {
        if (lastAskedSize != boardSize) {
            if (boardSize == BoardSize.Nine && !isDirectPlayEnabled) {
                recommendOn = true
                showDialog = true
                lastAskedSize = boardSize
            } else if (boardSize == BoardSize.Nineteen && isDirectPlayEnabled) {
                recommendOn = false
                showDialog = true
                lastAskedSize = boardSize
            } else {
                lastAskedSize = boardSize
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = strings.recommendedPrefix,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (recommendOn) {
                        strings.recommendDirectPlayOnPrompt
                    } else {
                        strings.recommendDirectPlayOffPrompt
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(recommendOn)
                        showDialog = false
                    }
                ) {
                    Text(strings.yes)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                    }
                ) {
                    Text(strings.no)
                }
            }
        )
    }
}
