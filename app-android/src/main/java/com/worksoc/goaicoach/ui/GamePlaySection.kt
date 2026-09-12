package com.worksoc.goaicoach.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableSpendDecision
import com.worksoc.goaicoach.application.movereview.MoveReviewTone
import com.worksoc.goaicoach.application.premium.FeatureAccess
import com.worksoc.goaicoach.application.preferences.DelayedPlayWindowMillis
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.safety.engineTurnWatchdogTimeoutMillisFor
import com.worksoc.goaicoach.application.safety.isEngineTurnWatchdogTriggered
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.presentation.GameActionButtonRole
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.worksoc.goaicoach.application.guide.GuideTarget

private const val TurnTimerTickIntervalMillis = 200L

/**
 * 지연 착수가 기다리는 자리(백로그 #144). [token]은 **누를 때마다 새로 발급**한다 — 같은 자리를 다시 눌러도
 * 타이머가 처음부터 다시 돌게 하는 것이 이 값의 유일한 존재 이유다.
 */
private data class PendingPlay(val coordinate: BoardCoordinate, val token: Int)

@Composable
internal fun GamePlaySection(
    screenState: GameScreenState,
    // 폰 배치 / 넓은 배치(#141). 판정은 `gameScreenLayoutFor` — 뷰포트 크기만 본다.
    layout: GameScreenLayout,
    // ⚠️ **기본값을 두지 않는다**(함정 40) — 빠뜨리면 조용히 예전처럼 넘치는 화면이 된다.
    //   재는 쪽은 `GoCoachContent`이고 계산은 `fittedBoardMaxHeightPx`(#139). 폰 배치만 쓴다.
    boardMaxHeight: Dp?,
    onBoardHeightChanged: (Int) -> Unit,
    onScoreGraphExpandedChange: (Boolean) -> Unit,
    turnTimeState: GameSessionTurnTimeState,
    onEvent: (GameUiEvent) -> Unit,
    // 넓은 배치의 위 줄 맨 앞 ☰. 메뉴 상태는 `GoCoachContent`가 들고 있다. 폰 배치는 헤더가 그린다.
    wideMenuButton: @Composable () -> Unit,
) {
    var tentativeMove by remember { mutableStateOf<BoardCoordinate?>(null) }
    // 지연 착수(#144) — 떼고 나서 기다리는 자리. ⚠️ **좌표만으로는 안 된다**: 같은 자리를 다시 눌러도
    // 처음부터 다시 세야 하는데(사용자 결정), 좌표가 그대로면 키가 안 바뀌어 타이머가 재시작되지 않는다.
    // 그래서 누를 때마다 **새 번호**를 함께 발급한다.
    var pendingPlay by remember { mutableStateOf<PendingPlay?>(null) }
    var pendingPlaySeq by remember { mutableIntStateOf(0) }
    // 이 애니메이션이 곧 타이머다 — 진해지는 것과 놓이는 시점이 **같은 하나**라 서로 어긋날 수 없다.
    val pendingPlayProgress = remember { Animatable(0f) }

    LaunchedEffect(screenState.gameState) {
        tentativeMove = null
        // ⚠️ 판이 바뀌면 대기를 버린다 — 무르기·기권·종국·AI 착수가 그 사이 들어오면, 남겨 둘 경우
        //   이미 끝난 판이나 남의 차례에 돌이 하나 더 떨어진다.
        pendingPlay = null
    }

    LaunchedEffect(pendingPlay) {
        val waiting = pendingPlay ?: return@LaunchedEffect
        pendingPlayProgress.snapTo(0f)
        pendingPlayProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = DelayedPlayWindowMillis.toInt(), easing = LinearEasing),
        )
        pendingPlay = null
        onEvent(GameUiEvent.PlayAt(waiting.coordinate))
    }
    LaunchedEffect(screenState.uxOptions.isDirectPlayEnabled) {
        tentativeMove = null
    }

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
    val onToggleMagnifier = {
        onEvent(
            GameUiEvent.ChangeUxOptions(
                screenState.uxOptions.copy(
                    isPlayMagnifierEnabled = !screenState.uxOptions.isPlayMagnifierEnabled,
                ),
            ),
        )
    }
    val onToggleBoardSize = {
        onEvent(GameUiEvent.ChangeUxOptions(screenState.uxOptions.copy(isBoardMaxSize = !isBoardMaxSize)))
    }

    // 대국 현황 패널 & 실시간 타이머 계산 (AI 차례 포함 실시간 티킹)
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    // 와치독 발동 시 뜨는 복구 팝업의 표시 여부. 새 차례가 시작될 때마다(키가 바뀔 때마다)
    // remember가 자동으로 false로 되돌리므로, 다음 차례에는 다시 정상적으로 감지 가능하다.
    var showEngineStuckDialog by remember(turnTimeState.currentTurnStartedAtMillis) { mutableStateOf(false) }
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
                // ⚠️ **여기에 `elapsed=63s / threshold=60s` 한 줄이 있었다**(백로그 #91 ⓒ, 2026-09-06 제거).
                // 이 다이얼로그의 나머지 넷은 전부 `strings.engineStuck*`(4개 언어)인데 그 줄만
                // 하드코딩 영문이었고 `BuildConfig.DEBUG` 게이트도 없어 **릴리스 사용자에게 그대로
                // 나갔다.** 번역하지 않고 지운 이유: 같은 두 값이 이미 진단 로그에 있고
                // (`GoCoachApp.kt`의 `engine_turn_watchdog_triggered` → `elapsedMillis`/`thresholdMillis`),
                // **이 팝업 제목 줄에 그 로그를 복사하는 버튼이 이미 있다.** 숫자를 원하는 사람은
                // 개발자이고, 개발자는 그 버튼을 누르면 된다.
                // ⚠️ **팝업 자체는 지우지 말 것** — 엔진 멈춤은 기조 1ⓒ의 "실패" 쪽이라 적극 알리는
                // 것이 맞다. 지운 것은 사용자가 읽을 수 없는 한 줄뿐이다.
                Text(strings.engineStuckDialogMessage)
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

    // 판은 두 배치가 **같은 호출**을 쓴다 — 자리(modifier)만 다르다. 두 벌로 적으면 한쪽만 고쳐지는
    // 사고가 난다(표시 권한·입력 조건이 여기 다 모여 있다).
    val board: @Composable (Modifier) -> Unit = { boardModifier ->
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
            modifier = boardModifier,
            tentativeMove = tentativeMove,
            pendingPlay = pendingPlay?.coordinate,
            pendingPlayProgress = { pendingPlayProgress.value },
            // 손가락이 닿는 순간 대기를 **버린다**(#144 실기 결함, 2026-09-12 사용자).
            // 카운트는 판에서 손이 떨어져 있을 때만 돈다 — 버리면 `LaunchedEffect(pendingPlay)`가
            // 취소돼 옛 자리가 확정되지 않고, 떼는 순간 `onCoordinateTap`이 **새 번호**로 다시 센다.
            // ⚠️ 조건을 달지 않는다 — 지연 착수가 꺼져 있으면 대기 자체가 없어 `null` 대입은 무해하고,
            //   조건을 달면 이 람다가 옛 `screenState`를 붙든 채 굳을 수 있다.
            onCoordinatePress = { pendingPlay = null },
            onCoordinateTap = { coordinate ->
                when {
                    // 지연 착수(#144): 누를 때마다 **그 자리에서 처음부터** 다시 센다 — 다른 자리든 같은 자리든.
                    screenState.uxOptions.isDelayedPlayEnabled -> {
                        pendingPlaySeq += 1
                        pendingPlay = PendingPlay(coordinate, pendingPlaySeq)
                    }
                    screenState.uxOptions.isDirectPlayEnabled -> onEvent(GameUiEvent.PlayAt(coordinate))
                    else -> tentativeMove = coordinate
                }
            },
            isGameEnded = screenState.isGameEnded,
            isEngineBusy = screenState.engine.isBusy,
        )
    }

    // 범례는 보드에 실제로 착수 품질 색이 그려지는 조건(GoBoard.kt의 showMoveReview + 프리미엄
    // 게이팅)과 정확히 일치시킨다 — 추천수/형세 활성 여부와는 무관하다.
    val showMoveQualityLegend = screenState.uxOptions.showMoveReview && LocalPremiumUiState.current.isActive

    when (layout) {
        GameScreenLayout.Phone -> {
            ScoreTimelineGraph(
                snapshots = screenState.score.snapshots,
                capturedByBlack = screenState.gameState.capturedBy(StoneColor.Black),
                capturedByWhite = screenState.gameState.capturedBy(StoneColor.White),
                whiteWinRate = screenState.score.estimate?.whiteWinRate,
                isExpanded = screenState.score.isGraphExpanded,
                onExpandedChange = onScoreGraphExpandedChange,
                modifier = Modifier.fillMaxWidth()
            )

            // ⚠️ 크기 선택은 **보드 바깥, 위쪽 경계선 밖**에 둔다(2026-08-30 사용자 지시). 보드 위에
            // 얹으면 그 자리에 착수할 수 없다 — 판의 우상단은 실제로 두는 자리다.
            //
            // 선택기와 보드를 **한 Column으로 묶는다.** 둘을 형제로 두면 화면 Column의
            // `spacedBy(12.dp)`가 사이에 끼어 선택기가 판에서 떠 보이고 세로도 낭비된다 — 묶으면
            // 그 12dp가 이 묶음 위에만 한 번 붙고, 선택기는 경계선에 바짝 붙는다(사용자 피드백).
            Column(modifier = Modifier.fillMaxWidth()) {
                // ⚠️ 판 **위쪽 경계선 밖**이다 — 판 위에 얹으면 그 자리에 착수할 수 없다(위 주석과 같은 이유).
                // 엔진이 멀쩡하면 아무것도 그리지 않으므로 정상 대국의 레이아웃은 그대로다.
                EngineUnavailableBadge(
                    availability = screenState.engine.availability,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                // ⚠️ 폭을 **GoBoard 바깥에서** 바꾼다. 안에서 바꾸면 탭 좌표 변환·좌표 라벨·형세
                // 오버레이가 저마다 다른 폭을 볼 위험이 있는데, 밖에서 주면 그 안의 모든 계산이
                // 같은 Canvas 크기를 따라간다.
                board(
                    Modifier
                        .fillMaxWidth()
                        // ⚠️ 한 화면에 맞추는 상한(#139) — 넘칠 때만 가로폭보다 작다. `GoBoard`의 `min(가로, 세로)`가
                        //   이 세로를 보고 판을 줄인다. 확대(`expandBeyondScreenPadding`)보다 **앞**에 둬야 그쪽이
                        //   이 상한을 그대로 넘겨받는다.
                        .then(if (boardMaxHeight != null) Modifier.heightIn(max = boardMaxHeight) else Modifier)
                        .onSizeChanged { size -> onBoardHeightChanged(size.height) }
                        .then(if (isBoardMaxSize) Modifier.expandBeyondScreenPadding() else Modifier),
                )
            }

            if (showMoveQualityLegend) {
                Spacer(modifier = Modifier.height(4.dp))
                MoveQualityLegend()
            }

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
                firstRowLeading = null,
                secondRowLeading = null,
            )
        }

        GameScreenLayout.WideStacked -> WidePlayArrangement(
            screenState = screenState,
            // 차례 표시는 폰 상태판과 **같은 출처**를 본다 — 시계가 도는 쪽과 초록 테두리가 어긋나지 않게.
            currentTurnPlayer = currentTurnPlayer,
            isBoardMaxSize = isBoardMaxSize,
            onToggleMagnifier = onToggleMagnifier,
            onToggleBoardSize = onToggleBoardSize,
            tentativeMove = tentativeMove,
            blackTotalMillis = blackTotalMillis,
            whiteTotalMillis = whiteTotalMillis,
            showMoveQualityLegend = showMoveQualityLegend,
            onScoreGraphExpandedChange = onScoreGraphExpandedChange,
            onEvent = onEvent,
            menuButton = wideMenuButton,
            board = board,
        )

        GameScreenLayout.WideColumns -> WideColumnsArrangement(
            screenState = screenState,
            currentTurnPlayer = currentTurnPlayer,
            isBoardMaxSize = isBoardMaxSize,
            onToggleMagnifier = onToggleMagnifier,
            onToggleBoardSize = onToggleBoardSize,
            tentativeMove = tentativeMove,
            blackTotalMillis = blackTotalMillis,
            whiteTotalMillis = whiteTotalMillis,
            showMoveQualityLegend = showMoveQualityLegend,
            onScoreGraphExpandedChange = onScoreGraphExpandedChange,
            onEvent = onEvent,
            menuButton = wideMenuButton,
            board = board,
        )
    }
}

