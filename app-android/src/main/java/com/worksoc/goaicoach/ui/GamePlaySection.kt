package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.boardInputEnabled
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.shared.StoneColor

@Composable
internal fun GamePlaySection(
    screenState: GameScreenState,
    onScoreGraphExpandedChange: (Boolean) -> Unit,
    onEvent: (GameUiEvent) -> Unit,
) {
    ScoreGraphPanel(
        snapshots = screenState.score.snapshots,
        capturedByBlack = screenState.gameState.capturedBy(StoneColor.Black),
        capturedByWhite = screenState.gameState.capturedBy(StoneColor.White),
        isExpanded = screenState.score.isGraphExpanded,
        onExpandedChange = onScoreGraphExpandedChange,
    )

    GoBoard(
        gameState = screenState.gameState,
        candidateMoves = screenState.analysis.candidateMoves,
        moveReviews = screenState.analysis.moveReviews,
        ownershipEstimate = screenState.score.estimate?.ownership,
        uxOptions = screenState.uxOptions,
        inputEnabled = !screenState.isGameEnded &&
            boardInputEnabled(
                screenState.playerSetup,
                screenState.engine.isReady,
                screenState.engine.isBusy,
                screenState.nextPlayer,
            ),
        engineBusy = screenState.engine.isBusy,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        onCoordinateTap = { coordinate ->
            onEvent(GameUiEvent.PlayAt(coordinate))
        },
    )

    GameActionButtons(
        screenState = screenState,
        onEvent = onEvent,
    )

    EngineResponsePanel(
        nextPlayer = screenState.nextPlayer,
        moveCount = screenState.gameState.moves.size,
        capturedByBlack = screenState.gameState.capturedBy(StoneColor.Black),
        capturedByWhite = screenState.gameState.capturedBy(StoneColor.White),
        lastMoveText = screenState.analysis.lastMoveText,
        isEngineBusy = screenState.engine.isBusy,
        playerSetup = screenState.playerSetup,
        engineMessage = screenState.engine.message,
        candidateText = screenState.analysis.candidateText,
        scoreText = screenState.score.text,
        moveReviewText = screenState.analysis.moveReviewText,
    )
}

@Composable
private fun GameActionButtons(
    screenState: GameScreenState,
    onEvent: (GameUiEvent) -> Unit,
) {
    val canPlayOnBoard = !screenState.isGameEnded &&
        boardInputEnabled(
            screenState.playerSetup,
            screenState.engine.isReady,
            screenState.engine.isBusy,
            screenState.nextPlayer,
        )
    val topMovesButtonEnabled = !screenState.isGameEnded &&
        screenState.engine.isReady &&
        (!screenState.engine.isBusy || screenState.analysis.topMovesEnabled)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onEvent(GameUiEvent.Pass) },
            enabled = canPlayOnBoard,
            modifier = Modifier.weight(1f),
            contentPadding = ActionButtonContentPadding,
        ) {
            ActionButtonText("Pass")
        }

        OutlinedButton(
            onClick = { onEvent(GameUiEvent.UndoLastTurn) },
            enabled = !screenState.engine.isBusy &&
                screenState.gameState.moves.isNotEmpty() &&
                (screenState.engine.isReady || screenState.matchMode == MatchMode.LocalTwoPlayer),
            modifier = Modifier.weight(1f),
            contentPadding = ActionButtonContentPadding,
        ) {
            ActionButtonText("Undo")
        }

        if (screenState.analysis.topMovesEnabled) {
            Button(
                onClick = { onEvent(GameUiEvent.ToggleTopMoves) },
                enabled = topMovesButtonEnabled,
                modifier = Modifier.weight(1f),
                contentPadding = ActionButtonContentPadding,
            ) {
                ActionButtonText("Top Moves")
            }
        } else {
            OutlinedButton(
                onClick = { onEvent(GameUiEvent.ToggleTopMoves) },
                enabled = topMovesButtonEnabled,
                modifier = Modifier.weight(1f),
                contentPadding = ActionButtonContentPadding,
            ) {
                ActionButtonText("Top Moves")
            }
        }

        OutlinedButton(
            onClick = { onEvent(GameUiEvent.RequestScoreEstimate) },
            enabled = !screenState.engine.isBusy &&
                (screenState.engine.isReady || screenState.matchMode == MatchMode.LocalTwoPlayer),
            modifier = Modifier.weight(1f),
            contentPadding = ActionButtonContentPadding,
        ) {
            ActionButtonText("Eval")
        }
    }
}

@Composable
private fun ActionButtonText(label: String) {
    Text(
        text = label,
        maxLines = 1,
        softWrap = false,
        style = MaterialTheme.typography.labelSmall,
    )
}

private val ActionButtonContentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
