package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProfile
import com.worksoc.goaicoach.application.engine.EngineBenchmarkProgress
import com.worksoc.goaicoach.application.analysis.JsonPositionAnalysisCacheOpeningInitialMoveCount
import com.worksoc.goaicoach.application.analysis.JsonPositionAnalysisCacheOpeningMaxMoveCount
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.presentation.shouldCollapseMenuAfterEvent
import com.worksoc.goaicoach.application.guide.GuideSurface

@Composable
internal fun GoCoachContent(
    screenState: GameScreenState,
    benchmarkProgress: EngineBenchmarkProgress?,
    benchmarkResult: EngineBenchmarkProfile?,
    onScoreGraphExpandedChange: (Boolean) -> Unit,
    onFinalJudgementReview: () -> Unit,
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
    turnTimeState: GameSessionTurnTimeState,
    onEvent: (GameUiEvent) -> Unit,
) {
    val strings = LocalUiStrings.current
    val cacheOptimizationPrompt = if (benchmarkProgress == null && benchmarkResult == null) {
        screenState.cacheOptimizationPrompt
    } else {
        null
    }
    var isDisplayMenuExpanded by remember { mutableStateOf(false) }
    val onMenuEvent: (GameUiEvent) -> Unit = { event ->
        onEvent(event)
        if (shouldCollapseMenuAfterEvent(event)) {
            isDisplayMenuExpanded = false
        }
    }
    var dismissedFinalJudgementKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(screenState.isGameEnded) {
        if (!screenState.isGameEnded) {
            dismissedFinalJudgementKey = null
        }
    }
    val finalJudgementKey = screenState.finalScoreJudgement?.dialogKey(screenState.gameState.moves.size)
    val finalJudgementToShow = screenState.finalScoreJudgement
        ?.takeIf { finalJudgementKey != null && dismissedFinalJudgementKey != finalJudgementKey }
    val dismissFinalJudgement = { dismissedFinalJudgementKey = finalJudgementKey }

    // ⚠️ **벤치마크 팝업은 여기서 그리지 않는다**(2026-09-10). 이 화면은 `InGame`에서만
    // 컴포즈되는데 '엔진 성능 측정' 버튼은 **설정 화면**에 있어서, 여기서 그리면 설정에서 누른
    // 사용자는 막힘·진행·결과·실패 **넷 다** 보지 못한다. 셸(`GoCoachApp`)이 목적지와 무관하게
    // 한 번 그린다(`EngineBenchmarkOverlays`).
    // ⚠️ 그래도 `benchmarkProgress`/`benchmarkResult`는 계속 받는다 — **그리기 위해서가 아니라,
    // 벤치마크가 떠 있는 동안 이 화면의 다른 팝업을 미루기 위해서**다(아래 세 곳).

    if (cacheOptimizationPrompt != null) {
        CacheOptimizationPromptDialog(
            title = strings.cacheOptTitle,
            message = strings.cacheOptBody(
                initialCount = JsonPositionAnalysisCacheOpeningInitialMoveCount,
                maxCount = JsonPositionAnalysisCacheOpeningMaxMoveCount,
                moveCount = cacheOptimizationPrompt.moveCount,
                targetCount = cacheOptimizationPrompt.targetCount,
            ),
            strings = strings,
            onAccept = { onEvent(GameUiEvent.AcceptCacheOptimizationPrompt) },
            onDismiss = { onEvent(GameUiEvent.DismissCacheOptimizationPrompt) },
        )
    }

    if (finalJudgementToShow != null && benchmarkProgress == null && benchmarkResult == null) {
        FinalJudgementDialog(
            judgement = finalJudgementToShow,
            strings = strings,
            onDismiss = dismissFinalJudgement,
            onReview = {
                onFinalJudgementReview()
                dismissFinalJudgement()
            },
            onNewGame = {
                dismissFinalJudgement()
                onEvent(GameUiEvent.StartConfiguredGame)
            },
        )
    }

    // ⚠️ 판을 한 화면에 맞추려고 **세 높이**를 잰다(백로그 #139 1차, 계산은 `fittedBoardMaxHeightPx`).
    //   · 뷰포트 — `verticalScroll` **앞**에서 잰다. 스크롤 안에서는 잴 높이가 없다(함정 45).
    //   · 내용 전체 — `verticalScroll` **뒤**, 그리고 `wrapContentHeight` **뒤**에서 잰다(padding까지 포함).
    //     ⚠️ `verticalScroll`은 `fillMaxSize`의 **최소 높이를 그대로 넘긴다** — 그래서 내용이 화면보다
    //     짧으면 뷰포트 높이로 늘어나 재진다. 그러면 상한이 지금 판 크기에 **고정**되어, 그래프를 접거나
    //     화면이 커져도 판이 다시 커지지 않는다(실측: 그래프 접은 뒤 판이 바닥 크기에 갇혔다).
    //     `wrapContentHeight`가 그 최소를 풀어 **본래 높이**를 재게 한다. 보이는 배치는 같다(위 정렬).
    //   · 판 — `GamePlaySection`이 알려 준다.
    // 보통 폰에서는 상한이 가로폭보다 커서 판이 그대로다 — 줄어드는 것은 넘칠 때(폴드 안쪽 화면 등)뿐이다.
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var contentHeightPx by remember { mutableIntStateOf(0) }
    var boardHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val boardMaxHeight = fittedBoardMaxHeightPx(
        viewportPx = viewportHeightPx,
        contentPx = contentHeightPx,
        boardPx = boardHeightPx,
        minBoardPx = with(density) { MinFittedBoardSide.roundToPx() },
    )?.let { px -> with(density) { px.toDp() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .onSizeChanged { size -> viewportHeightPx = size.height }
            .verticalScroll(rememberScrollState())
            .wrapContentHeight(align = Alignment.Top)
            .onSizeChanged { size -> contentHeightPx = size.height }
            .padding(GameScreenEdgePadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameHeaderSection(
            screenState = screenState,
            isDisplayMenuExpanded = isDisplayMenuExpanded,
            onDisplayMenuExpandedChange = { expanded -> isDisplayMenuExpanded = expanded },
        )

        if (isDisplayMenuExpanded) {
            AlertDialog(
                onDismissRequest = { isDisplayMenuExpanded = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth(0.9f),
                title = {
                    Text(
                        // ⚠️ **`matchSetup`("대국 설정")이 아니라 `settingsTitle`("설정")이다**(백로그 #110).
                        // 이 다이얼로그에는 언어·표시 옵션·탐색 시간·진단 액션뿐이고 **플레이어 설정과
                        // 계가/판 설정은 없다** — #76이 사문화된 `showSettings` 블록과 함께 지웠다.
                        // 그 둘을 바꾸는 곳은 대국 설정 로비(대국 시작 전)와 홈 → `설정` 화면(대국 중에도
                        // 가능, 종국 전에는 판 크기·접바둑 잠김)이다.
                        // ⚠️ `matchSetup` 자체를 건드리지 말 것 — 나머지 두 호출부는 맞다
                        // (`GameSetupLobby.kt`가 진짜 대국 설정 화면, `SettingsScreen.kt`가 그 섹션 헤더).
                        text = strings.settingsTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExpandedGameMenuSection(
                            screenState = screenState,
                            selectedLanguage = selectedLanguage,
                            onLanguageChange = onLanguageChange,
                            onEvent = onMenuEvent,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isDisplayMenuExpanded = false }) {
                        Text(strings.close)
                    }
                }
            )
        }

        GamePlaySection(
            screenState = screenState,
            boardMaxHeight = boardMaxHeight,
            onBoardHeightChanged = { height -> boardHeightPx = height },
            onScoreGraphExpandedChange = onScoreGraphExpandedChange,
            turnTimeState = turnTimeState,
            onEvent = onEvent,
        )
    }

    // ⑤ 대국 화면 — 첫돌이가 **버튼마다 하나씩** 안내한다(백로그 #128, 사용자 확정 ⓒ: 첫 대국에
    // 한 번만 / 2026-09-09 지시: 버튼별로 쪼개고 버튼 옆에서, 버튼에 동그라미).
    //
    // ⚠️ **이 호출이 `Column` 뒤에 있는 이유** — 실기에서 동그라미는 그려지는데 말풍선이 보이지
    // 않았다. 이 화면의 형제들은 `MainActivity`의 `Box` 자식이라 **나중에 온 것이 위에 그려진다**:
    // 앞에 두면 반상이 말풍선을 덮는다. `fillMaxSize` 오버레이가 레이아웃을 밀지 않는 것도
    // 부모가 `Box`이기 때문이다(`Column`이었다면 판을 밀어냈을 것이다).
    //
    // ⚠️ **`blocked`가 이 앵커의 핵심이다.** 위 사슬의 팝업들은 **별도 윈도우**라 창 안 카드를
    // 덮는다 — 덮인 채 "봤음"으로 기록되면 사용자 기준으로는 **0번** 보게 되고, 그것이 사용자
    // 확정 ⓒ를 조용히 무효화한다. 그래서 여섯을 전부 센다.
    // ⚠️ `isGameEnded`를 반드시 넣는다 — `GoCoachApp`의 **끝난 대국 복원** 경로가 그 상태로 들어오고,
    //   그 자리에서 도구를 소개하는 것은 뜻이 없다. (`moves.isEmpty()`를 트리거로 쓰지 말 것 —
    //   접바둑 첫 대국은 AI가 먼저 두므로 그 조건이 첫 프레임에 이미 거짓이다.)
    // ⚠️ **엔진 멈춤 팝업은 이 목록이 볼 수 없다** — 그 상태는 `GamePlaySection` **안**에 있다.
    //   설계 심사가 정직하게 신고한 사각지대이고, 그 팝업이 뜬 채 카드가 함께 뜨면 카드는 덮인다.
    //   다만 카드는 **누를 때만** 기록하므로 소진되지는 않는다(그 비대칭이 `GuideCard`의 KDoc에 있다).
    // ⚠️ 이 앵커는 `CompositionLocalProvider` **안**에 있어야 한다 — 밖에서 그리면
    //   `LocalConsumableUiState`·`LocalUiStrings` 같은 것들이 조용히 기본값으로 잡힌다.
    GuideAnchor(
        surface = GuideSurface.InGame,
        blocked = GuideBlockingOverlays.isShowing ||
            screenState.isGameEnded ||
            benchmarkProgress != null ||
            benchmarkResult != null ||
            cacheOptimizationPrompt != null ||
            finalJudgementToShow != null ||
            isDisplayMenuExpanded,
        toolLabels = GuideToolLabels(
            // ⚠️ **판 위에 적힌 그대로 인용한다** — 실기에서 처음에 어긋났다: `magnifierWindowSizeLabel`
            // ("돋보기 창 크기")은 **설정 화면**의 라벨이고 판 위 토글은 `착수 돋보기`였다.
            // ⚠️ 바둑판 쪽은 토글 라벨(`바둑판 최대`/`바둑판 여백`)이 **상태에 따라 바뀌므로**
            //   인용하지 않고 **주체 이름**만 쓴다 — 여백 상태로 들어온 사용자에게도 참이어야 한다.
            magnifier = playMagnifierLabelFor(strings.language),
            boardSubject = boardSizeSubjectFor(strings.language),
            eval = strings.eval,
            topMoves = strings.topMovesAction,
        ),
    )
}

/**
 * 대국 화면 최상위 여백. 보드 "최대 크기" 모드가 **정확히 이 값을 되찾아** 화면 폭 끝까지
 * 그리므로(#38, `GamePlaySection`), 두 값이 어긋나면 보드가 화면 밖으로 나가거나 여백이 남는다.
 * 그래서 숫자를 두 곳에 적지 않고 여기 하나로 둔다.
 */
internal val GameScreenEdgePadding = 16.dp