/**
 * **넓은 배치**(백로그 #141 P1, 2026-09-12 사용자 확정) — 판을 먼저 최대로, 조작부는 **남는 변**에.
 * 세로로 펼친 폴드는 거의 정사각형이라 위아래가 남는다:
 * - 위 한 줄: ☰ · 흑 좌석 · 수순/점수(누르면 그래프) · 백 좌석
 * - 판: 남는 높이를 전부 — `GoBoard`의 `min(가로, 세로)`가 정사각형을 잡는다. 스크롤이 없다.
 * - 아래 두 줄: [돋보기 · 판 크기 · 형세 보기 · 추천 수] / [착수 칸 · 기권 · 통과 · 무르기]
 *
 * ⚠️ **스크롤이 없어야 한다** — 판 위 끌기는 착수다(함정 44). 넘치는 것이 생기면 판이 줄지 화면이
 *   늘지 않는다(판이 `weight(1f)`).
 * ⚠️ 형세 그래프는 판 위에 **떠서** 열린다 — 판을 밀어내면 판 크기가 출렁인다.
 * ⚠️ `바둑판 여백`은 이 배치에서 판 둘레에 [WideBoardInset]을 준다 — 판이 세로에 묶여 있어
 *   폰 배치의 '가로 여백 되찾기'로는 아무 차이가 없기 때문이다.
 */
