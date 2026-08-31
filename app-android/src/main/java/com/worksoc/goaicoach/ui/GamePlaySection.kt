package com.worksoc.goaicoach.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableItem
import com.worksoc.goaicoach.application.consumable.ConsumableSpendDecision
import com.worksoc.goaicoach.application.movereview.MoveReviewTone
import com.worksoc.goaicoach.application.premium.FeatureAccess
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.UnlockOption
import com.worksoc.goaicoach.application.safety.engineTurnWatchdogTimeoutMillisFor
import com.worksoc.goaicoach.application.safety.isEngineTurnWatchdogTriggered
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.presentation.GameActionButtonRole
import com.worksoc.goaicoach.presentation.GameActionButtonState
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.math.roundToInt
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

    // 보드에 무엇을 그릴 권한이 있는지는 여기서 정해 GoBoard에는 데이터만 넘긴다 — 보드가 스스로
    // premium.isActive를 보면 1회권으로 켠 표시가 걸러진다(티켓만 차감되고 아무것도 안 보이던
    // 버그, 2026-08-29 실기 확인). 세 경로가 모두 허용이다: 프리미엄 / 1회권 / 대국 종료.
    val boardPremium = LocalPremiumUiState.current
    val boardConsumables = LocalConsumableUiState.current
    fun mayShow(featureId: FeatureId): Boolean =
        boardPremium.resolve(featureId) is FeatureAccess.Allowed ||
            boardConsumables.isOneShotActive(featureId) ||
            screenState.isGameEnded

    val isBoardMaxSize = screenState.uxOptions.isBoardMaxSize

    // ⚠️ 크기 선택은 **보드 바깥, 위쪽 경계선 밖**에 둔다(2026-08-30 사용자 지시). 보드 위에
    // 얹으면 그 자리에 착수할 수 없다 — 판의 우상단은 실제로 두는 자리다.
    //
    // 선택기와 보드를 **한 Column으로 묶는다.** 둘을 형제로 두면 화면 Column의
    // `spacedBy(12.dp)`가 사이에 끼어 선택기가 판에서 떠 보이고 세로도 낭비된다 — 묶으면
    // 그 12dp가 이 묶음 위에만 한 번 붙고, 선택기는 경계선에 바짝 붙는다(사용자 피드백).
    Column(modifier = Modifier.fillMaxWidth()) {
    BoardTopControls(
        isMagnifierEnabled = screenState.uxOptions.isPlayMagnifierEnabled,
        onToggleMagnifier = {
            onEvent(
                GameUiEvent.ChangeUxOptions(
                    screenState.uxOptions.copy(
                        isPlayMagnifierEnabled = !screenState.uxOptions.isPlayMagnifierEnabled,
                    ),
                ),
            )
        },
        isMaxSize = isBoardMaxSize,
        onToggleBoardSize = {
            onEvent(GameUiEvent.ChangeUxOptions(screenState.uxOptions.copy(isBoardMaxSize = !isBoardMaxSize)))
        },
    )

    GoBoard(
        gameState = screenState.gameState,
        candidateMoves = screenState.analysis.candidateMoves
            .takeIf { mayShow(FeatureId.TopMoves) }
            .orEmpty(),
        moveReviews = screenState.analysis.moveReviews,
        // 대국 종료 시엔 프리미엄 여부와 무관하게 최종 형세를 보여준다 — 이 값 자체는
        // '형세보기' 버튼의 켜짐 표시(GameScreenState.kt의 isFilled)와는 무관하다.
        ownershipEstimate = screenState.score.estimate?.ownership
            ?.takeIf { screenState.uxOptions.showOwnershipOverlay || screenState.isGameEnded }
            ?.takeIf { mayShow(FeatureId.Eval) },
        uxOptions = screenState.uxOptions,
        inputEnabled = !screenState.isGameEnded &&
            screenState.matchSeats.current.canAcceptBoardInput,
        engineActivityIndicator = screenState.engine.activityIndicator,
        // ⚠️ 폭을 **GoBoard 바깥에서** 바꾼다. 안에서 바꾸면 탭 좌표 변환·좌표 라벨·형세
        // 오버레이가 저마다 다른 폭을 볼 위험이 있는데, 밖에서 주면 그 안의 모든 계산이
        // 같은 Canvas 크기를 따라간다.
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isBoardMaxSize) Modifier.expandBeyondScreenPadding() else Modifier),
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
    }

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
    val consumables = LocalConsumableUiState.current
    val moveCount = screenState.gameState.moves.size

    // 이 탭이 표를 쓰지 않는가 — 켜져 있어 끄는 탭이거나, 이 수순에 이미 값을 치렀거나(#44).
    // 잠금 테두리 판단에도 같은 기준을 써야 한다. 껐다고 테두리가 돌아오면 "누르면 또
    // 나간다"고 잘못 알리게 된다 — 실제로는 무료로 통과한다.
    fun tapIsFree(featureId: FeatureId): Boolean =
        consumables.isOneShotActive(featureId) || consumables.isPaidForMove(featureId, moveCount)
    // 버튼을 눌렀을 때 띄우는 토스트 하나. 잔량과 안내를 **한 토스트로 합친다** — 따로 띄우면
    // 안드로이드가 둘을 큐잉해 첫 사용 때 토스트가 연달아 두 번 뜬다(2026-08-29 실기 확인).
    // 안내("매 수마다 보려면 메뉴에서")는 대국 한 판에 한 번만 붙고, 수순이 리셋되면 다시 붙는다.
    var everyMoveHintShown by remember(screenState.gameState.moves.size == 0) { mutableStateOf(false) }
    fun toastForTap(spentMessage: String?) {
        val hint = strings.everyMoveHint.takeIf { !everyMoveHintShown }
        everyMoveHintShown = true
        val text = listOfNotNull(spentMessage, hint).joinToString("\n")
        if (text.isEmpty()) return
        val duration = if (hint != null) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        Toast.makeText(context, text, duration).show()
    }

    // 기능별 판정은 FeatureAccessPolicy(6계층, application/premium/FeatureAccessPolicy.kt)에
    // 위임한다 — 어느 기능이 무료/광고/구매/클레임 중 무엇으로 풀리는지는 여기서 다시
    // 판단하지 않는다. 잠겨 있을 때는 금색 테두리(PremiumLockedBorder)로 표시하고, 탭하면
    // 실제 동작 대신 업셀(또는 클레임 가능하면 클레임) 팝업을 띄운다. 액션을 뒤로 미루지
    // 않는다 — 이번 탭은 팝업까지만 하고 멈춘다 (기권/통과는 게이팅 대상이 아님).
    //
    // [featureId]가 있으면 잠긴 상태에서도 **1회권**이라는 길이 하나 더 있다(킥오프 플랜 4.5절):
    // 재고가 있으면 업셀 대신 사용 확인 팝업을 띄우고, 확인하면 한 장을 써서 이번 한 번만
    // 동작시킨다. 이미 1회권으로 켜 둔 표시를 다시 탭하는 것은 **끄는 동작**이므로 그냥
    // 통과시킨다 — 끄는 데 또 한 장을 받으면 "1회"가 반 번이 되기 때문이다.
    fun featureGated(
        access: FeatureAccess,
        featureId: FeatureId? = null,
        turningOn: Boolean = true,
        action: () -> Unit,
    ) {
        if (featureId != null && consumables.isOneShotActive(featureId)) {
            consumables.clearOneShot(featureId)
            action()
            return
        }
        // **같은 수순에서 껐다 다시 켜는 탭은 무료다**(백로그 #44, 2026-08-30). 표 한 장의 유효
        // 범위는 한 수이므로, 그 수 안에서 몇 번을 껐다 켜든 값은 이미 치른 것이다. 예전에는
        // 끄는 순간 지불 기록까지 지워져 세 번째 탭에서 한 장이 또 나갔다.
        if (featureId != null && turningOn && consumables.isPaidForMove(featureId, moveCount)) {
            consumables.markOneShot(featureId, moveCount)
            action()
            return
        }
        // **끄는 동작에는 표를 쓰지 않는다**(2026-08-30). 위 `isOneShotActive` 분기가 그 취지를
        // 이미 담고 있지만, 1회성 표시가 만료돼 지워진 뒤에 토글만 켜져 있는 상태가 생긴다 —
        // 그때 다시 탭하면 **끄는 동작인데 아래 Locked 분기가 표를 한 장 먹는다.** 켜는 동작이
        // 아닌 탭은 게이팅 자체를 건너뛴다.
        if (!turningOn) {
            action()
            return
        }
        when (access) {
            is FeatureAccess.Allowed -> {
                action()
                // 프리미엄이어도 **버튼은 1회성**이다(2026-08-29 사용자 확정) — 차감이 없을 뿐
                // 동작 모델은 같다. 매 수마다 갱신되는 상시 표시는 대국 메뉴의 '매 수마다'
                // 옵션이 담당한다. 켜는 동작일 때만 표시해야 끄는 탭이 1회성으로 오인되지 않는다.
                if (turningOn && featureId != null) consumables.markOneShot(featureId, moveCount)
                // 프리미엄은 차감이 없으니 잔량 문구가 없다 — 안내만 남으면 그것만 띄운다.
                toastForTap(spentMessage = null)
            }
            is FeatureAccess.Locked -> {
                val ticket = featureId?.let(consumables::ticketFor)
                when {
                    // 확인 팝업 없이 바로 쓴다(2026-08-29 사용자 재확정) — 오탭 여지가 낮고
                    // 오탭 비용도 작아 빠른 진행을 택했다. "말없이 쓰지 않는다"는 원래 취지는
                    // 사용 직후 토스트로 잔량을 알리는 것으로 지킨다.
                    ticket != null -> {
                        // 차감이 실제로 일어났을 때만 동작시킨다 — 그 사이 프리미엄이 켜졌다면
                        // decideConsumableSpend가 재고를 건드리지 않고 통과시키므로 그때도 동작한다.
                        // ⚠️ 잔량은 반드시 판정 결과의 `remaining`(차감 후)을 쓴다. `consumables`는
                        // 이번 재구성 시점의 값이라 `countOf`는 **차감 전** 재고를 돌려준다 —
                        // 그대로 쓰면 토스트 잔량이 1 많게 나온다(2026-08-29 실기에서 발견).
                        when (val decision = consumables.spend(ticket)) {
                            is ConsumableSpendDecision.OutOfStock -> Unit
                            is ConsumableSpendDecision.Spent -> {
                                consumables.markOneShot(featureId, moveCount)
                                action()
                                toastForTap(strings.consumableSpentToast(ticket, decision.remaining))
                            }
                            // 프리미엄이 그 사이 켜져 재고를 안 건드린 경우 — 잔량 문구는 두지 않는다.
                            is ConsumableSpendDecision.AllowedWithoutSpending -> {
                                consumables.markOneShot(featureId, moveCount)
                                action()
                                toastForTap(spentMessage = null)
                            }
                        }
                    }
                    UnlockOption.Claim in access.unlockOptions -> showUndoClaimDialog = true
                    else -> showPremiumUpsellDialog = true
                }
            }
        }
    }

    PremiumUpsellDialogHost(
        visible = showPremiumUpsellDialog,
        onDismiss = { showPremiumUpsellDialog = false },
    )

    // ⚠️ 방어적 폴백으로만 남긴 경로다(킥오프 플랜 4.4절, 백로그 #4). 정상 흐름에서는 앱 최초
    // 실행 시 출석 1일차 보상이 무르기를 자동 클레임하므로(`AttendanceCheckInCoordinator` →
    // `runAttendanceRewardGrant`) 여기까지 오지 않는다. 그래도 지우지 않는 이유:
    // (1) 자동 지급은 foreground 이벤트를 타는 비동기 경로라 실패/유실 가능성이 0이 아니고,
    // (2) `PremiumStateStore.load()`가 기기 시계 이상 등으로 상태를 기본값 폴백하면 이미 받은
    //     클레임이 사라지는데, 출석 쪽은 "1일차 지급 완료"로 기록돼 있어 자동 재지급되지 않는다.
    // 이 두 경우에 무르기를 영영 못 쓰게 되는 것보다, 도달 확률이 낮은 팝업 하나를 남겨 두는
    // 편이 안전하다. 자동 지급이 안정화됐다고 판단되면 그때 제거한다.
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
        // 재고 바를 여기 두지 않는다(#24, 2026-08-30). #17이 "차감이 눈앞에서 보이게" 상시
        // 띄웠지만, 대국 내내 필요한 정보가 아닌 데다 바로 아래 버튼과 같은 말을 두 번 했다.
        // 남은 수는 버튼 자신이 괄호로 말하고(`strings.featureButtonLabel`), 전체 재고는
        // 마이 페이지가 맡는다.

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
                    // 이름과 잔량을 **따로** 넘긴다 — 한 문자열이면 폭이 모자랄 때 잔량부터
                    // 잘려 나간다(#27).
                    label = strings.eval,
                    mark = strings.featureButtonMark(
                        access = evalAccess,
                        remaining = consumables.countOf(ConsumableCatalog.EvalOnce),
                    ),
                    onEvent = { event -> featureGated(evalAccess, FeatureId.Eval, turningOn = !evalAction.isFilled) { onEvent(event) } },
                    modifier = Modifier.weight(1f),
                    premiumLocked = evalAccess !is FeatureAccess.Allowed && !tapIsFree(FeatureId.Eval),
                )
            }

            // 2. 추천수 (Top Moves) 버튼 (프리미엄 전용)
            val topMovesAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.TopMoves }
            if (topMovesAction != null) {
                val topMovesAccess = premium.resolve(FeatureId.TopMoves)
                ToggleActionButton(
                    action = topMovesAction,
                    label = strings.topMovesAction,
                    mark = strings.featureButtonMark(
                        access = topMovesAccess,
                        remaining = consumables.countOf(ConsumableCatalog.TopMovesOnce),
                    ),
                    onEvent = { event -> featureGated(topMovesAccess, FeatureId.TopMoves, turningOn = !topMovesAction.isFilled) { onEvent(event) } },
                    modifier = Modifier.weight(1f),
                    premiumLocked = topMovesAccess !is FeatureAccess.Allowed && !tapIsFree(FeatureId.TopMoves),
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

/**
 * 부모(대국 화면 최상위 Column)가 준 좌우 여백을 **되찾아** 화면 폭 끝까지 그리게 한다(#38).
 *
 * 보드를 패딩 바깥으로 옮기는 대신 여기서 폭만 늘리는 이유: 보드는 헤더·점수 바·버튼들과 같은
 * Column 안에 있어야 세로 순서와 스크롤이 유지된다. 밖으로 빼면 그 배치를 다시 짜야 한다.
 *
 * 자기 크기는 **원래 제약대로** 보고하고 자식만 넓게 측정해 왼쪽으로 밀어 놓는다. 자기 크기를
 * 같이 키우면 부모 Column의 폭이 따라 커져 다른 행까지 화면 밖으로 밀려난다.
 */
private fun Modifier.expandBeyondScreenPadding(): Modifier = layout { measurable, constraints ->
    val extra = (GameScreenEdgePadding * 2).roundToPx()
    val target = constraints.maxWidth + extra
    val widened = Constraints.fixedWidth(target).copy(
        minHeight = constraints.minHeight,
        maxHeight = constraints.maxHeight,
    )
    val placeable = measurable.measure(widened)
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-(GameScreenEdgePadding.roundToPx()), 0)
    }
}

