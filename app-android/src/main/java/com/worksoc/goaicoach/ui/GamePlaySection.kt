package com.worksoc.goaicoach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.worksoc.goaicoach.application.movereview.MoveReviewTone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.safety.engineTurnWatchdogTimeoutMillisFor
import com.worksoc.goaicoach.application.safety.isEngineTurnWatchdogTriggered
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

private const val TurnTimerTickIntervalMillis = 200L

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

    ScoreTimelineGraph(
        snapshots = screenState.score.snapshots,
        capturedByBlack = screenState.gameState.capturedBy(StoneColor.Black),
        capturedByWhite = screenState.gameState.capturedBy(StoneColor.White),
        isExpanded = screenState.score.isGraphExpanded,
        onExpandedChange = onScoreGraphExpandedChange,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

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

    // 범례는 보드에 실제로 착수 품질 색이 그려지는 조건(GoBoard.kt의 showMoveReview + 프리미엄
    // 게이팅)과 정확히 일치시킨다 — 추천수/형세 활성 여부와는 무관하다.
    val premiumForLegend = LocalPremiumUiState.current
    if (screenState.uxOptions.showMoveReview && premiumForLegend.isActive) {
        Spacer(modifier = Modifier.height(4.dp))
        MoveQualityLegend()
    }

    // 대국 현황 패널 & 실시간 타이머 계산 (AI 차례 포함 실시간 티킹)
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    // 와치독 발동 시 뜨는 복구 팝업의 표시 여부. 새 차례가 시작될 때마다(키가 바뀔 때마다)
    // remember가 자동으로 false로 되돌리므로, 다음 차례에는 다시 정상적으로 감지 가능하다.
    var showEngineStuckDialog by remember(turnTimeState.currentTurnStartedAtMillis) { mutableStateOf(false) }
    var engineStuckElapsedMillis by remember(turnTimeState.currentTurnStartedAtMillis) { mutableStateOf(0L) }
    var engineStuckThresholdMillis by remember(turnTimeState.currentTurnStartedAtMillis) { mutableStateOf(0L) }
    LaunchedEffect(turnTimeState.currentTurnStartedAtMillis, turnTimeState.isPaused, screenState.isGameEnded) {
        // 안전 관리(레프리) 도메인 와치독: 새 차례가 시작될 때마다(이 effect가 재시작될 때마다)
        // 리셋되므로 별도 remember 없이 이 지역 변수 하나로 "이번 차례에 이미 보고했는지"를 추적한다.
        var watchdogReported = false
        while (!screenState.isGameEnded && !turnTimeState.isPaused) {
            delay(TurnTimerTickIntervalMillis)
            now = System.currentTimeMillis()
            if (!watchdogReported) {
                val elapsedSinceTurnStartMillis = (now - turnTimeState.currentTurnStartedAtMillis).coerceAtLeast(0L)
                val isAiTurn = when (turnTimeState.currentTurnPlayer) {
                    StoneColor.Black -> screenState.playerSetup.black.controller == SeatController.Ai
                    StoneColor.White -> screenState.playerSetup.white.controller == SeatController.Ai
                }
                val searchTimeLimit = screenState.searchTimeSettings.limit
                if (isEngineTurnWatchdogTriggered(isAiTurn, elapsedSinceTurnStartMillis, searchTimeLimit)) {
                    watchdogReported = true
                    val thresholdMillis = engineTurnWatchdogTimeoutMillisFor(searchTimeLimit)
                    onEvent(
                        GameUiEvent.ReportEngineTurnWatchdogTriggered(
                            elapsedMillis = elapsedSinceTurnStartMillis,
                            thresholdMillis = thresholdMillis,
                        ),
                    )
                    // 감지에 그치지 않고 사용자가 인식하고 직접 복구할 수 있도록 팝업을 띄운다.
                    // 자동 복구는 이번 범위에 포함하지 않음 — 개발 단계에서는 사용자가 직접
                    // 확인 후 판단하도록 한다.
                    engineStuckElapsedMillis = elapsedSinceTurnStartMillis
                    engineStuckThresholdMillis = thresholdMillis
                    showEngineStuckDialog = true
                }
            }
        }
    }

    if (showEngineStuckDialog) {
        val strings = LocalUiStrings.current
        AlertDialog(
            onDismissRequest = { showEngineStuckDialog = false },
            title = { Text(strings.engineStuckDialogTitle) },
            text = {
                Column {
                    Text(strings.engineStuckDialogMessage)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "elapsed=${engineStuckElapsedMillis / 1000}s / threshold=${engineStuckThresholdMillis / 1000}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEngineStuckDialog = false
                        onEvent(GameUiEvent.ForceResetEngine)
                    },
                ) {
                    Text(strings.engineStuckDialogResetAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEngineStuckDialog = false }) {
                    Text(strings.engineStuckDialogWaitAction)
                }
            },
        )
    }

    val currentTurnPlayer = turnTimeState.currentTurnPlayer
    val elapsedSinceTurnStart = if (!screenState.isGameEnded) {
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

/**
 * 추천수(Top Moves) 또는 형세보기(Eval) 오버레이가 켜져 있을 때 노출되는
 * 착수 품질 색상 범례. GoBoard의 candidateToneColor와 동일한 색상을 사용한다.
 */
@Composable
private fun MoveQualityLegend() {
    val strings = LocalUiStrings.current
    val items = listOf(
        MoveReviewTone.Excellent to strings.legendBest,
        MoveReviewTone.Good to strings.legendGood,
        MoveReviewTone.Inaccuracy to strings.legendInaccuracy,
        MoveReviewTone.Mistake to strings.legendMistake,
        MoveReviewTone.Blunder to strings.legendBlunder,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { (tone, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(candidateToneColor(tone), CircleShape),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun GameActionButtons(
    screenState: GameScreenState,
    onAnalysisClick: () -> Unit,
    onEvent: (GameUiEvent) -> Unit,
) {
    val strings = LocalUiStrings.current
    val premium = LocalPremiumUiState.current
    var showResignConfirm by remember { mutableStateOf(false) }
    var showPremiumUpsellDialog by remember { mutableStateOf(false) }
    val lockedAlpha = if (premium.isActive) 1f else 0.5f

    // 분석/형세보기/추천수/무르기는 프리미엄 전용. 비활성 상태에서는 흐리게 표시하고,
    // 탭하면 실제 동작 대신 업셀 팝업을 띄운다 (기권/통과는 게이팅 대상이 아님).
    fun premiumGated(action: () -> Unit) {
        if (premium.isActive) action() else showPremiumUpsellDialog = true
    }

    PremiumUpsellDialogHost(
        visible = showPremiumUpsellDialog,
        onDismiss = { showPremiumUpsellDialog = false },
    )

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
        // [1열] 분석, 형세보기(Eval), 추천수(Top Moves)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val engineReady = screenState.engine.isReady
            val isLocalTwoPlayer = screenState.matchMode == MatchMode.LocalTwoPlayer

            // 1. "분석" 버튼 (프리미엄 전용)
            val analysisEnabled = engineReady || isLocalTwoPlayer
            ActionButton(
                onClick = { premiumGated(onAnalysisClick) },
                enabled = analysisEnabled,
                modifier = Modifier.weight(1f).alpha(lockedAlpha),
                label = strings.analysis,
            )

            // 2. 형세보기 (Eval) 버튼 (프리미엄 전용)
            val evalAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.Eval }
            if (evalAction != null) {
                ToggleActionButton(
                    action = evalAction,
                    label = strings.eval,
                    onEvent = { event -> premiumGated { onEvent(event) } },
                    modifier = Modifier.weight(1f).alpha(lockedAlpha)
                )
            }

            // 3. 추천수 (Top Moves) 버튼 (프리미엄 전용)
            val topMovesAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.TopMoves }
            if (topMovesAction != null) {
                ToggleActionButton(
                    action = topMovesAction,
                    label = strings.topMoves,
                    onEvent = { event -> premiumGated { onEvent(event) } },
                    modifier = Modifier.weight(1f).alpha(lockedAlpha)
                )
            }
        }

        // [2열] 기권(Resign/New Game), 통과(Pass), 무르기(Undo)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. 기권 / 새 게임 버튼 (좌측)
            val resignEnabled = screenState.isGameEnded || (!screenState.engine.isBlockingBusy && screenState.matchSeats.current.canAcceptBoardInput)
            ActionButton(
                onClick = {
                    if (screenState.isGameEnded) {
                        onEvent(GameUiEvent.StartConfiguredGame)
                    } else {
                        showResignConfirm = true
                    }
                },
                enabled = resignEnabled,
                modifier = Modifier.weight(1f),
                label = if (screenState.isGameEnded) strings.newGameAction else strings.resign,
            )

            // 2. 통과 (Pass) 버튼 (중앙)
            val passAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.Pass }
            if (passAction != null) {
                SingleActionButton(
                    action = passAction,
                    label = strings.pass,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f),
                )
            }

            // 3. 무르기 (Undo) 버튼 (우측, 프리미엄 전용)
            val undoAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.Undo }
            if (undoAction != null) {
                SingleActionButton(
                    action = undoAction,
                    label = strings.undo,
                    onEvent = { event -> premiumGated { onEvent(event) } },
                    modifier = Modifier.weight(1f).alpha(lockedAlpha),
                )
            }
        }
    }
}