@Composable
private fun WidePlayArrangement(
    screenState: GameScreenState,
    currentTurnPlayer: StoneColor,
    isBoardMaxSize: Boolean,
    onToggleMagnifier: () -> Unit,
    onToggleBoardSize: () -> Unit,
    tentativeMove: BoardCoordinate?,
    blackTotalMillis: Long,
    whiteTotalMillis: Long,
    showMoveQualityLegend: Boolean,
    onScoreGraphExpandedChange: (Boolean) -> Unit,
    onEvent: (GameUiEvent) -> Unit,
    menuButton: @Composable () -> Unit,
    board: @Composable (Modifier) -> Unit,
) {
    val strings = LocalUiStrings.current
    val turn = currentTurnPlayer.takeIf { !screenState.isGameEnded }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            menuButton()
            CompactSeatCard(
                isActiveTurn = turn == StoneColor.Black,
                stoneGlyph = "●",
                stoneGlyphColor = Color.Black,
                label = strings.sideLabel(screenState.playerSetup.black, StoneColor.Black),
                elapsedMillis = blackTotalMillis,
                capturedCount = screenState.gameState.capturedBy(StoneColor.Black),
                capturesLabel = strings.captures,
                stacked = false,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            WideScoreSummary(
                moveCountText = "${strings.moveCountPrefix} ${screenState.gameState.moves.size}${strings.moveCountSuffix}",
                snapshots = screenState.score.snapshots,
                whiteWinRate = screenState.score.estimate?.whiteWinRate,
                isGraphExpanded = screenState.score.isGraphExpanded,
                onGraphExpandedChange = onScoreGraphExpandedChange,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            CompactSeatCard(
                isActiveTurn = turn == StoneColor.White,
                stoneGlyph = "○",
                stoneGlyphColor = Color.Gray,
                label = strings.sideLabel(screenState.playerSetup.white, StoneColor.White),
                elapsedMillis = whiteTotalMillis,
                capturedCount = screenState.gameState.capturedBy(StoneColor.White),
                capturesLabel = strings.captures,
                stacked = false,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EngineUnavailableBadge(
                    availability = screenState.engine.availability,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                board(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (isBoardMaxSize) Modifier else Modifier.padding(WideBoardInset)),
                )
                if (showMoveQualityLegend) {
                    Spacer(modifier = Modifier.height(4.dp))
                    MoveQualityLegend()
                }
            }
            if (screenState.score.isGraphExpanded) {
                ScoreTimelineGraph(
                    snapshots = screenState.score.snapshots,
                    capturedByBlack = screenState.gameState.capturedBy(StoneColor.Black),
                    capturedByWhite = screenState.gameState.capturedBy(StoneColor.White),
                    whiteWinRate = screenState.score.estimate?.whiteWinRate,
                    isExpanded = true,
                    onExpandedChange = onScoreGraphExpandedChange,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
            }
        }

        // ⭐ **다섯을 한 줄로**(2026-09-12 사용자 지시 — *"폴드 사이즈에서는 버튼 5개 나란히 한 줄"*).
        // #143이 토글 둘과 착수 칸을 빼면서 아래가 헐거워졌고, 한 줄로 접은 만큼(48dp + 틈 8dp) **판이 커진다.**
        // ⚠️ 폰 배치는 그대로 두 줄이다 — 폭이 380dp 남짓이라 다섯이면 칸당 70dp도 안 돼 라벨이 잘린다.
        GameActionButtonHost(screenState = screenState, onEvent = onEvent) { slots ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 확인 모드를 되살리면(플래그) 여섯 칸이 된다 — 그때는 칸당 100dp 남짓으로 좁아지지만
                // 기본 경로가 아니므로 줄을 나누지 않고 그대로 둔다.
                playConfirmSlot { modifier ->
                    PlaySlot(
                        screenState = screenState,
                        tentativeMove = tentativeMove,
                        onEvent = onEvent,
                        horizontal = true,
                        modifier = modifier.weight(2f),
                    )
                }?.invoke(this)
                slots.eval(Modifier.weight(1f))
                slots.topMoves(Modifier.weight(1f))
                slots.resign(Modifier.weight(1f))
                slots.pass(Modifier.weight(1f))
                slots.undo(Modifier.weight(1f))
            }
        }
    }
}