/**
 * 보드 **바로 위** 경계선에 바짝 붙는 토글 두 개(#38의 자리에 #39가 하나를 더했다).
 * 왼쪽은 **착수 돋보기**, 오른쪽은 **바둑판 크기**다(2026-08-31 사용자 지시 — 좌우 대칭 배치).
 *
 * ⚠️ **보드 위에 얹지 마라.** 처음에는 판 우상단에 오버레이했는데, 거기는 실제로 착수하는
 * 자리라 칩이 탭을 가로챈다(2026-08-30 사용자 지적).
 *
 * ## 세그먼트에서 토글로 바꾼 이유 (2026-08-31)
 * 원래 크기 선택기는 `여백`·`최대` 두 칩을 나란히 놓고 선택된 쪽을 강조했고, 그 주석은
 * *"라벨이 상태와 어긋나면 오히려 헷갈린다"* 는 이유로 토글을 물리쳤다. **그 판단을 뒤집었다:**
 * - **판 크기는 상태가 이미 눈에 보인다.** 판이 화면 끝까지 차 있으면 최대인 것이 즉시 보이므로,
 *   칩이 상태를 다시 말하는 것은 중복이다.
 * - 이 앱에는 "뒤집을 수 있다"를 말하는 관용구가 이미 있다 — `PlayModeSwitch`의 `⇅` 글리프다.
 *   ⚠️ 다만 **라벨 방향은 그쪽과 다르다**: 여기서는 색이 상태를 말하므로 라벨도 **지금 상태**를
 *   적는다(`UiStringsBoardControls.kt` KDoc에 뒤집은 사유가 있다).
 * - 폭이 절반으로 줄어 **왼쪽에 대칭 버튼 자리가 생긴다** — 이것이 실제 계기였다.
 *
 * ⚠️ **두 버튼은 반드시 같은 관용구를 써야 한다.** 하나는 상태 라벨, 하나는 동작 라벨이면
 * 나란히 놓인 두 칩이 서로 다른 문법으로 말하게 되어 가장 헷갈린다 — 그래서 [BoardTopToggle]
 * 하나를 공유한다.
 */
