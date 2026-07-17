package com.worksoc.goaicoach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.presentation.GameActionButtonRole
import com.worksoc.goaicoach.presentation.GameActionButtonState
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import kotlin.math.roundToInt
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.delay

@Composable
internal fun GamePlaySection(
    screenState: GameScreenState,
    onScoreGraphExpandedChange: (Boolean) -> Unit,
    turnTimeState: GameSessionTurnTimeState,
    onEvent: (GameUiEvent) -> Unit,
) {
    var tentativeMove by remember { mutableStateOf<BoardCoordinate?>(null) }

    LaunchedEffect(screenState.gameState) {
        tentativeMove = null
    }
    LaunchedEffect(screenState.uxOptions.isDirectPlayEnabled) {
        tentativeMove = null
    }

    GoBoard(
        gameState = screenState.gameState,
        candidateMoves = screenState.analysis.candidateMoves,
        moveReviews = screenState.analysis.moveReviews,
        ownershipEstimate = screenState.score.estimate?.ownership
            ?.takeIf { screenState.uxOptions.showOwnershipOverlay },
        uxOptions = screenState.uxOptions,
        inputEnabled = !screenState.isGameEnded &&
            screenState.matchSeats.current.canAcceptBoardInput,
        engineActivityIndicator = screenState.engine.activityIndicator,
        modifier = Modifier.fillMaxWidth(),
        tentativeMove = tentativeMove,
        onCoordinateTap = { coordinate ->
            if (screenState.uxOptions.isDirectPlayEnabled) {
                onEvent(GameUiEvent.PlayAt(coordinate))
            } else {
                tentativeMove = coordinate
            }
        },
        isGameEnded = screenState.isGameEnded,
        isEngineBusy = screenState.engine.isBusy,
    )

    // 대국 현황 패널 & 실시간 타이머 계산
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(turnTimeState.currentTurnPlayer, screenState.isGameEnded, screenState.engine.isBlockingBusy) {
        while (!screenState.isGameEnded) {
            delay(100)
            now = System.currentTimeMillis()
        }
    }

    val currentTurnPlayer = turnTimeState.currentTurnPlayer
    val elapsedSinceTurnStart = if (!screenState.isGameEnded && !screenState.engine.isBlockingBusy) {
        if (turnTimeState.isPaused) {
            (turnTimeState.pausedAtMillis - turnTimeState.currentTurnStartedAtMillis).coerceAtLeast(0L)
        } else {
            (now - turnTimeState.currentTurnStartedAtMillis).coerceAtLeast(0L)
        }
    } else {
        0L
    }

    val blackTotalMillis = turnTimeState.blackAccumulatedMillis + if (currentTurnPlayer == StoneColor.Black) elapsedSinceTurnStart else 0L
    val whiteTotalMillis = turnTimeState.whiteAccumulatedMillis + if (currentTurnPlayer == StoneColor.White) elapsedSinceTurnStart else 0L

    GameStatusPanel(
        screenState = screenState,
        turnTimeState = turnTimeState,
        tentativeMove = tentativeMove,
        blackTotalMillis = blackTotalMillis,
        whiteTotalMillis = whiteTotalMillis,
        onEvent = onEvent,
    )

    var showAnalysisDialog by remember { mutableStateOf(false) }
    val strings = LocalUiStrings.current

    GameActionButtons(
        screenState = screenState,
        onAnalysisClick = { showAnalysisDialog = true },
        onEvent = onEvent,
    )

    if (showAnalysisDialog) {
        AlertDialog(
            onDismissRequest = { showAnalysisDialog = false },
            title = {
                Text(
                    text = strings.kataGoAnalysis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                EngineResponsePanel(
                    screenState = screenState,
                    engineMessage = screenState.engine.message,
                    candidateText = screenState.analysis.candidateText,
                    moveReviewText = screenState.analysis.moveReviewText,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAnalysisDialog = false }) {
                    Text(strings.close)
                }
            }
        )
    }
}

@Composable
private fun GameActionButtons(
    screenState: GameScreenState,
    onAnalysisClick: () -> Unit,
    onEvent: (GameUiEvent) -> Unit,
) {
    val strings = LocalUiStrings.current
    var showResignConfirm by remember { mutableStateOf(false) }

    if (showResignConfirm) {
        AlertDialog(
            onDismissRequest = { showResignConfirm = false },
            title = { Text(strings.resignConfirmTitle) },
            text = { Text(strings.resignConfirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResignConfirm = false
                        onEvent(GameUiEvent.ResignCurrentGame)
                    },
                ) {
                    Text(strings.resign)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResignConfirm = false }) {
                    Text(strings.cancel)
                }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // [1열] "분석", 형세보기(Eval), 추천수(Top Moves), 무르기(Undo)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val isBlockingBusy = screenState.engine.isBlockingBusy
            val engineReady = screenState.engine.isReady
            val isLocalTwoPlayer = screenState.matchMode == MatchMode.LocalTwoPlayer

            // 1. "분석" 버튼 (신규 추가)
            val analysisEnabled = !isBlockingBusy && (engineReady || isLocalTwoPlayer)
            OutlinedButton(
                onClick = onAnalysisClick,
                enabled = analysisEnabled,
                modifier = Modifier.weight(1f),
                contentPadding = ActionButtonContentPadding,
            ) {
                ActionButtonText(strings.analysis)
            }

            // 2. 형세보기 (Eval) 버튼
            val evalAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.Eval }
            if (evalAction != null) {
                ActionButtonWrapper(
                    action = evalAction,
                    label = strings.eval,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. 추천수 (Top Moves) 버튼
            val topMovesAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.TopMoves }
            if (topMovesAction != null) {
                ActionButtonWrapper(
                    action = topMovesAction,
                    label = strings.topMoves,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f)
                )
            }

            // 4. 무르기 (Undo) 버튼
            val undoAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.Undo }
            if (undoAction != null) {
                ActionButtonWrapper(
                    action = undoAction,
                    label = strings.undo,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // [2열] 기권(Resign/New Game), 통과(Pass)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. 기권 / 새 게임 버튼 (좌측)
            val resignEnabled = screenState.isGameEnded || (!screenState.engine.isBlockingBusy && screenState.matchSeats.current.canAcceptBoardInput)
            OutlinedButton(
                onClick = {
                    if (screenState.isGameEnded) {
                        onEvent(GameUiEvent.StartConfiguredGame)
                    } else {
                        showResignConfirm = true
                    }
                },
                enabled = resignEnabled,
                modifier = Modifier.weight(1f),
                contentPadding = ActionButtonContentPadding,
            ) {
                ActionButtonText(if (screenState.isGameEnded) strings.newGameAction else strings.resign)
            }

            // 2. 통과 (Pass) 버튼 (우측)
            val passAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.Pass }
            if (passAction != null) {
                ActionButtonWrapper(
                    action = passAction,
                    label = strings.pass,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ActionButtonWrapper(
    action: GameActionButtonState,
    label: String,
    onEvent: (GameUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (action.isFilled) {
        Button(
            onClick = { onEvent(action.event) },
            enabled = action.enabled,
            modifier = modifier,
            contentPadding = ActionButtonContentPadding,
        ) {
            ActionButtonText(label)
        }
    } else {
        OutlinedButton(
            onClick = { onEvent(action.event) },
            enabled = action.enabled,
            modifier = modifier,
            contentPadding = ActionButtonContentPadding,
        ) {
            ActionButtonText(label)
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