/**
 * **좌우 기둥 배치**(백로그 #141 L1, 2026-09-12 사용자 확정) — 가로로 돌리면 **좌우**가 남는다.
 * - 얇은 위 줄: ☰ · 수순/점수(누르면 그래프) · 판 토글 둘
 * - 가운데: 왼쪽 기둥 | 판(남는 폭·높이) | 오른쪽 기둥
 *   · 왼쪽 — 흑 좌석 · 형세 보기 · 추천 수
 *   · 오른쪽 — 백 좌석 · 착수 칸 · 무르기 · 통과 · 기권
 *
 * ⚠️ **판은 가운데 칸이 통째로 가진다**(`weight(1f)`) — 기둥은 고정 폭([WideColumnWidth])이라 판이
 *   남는 폭을 전부 쓰고, `GoBoard`의 `min(가로, 세로)`가 거기서 정사각형을 잡는다. 스크롤은 없다(함정 44).
 * ⚠️ 좌석 카드는 **세 줄**로 접는다(`stacked`) — 기둥 폭에서 시계와 사석을 한 줄에 쓰면 잘린다.
 * ⚠️ 버튼 순서는 폰 배치의 좌→우를 **위→아래**로 옮긴 것이다(무르기·통과·기권이 아니라
 *   기권·통과·무르기 순서로 두면, 가장 자주 쓰는 무르기가 맨 아래로 가 손이 먼 자리에 놓인다).
 */