@Composable
private fun BoardTopControls(
    isMagnifierEnabled: Boolean,
    onToggleMagnifier: () -> Unit,
    isMaxSize: Boolean,
    onToggleBoardSize: () -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(
        // 아래 2dp만 남긴다 — 경계선에 바짝 붙이는 것이 요점이고, 세로 공간도 아낀다.
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoardTopToggle(
            label = playMagnifierLabelFor(strings.language),
            // ⚠️ 화면 라벨에 켜짐/꺼짐이 없으므로(사용자 지시) 상태는 **여기서만** 말한다.
            // 지우면 스크린 리더 사용자에게 이 버튼은 상태를 알 수 없는 버튼이 된다.
            spokenSubject = playMagnifierLabelFor(strings.language),
            spokenState = playMagnifierStateFor(strings.language, isMagnifierEnabled),
            active = isMagnifierEnabled,
            onClick = onToggleMagnifier,
        )
        BoardTopToggle(
            label = boardSizeToggleLabelFor(strings.language, isMaxSize),
            spokenSubject = boardSizeSubjectFor(strings.language),
            // 이쪽은 라벨이 곧 상태다 — 소리로도 같은 낱말을 넘긴다.
            spokenState = boardSizeToggleLabelFor(strings.language, isMaxSize),
            active = isMaxSize,
            onClick = onToggleBoardSize,
        )
    }
}

