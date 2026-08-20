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
import com.worksoc.goaicoach.application.premium.FeatureAccess
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.UnlockOption
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.safety.engineTurnWatchdogTimeoutMillisFor
import com.worksoc.goaicoach.application.safety.isEngineTurnWatchdogTriggered
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.presentation.GameActionButtonRole
import com.worksoc.goaicoach.presentation.GameActionButtonState
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import kotlin.math.roundToInt
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

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
        whiteWinRate = screenState.score.estimate?.whiteWinRate,
        isExpanded = screenState.score.isGraphExpanded,
        onExpandedChange = onScoreGraphExpandedChange,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    GoBoard(
        gameState = screenState.gameState,
        candidateMoves = screenState.analysis.candidateMoves,
        moveReviews = screenState.analysis.moveReviews,
        // 대국 종료 시엔 프리미엄 여부와 무관하게 최종 형세를 보여준다 — 이 값 자체는
        // '형세보기' 버튼의 켜짐 표시(GameScreenState.kt의 isFilled)와는 무관하다.
        ownershipEstimate = screenState.score.estimate?.ownership
            ?.takeIf { screenState.uxOptions.showOwnershipOverlay || screenState.isGameEnded },
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
                // 양패스(또는 보드 가득 참) 이후에는 일반 착수가 아니라 계가(종국 처리) 엔진
                // 호출이 진행 중이다 — 정상적으로도 수 초 더 걸릴 수 있으므로 착수 시간 제한이
                // 아닌 별도의 계가 전용 한도를 적용해야 오탐 팝업을 피할 수 있다.
                val isResolvingEndgame = screenState.gameState.hasConsecutivePasses() ||
                    screenState.gameState.isBoardFull()
                if (isEngineTurnWatchdogTriggered(isAiTurn, elapsedSinceTurnStartMillis, searchTimeLimit, isResolvingEndgame)) {
                    watchdogReported = true
                    val thresholdMillis = engineTurnWatchdogTimeoutMillisFor(searchTimeLimit, isResolvingEndgame)
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

    // 팝업이 떠 있는 도중 이번 차례 대기(AI 착수 AutoAiTurn, 사람 착수 후 동기화 HumanMoveSync —
    // 양패스로 계가에 들어가는 경우 포함, AI 쪽 종국 처리 AutoAiEndgame)가 성공/실패/폐기(discard)
    // 중 무엇으로 끝나든, currentTurnStartedAtMillis 갱신(성공 시에만 발생)을 기다리지 않고 즉시
    // 팝업을 닫는다. 이 신호가 없으면 실패 후 조용히 재시도되는 경우, 혹은 계가 처리가 다른
    // 종류의 작업(HumanMoveSync)으로 끝났는데 AutoAiTurn 완료만 감시하는 경우 팝업이 닫히지
    // 않고 hang 상태로(계가 결과 팝업 아래에) 남는다.
    //
    // activityIndicator 자체(Thinking 여부)를 직접 비교하는 방식은 실기기 재현에서 실패했다 —
    // 실패 직후 재시도가 같은 리컴포지션 배치 안에서 바로 다음 시도를 시작하면 값 전이가
    // Compose 리컴포지션에 의해 뭉개져서(coalesced) LaunchedEffect(key) 쪽에서 "값이 바뀌었다"는
    // 이벤트 자체를 못 받는다. 대신 완료 횟수를 세는 단조증가 카운터(engineTurnWaitCompletionSeq)를
    // 도입해, "팝업이 뜬 시점의 카운터 값과 달라지는 순간"을 snapshotFlow로 기다린다 — 카운터는
    // 절대 이전 값으로 되돌아가지 않으므로 중간값이 뭉개져도 최종적으로 값이 다르다는 사실
    // 자체는 유실되지 않는다.
    val liveEngineTurnWaitCompletionSeq = rememberUpdatedState(screenState.engine.engineTurnWaitCompletionSeq)
    LaunchedEffect(showEngineStuckDialog) {
        if (showEngineStuckDialog) {
            val openedAtSeq = liveEngineTurnWaitCompletionSeq.value
            snapshotFlow { liveEngineTurnWaitCompletionSeq.value }
                .first { it != openedAtSeq }
            showEngineStuckDialog = false
        }
    }

    if (showEngineStuckDialog) {
        val strings = LocalUiStrings.current
        AlertDialog(
            onDismissRequest = { showEngineStuckDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.engineStuckDialogTitle)
                    TextButton(onClick = { onEvent(GameUiEvent.CopyDebugReport) }) {
                        Text(strings.copyLog, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
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

    GameActionButtons(
        screenState = screenState,
        onEvent = onEvent,
    )
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
    onEvent: (GameUiEvent) -> Unit,
) {
    val strings = LocalUiStrings.current
    val premium = LocalPremiumUiState.current
    val context = LocalContext.current
    var showResignConfirm by remember { mutableStateOf(false) }
    var showPremiumUpsellDialog by remember { mutableStateOf(false) }
    var showUndoClaimDialog by remember { mutableStateOf(false) }

    // 기능별 판정은 FeatureAccessPolicy(6계층, application/premium/FeatureAccessPolicy.kt)에
    // 위임한다 — 어느 기능이 무료/광고/구매/클레임 중 무엇으로 풀리는지는 여기서 다시
    // 판단하지 않는다. 잠겨 있을 때는 금색 테두리(PremiumLockedBorder)로 표시하고, 탭하면
    // 실제 동작 대신 업셀(또는 클레임 가능하면 클레임) 팝업을 띄운다. 액션을 뒤로 미루지
    // 않는다 — 이번 탭은 팝업까지만 하고 멈춘다 (기권/통과는 게이팅 대상이 아님).
    fun featureGated(access: FeatureAccess, action: () -> Unit) {
        when (access) {
            is FeatureAccess.Allowed -> action()
            is FeatureAccess.Locked ->
                if (UnlockOption.Claim in access.unlockOptions) showUndoClaimDialog = true else showPremiumUpsellDialog = true
        }
    }

    PremiumUpsellDialogHost(
        visible = showPremiumUpsellDialog,
        onDismiss = { showPremiumUpsellDialog = false },
    )

    if (showUndoClaimDialog) {
        AlertDialog(
            onDismissRequest = { showUndoClaimDialog = false },
            title = { Text(strings.undoClaimTitle) },
            text = { Text(strings.undoClaimMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUndoClaimDialog = false
                        premium.claim(FeatureId.Undo)
                        Toast.makeText(context, strings.undoClaimSuccessMessage, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(strings.undoClaimConfirmAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUndoClaimDialog = false }) {
                    Text(strings.no)
                }
            },
        )
    }

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
        // [1행] 형세보기(Eval), 추천수(Top Moves) — 프리미엄 전용 온/오프 토글, 2열로 크게 배치
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. 형세보기 (Eval) 버튼 (프리미엄 전용)
            val evalAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.Eval }
            if (evalAction != null) {
                val evalAccess = premium.resolve(FeatureId.Eval)
                ToggleActionButton(
                    action = evalAction,
                    label = strings.eval,
                    onEvent = { event -> featureGated(evalAccess) { onEvent(event) } },
                    modifier = Modifier.weight(1f),
                    premiumLocked = evalAccess !is FeatureAccess.Allowed,
                )
            }

            // 2. 추천수 (Top Moves) 버튼 (프리미엄 전용)
            val topMovesAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.TopMoves }
            if (topMovesAction != null) {
                val topMovesAccess = premium.resolve(FeatureId.TopMoves)
                ToggleActionButton(
                    action = topMovesAction,
                    label = strings.topMoves,
                    onEvent = { event -> featureGated(topMovesAccess) { onEvent(event) } },
                    modifier = Modifier.weight(1f),
                    premiumLocked = topMovesAccess !is FeatureAccess.Allowed,
                )
            }
        }

        // [2행] 기권(Resign/New Game), 통과(Pass), 무르기(Undo) — 기본 기능 버튼 3열
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. 기권 / 새 게임 버튼
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

            // 2. 통과 (Pass) 버튼
            val passAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.Pass }
            if (passAction != null) {
                SingleActionButton(
                    action = passAction,
                    label = strings.pass,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f),
                )
            }

            // 3. 무르기 (Undo) 버튼 (클레임 시 무료 — 그랜드파더링, launch-plan/README.md 3장)
            val undoAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.Undo }
            if (undoAction != null) {
                val undoAccess = premium.resolve(FeatureId.Undo)
                SingleActionButton(
                    action = undoAction,
                    label = strings.undo,
                    onEvent = { event -> featureGated(undoAccess) { onEvent(event) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}