@Composable
private fun WideColumnsArrangement(
    screenState: GameScreenState,
    currentTurnPlayer: StoneColor,
    isBoardMaxSize: Boolean,
    onToggleMagnifier: () -> Unit,
    onToggleBoardSize: () -> Unit,
    tentativeMove: BoardCoordinate?,
    blackTotalMillis: Long,
    whiteTotalMillis: Long,
    showMoveQualityLegend: Boolean,
    onScoreGraphExpandedChange: (Boolean) -> Unit,
    onEvent: (GameUiEvent) -> Unit,
    menuButton: @Composable () -> Unit,
    board: @Composable (Modifier) -> Unit,
) {
    val strings = LocalUiStrings.current
    val turn = currentTurnPlayer.takeIf { !screenState.isGameEnded }
    GameActionButtonHost(screenState = screenState, onEvent = onEvent) { slots ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                menuButton()
                WideScoreSummary(
                    moveCountText = "${strings.moveCountPrefix} ${screenState.gameState.moves.size}${strings.moveCountSuffix}",
                    snapshots = screenState.score.snapshots,
                    whiteWinRate = screenState.score.estimate?.whiteWinRate,
                    isGraphExpanded = screenState.score.isGraphExpanded,
                    onGraphExpandedChange = onScoreGraphExpandedChange,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.width(WideColumnWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactSeatCard(
                        isActiveTurn = turn == StoneColor.Black,
                        stoneGlyph = "●",
                        stoneGlyphColor = Color.Black,
                        label = strings.sideLabel(screenState.playerSetup.black, StoneColor.Black),
                        elapsedMillis = blackTotalMillis,
                        capturedCount = screenState.gameState.capturedBy(StoneColor.Black),
                        capturesLabel = strings.captures,
                        stacked = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    slots.eval(Modifier.fillMaxWidth())
                    slots.topMoves(Modifier.fillMaxWidth())
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EngineUnavailableBadge(
                            availability = screenState.engine.availability,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        board(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .then(if (isBoardMaxSize) Modifier else Modifier.padding(WideBoardInset)),
                        )
                        if (showMoveQualityLegend) {
                            Spacer(modifier = Modifier.height(4.dp))
                            MoveQualityLegend()
                        }
                    }
                    if (screenState.score.isGraphExpanded) {
                        ScoreTimelineGraph(
                            snapshots = screenState.score.snapshots,
                            capturedByBlack = screenState.gameState.capturedBy(StoneColor.Black),
                            capturedByWhite = screenState.gameState.capturedBy(StoneColor.White),
                            whiteWinRate = screenState.score.estimate?.whiteWinRate,
                            isExpanded = true,
                            onExpandedChange = onScoreGraphExpandedChange,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth(),
                        )
                    }
                }

                Column(
                    modifier = Modifier.width(WideColumnWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactSeatCard(
                        isActiveTurn = turn == StoneColor.White,
                        stoneGlyph = "○",
                        stoneGlyphColor = Color.Gray,
                        label = strings.sideLabel(screenState.playerSetup.white, StoneColor.White),
                        elapsedMillis = whiteTotalMillis,
                        capturedCount = screenState.gameState.capturedBy(StoneColor.White),
                        capturesLabel = strings.captures,
                        stacked = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (FeatureFlags.isPlayConfirmModeEnabled) {
                        PlaySlot(
                            screenState = screenState,
                            tentativeMove = tentativeMove,
                            onEvent = onEvent,
                            horizontal = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    slots.undo(Modifier.fillMaxWidth())
                    slots.pass(Modifier.fillMaxWidth())
                    slots.resign(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** 좌우 기둥의 폭. 좌석 카드 세 줄과 `형세 보기 (30)` 라벨이 들어가는 최소치에서 잡았다. */
private val WideColumnWidth = 104.dp

/**
 * 착수 칸을 **플래그 뒤에** 둔다(백로그 #143) — 꺼져 있으면 아래 줄에 칸이 아예 생기지 않는다.
 * 코드는 지우지 않았으므로 `FeatureFlags.isPlayConfirmModeEnabled`를 켜면 그대로 돌아온다.
 */
private fun playConfirmSlot(
    content: @Composable RowScope.(Modifier) -> Unit,
): (@Composable RowScope.() -> Unit)? =
    if (FeatureFlags.isPlayConfirmModeEnabled) {
        { content(Modifier) }
    } else {
        null
    }

/** 넓은 배치에서 `바둑판 여백`일 때 판 둘레 여백. */
private val WideBoardInset = 16.dp

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

/**
 * 대국 조작 버튼 **다섯 칸**. 게이팅(1회권·광고·프리미엄)·팝업은 [GameActionButtonHost]가 들고,
 * **어디에 놓을지는 배치가 정한다** — 폰·위아래 배치는 두 줄로, 좌우 기둥(L1)은 판 양옆 기둥으로.
 *
 * ⚠️ 칸을 두 벌로 적지 말 것. 잠금 테두리·잔량 표기·1회권 차감이 버튼마다 붙어 있어서, 배치마다
 * 다시 적으면 한쪽만 고쳐지는 사고가 난다(#44·#66이 그 자리다).
 */
internal class GameActionSlots internal constructor(
    val eval: @Composable (Modifier) -> Unit,
    val topMoves: @Composable (Modifier) -> Unit,
    val resign: @Composable (Modifier) -> Unit,
    val pass: @Composable (Modifier) -> Unit,
    val undo: @Composable (Modifier) -> Unit,
)

@Composable
private fun GameActionButtonHost(
    screenState: GameScreenState,
    onEvent: (GameUiEvent) -> Unit,
    content: @Composable (GameActionSlots) -> Unit,
) {
    val strings = LocalUiStrings.current
    val premium = LocalPremiumUiState.current
    val context = LocalContext.current
    var showResignConfirm by remember { mutableStateOf(false) }
    var showPremiumUpsellDialog by remember { mutableStateOf(false) }
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
                    else -> showPremiumUpsellDialog = true
                }
            }
        }
    }

    PremiumUpsellDialogHost(
        visible = showPremiumUpsellDialog,
        onDismiss = { showPremiumUpsellDialog = false },
    )

    // ⚠️ **무르기의 인게임 "영구 활성화" 팝업이 여기 있었다 — 지웠다**(백로그 #66, 2026-09-03).
    // 확인 버튼이 `premium.claim(FeatureId.Undo)`로 무르기를 **1일차부터 영구 지급**하고 있었고,
    // 그래서 3일차 출석 보상(#55)이 하는 일이 없었다. 이 자리의 주석은 그 경로를 "방어적 폴백"
    // 이라고 적고 있었는데, 근거로 든 전제 **둘 다 그 사이 무너져 있었다**: 출석 1일차 자동
    // 지급은 #14로 없어졌고(`AttendanceCheckInCoordinator`는 이제 지급하지 않는다), 무르기
    // 회차도 1일차 → 3일차로 옮겨졌다. 즉 폴백이 아니라 **1·2일차 사용자가 반드시 만나는
    // 정상 경로**였다.
    //
    // ⚠️ **잃은 것이 하나 있고, 그것을 알고 지웠다**(2026-09-03 사용자 결정). 이 팝업은
    // `PremiumStateStore.load()`가 기기 시계 이상 등으로 상태를 기본값 폴백했을 때 무르기를
    // 되찾는 **유일한 자력 복구 수단**이기도 했다 — 출석 쪽에는 "3일차 지급 완료"가 남아 있어
    // 자동 재지급되지 않는다. 폴백 조건이 매우 좁고(광고 시청 후 기기 시계 되돌림, 또는 저장
    // JSON 파싱 실패) 비공개 테스트 권한은 #63 초기화로 이미 밀렸으므로 **감수하기로 했다.**
    // → **이것은 버그가 아니라 결정이다.** 복구 경로가 없다는 이유로 다시 발행하지 말 것.

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

    val slots = GameActionSlots(
        eval = { modifier ->
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
                    modifier = modifier.guideTarget(GuideTarget.Eval),
                    premiumLocked = evalAccess !is FeatureAccess.Allowed && !tapIsFree(FeatureId.Eval),
                )
            }
        },
        topMoves = { modifier ->
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
                    modifier = modifier.guideTarget(GuideTarget.TopMoves),
                    premiumLocked = topMovesAccess !is FeatureAccess.Allowed && !tapIsFree(FeatureId.TopMoves),
                )
            }
        },
        resign = { modifier ->
            // 기권 / 새 게임 버튼
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
                modifier = modifier,
                label = if (screenState.isGameEnded) strings.newGameAction else strings.resign,
            )
        },
        pass = { modifier ->
            val passAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.Pass }
            if (passAction != null) {
                SingleActionButton(
                    action = passAction,
                    label = strings.pass,
                    onEvent = onEvent,
                    modifier = modifier,
                )
            }
        },
        undo = { modifier ->
            // 무르기 (Undo) 버튼 — **다른 프리미엄 기능과 같은 규칙으로 그린다**(백로그 #66).
            // 잠긴 동안 금색 테두리가 붙고, 열리면 평범한 버튼으로 돌아간다. 무르기가 열리는 길은
            // 셋이고 `resolve`가 그 셋을 이미 한 값으로 접어 준다:
            //   ⓐ 프리미엄이 지금 유효(구독/영구) → `Allowed(Purchase)`
            //   ⓑ 광고 1시간 활성 → `Allowed(AdGrant)`
            //   ⓒ 3일차 출석 보상으로 영구 획득 → `Allowed(Claimed)`
            // 셋 다 `Allowed`라 테두리가 저절로 사라진다 — **상태 전이를 따로 배선할 것이 없다.**
            // ⚠️ 라벨에는 아무 표시도 붙이지 않는다. 무르기에 무제한 표시를 달지 않기로 한 것은
            // 사용자 확정 사항이고(`UiStrings.featureButtonLabel` KDoc), 게다가 이 버튼은
            // `ActionButtonMinHeight`(48dp) **고정 높이**라 줄이 늘면 폰트 배율에서 잘린다.
            val undoAction = screenState.actionButtons.firstOrNull { it.role == GameActionButtonRole.Undo }
            if (undoAction != null) {
                val undoAccess = premium.resolve(FeatureId.Undo)
                SingleActionButton(
                    action = undoAction,
                    label = strings.undo,
                    onEvent = { event -> featureGated(undoAccess) { onEvent(event) } },
                    modifier = modifier,
                    // 형세·추천과 같은 관용구다. 다만 그 둘이 함께 보는 `tapIsFree`는 여기 없다 —
                    // 무르기에는 1회권이 없어(`ConsumableCatalog`에 `FeatureUse(Undo)`가 없다)
                    // 언제나 false이므로, 붙이면 읽는 사람만 헷갈린다.
                    premiumLocked = undoAccess is FeatureAccess.Locked,
                )
            }
        },
    )

    content(slots)
}