/**
 * 두 토글이 **한 함수를 공유한다** — 모양이 갈리면 위 KDoc의 "같은 관용구" 약속이 깨진다.
 *
 * ⚠️ **활성 표시는 테두리 색뿐이다**(2026-08-31 사용자 지시 — *"테두리 색만으로 충분"*).
 * 처음에는 대국 상태판 턴 카드처럼 배경까지 칠했는데 **너무 눈에 띄었다.** 그래서 배경·글자색·
 * 글자 굵기를 **상태와 무관하게 고정**하고 테두리만 [ActiveStateBorder]로 바꾼다.
 * 테두리 색 자체는 여전히 그 턴 카드와 같은 토큰이라 "초록 테두리 = 활성"이 화면 위아래에서
 * 한 가지 뜻으로 읽힌다. 값을 여기 다시 적지 말 것.
 *
 * ⚠️ **글자색은 언제나 [ActionButtonContentColor]다**(상태판 아래 버튼들과 같은 방식).
 * 꺼졌을 때 흐리게 하면 **비활성(누를 수 없음)으로 읽히는데** 이 버튼은 언제나 누를 수 있다.
 */
@Composable
private fun BoardTopToggle(
    label: String,
    spokenSubject: String,
    spokenState: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics {
                // 이 파일·`GameActionButtons.kt`의 다른 토글들과 같은 관용구다.
                role = Role.Switch
                contentDescription = spokenSubject
                stateDescription = spokenState
            },
        shape = RoundedCornerShape(10.dp),
        // ⚠️ 활성일 때도 **같은 배경**이다. 이름이 `Inactive~`인 것은 이 토큰의 출처(턴 카드의
        // 비활성 색)를 가리키는 것이고, 여기서는 두 상태가 함께 쓰는 바탕색이다.
        color = InactiveStateContainerColor,
        border = if (active) ActiveStateBorder else InactiveStateBorder,
        tonalElevation = 0.dp,
    ) {
        Text(
            // `⇅`는 "이건 뒤집히는 것"이라는 이 앱의 관용구다(`PlayModeSwitch`와 같다).
            text = "\u21C5 " + label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Normal,
            color = ActionButtonContentColor,
            maxLines = 1,
        )
    }
}
