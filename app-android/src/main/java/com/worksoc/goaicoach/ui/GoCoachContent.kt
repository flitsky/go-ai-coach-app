package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.match.summary
import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.presentation.shouldCollapseMenuAfterEvent
import com.worksoc.goaicoach.shared.describe

@Composable
internal fun GoCoachContent(
    screenState: GameScreenState,
    isDisplayMenuExpanded: Boolean,
    onDisplayMenuExpandedChange: (Boolean) -> Unit,
    onScoreGraphExpandedChange: (Boolean) -> Unit,
    onEvent: (GameUiEvent) -> Unit,
) {
    val savedSessionToPrompt = screenState.resumePrompt?.snapshot
    val onMenuEvent: (GameUiEvent) -> Unit = { event ->
        onEvent(event)
        if (shouldCollapseMenuAfterEvent(event)) {
            onDisplayMenuExpandedChange(false)
        }
    }

    if (savedSessionToPrompt != null) {
        ResumeSavedSessionDialog(
            snapshot = savedSessionToPrompt,
            engineName = screenState.engine.name,
            onResume = { onEvent(GameUiEvent.ResumeSavedSession(savedSessionToPrompt)) },
            onDismiss = { onEvent(GameUiEvent.DismissResumePrompt) },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameHeaderSection(
            screenState = screenState,
            isDisplayMenuExpanded = isDisplayMenuExpanded,
            onDisplayMenuExpandedChange = onDisplayMenuExpandedChange,
        )

        if (isDisplayMenuExpanded) {
            ExpandedGameMenuSection(
                screenState = screenState,
                onEvent = onMenuEvent,
            )
        }

        GamePlaySection(
            screenState = screenState,
            onScoreGraphExpandedChange = onScoreGraphExpandedChange,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun ResumeSavedSessionDialog(
    snapshot: SavedGameSnapshot,
    engineName: String,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이전 대국 이어하기") },
        text = {
            Text(
                text = buildString {
                    appendLine("진행 중이던 ${snapshot.gameState.moves.size}수 대국이 있습니다.")
                    appendLine("이어 진행하시겠습니까?")
                    appendLine()
                    append("마지막 수: ")
                    append(
                        snapshot.gameState.moves
                            .lastOrNull()
                            ?.describe(snapshot.gameState.boardSize)
                            ?: "None",
                    )
                    appendLine()
                    append(snapshot.playerSetup.summary(engineName))
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text("예")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("아니오")
            }
        },
    )
}