/**
 * 두 줄 배치 — 폰 배치와 위아래 넓은 배치(P1)가 함께 쓴다.
 */
@Composable
private fun GameActionButtons(
    screenState: GameScreenState,
    onEvent: (GameUiEvent) -> Unit,
    // 넓은 배치(#141)가 두 줄 **맨 앞**에 끼워 넣는 칸 — 첫 줄엔 판 토글 둘, 둘째 줄엔 착수 칸.
    // 폰 배치는 `null`(토글은 판 위, 착수 칸은 상태판 가운데에 있다). 게이팅·팝업은 두 배치가 공유한다.
    firstRowLeading: (@Composable RowScope.() -> Unit)?,
    secondRowLeading: (@Composable RowScope.() -> Unit)?,
) {
    GameActionButtonHost(screenState = screenState, onEvent = onEvent) { slots ->
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
                firstRowLeading?.invoke(this)
                slots.eval(Modifier.weight(1f))
                slots.topMoves(Modifier.weight(1f))
            }

            // [2행] 기권(Resign/New Game), 통과(Pass), 무르기(Undo) — 기본 기능 버튼 3열
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                secondRowLeading?.invoke(this)
                slots.resign(Modifier.weight(1f))
                slots.pass(Modifier.weight(1f))
                slots.undo(Modifier.weight(1f))
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

