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
        // ⚠️ 이 팝업이 떠 있는 동안 첫돌이 가이드를 **기록하지 않는다** — 뒤에 깔린 채 "봤음"으로
        //   소진되는 것을 막는다(그 사유는 `GuideBlockingOverlays`의 KDoc).
        GuideBlockingOverlays.TrackWhileShown()
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
