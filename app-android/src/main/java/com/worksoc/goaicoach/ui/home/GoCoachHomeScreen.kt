package com.worksoc.goaicoach.ui.home

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.guide.GuideSurface
import com.worksoc.goaicoach.application.guide.GuideTarget
import com.worksoc.goaicoach.application.premium.state.FeatureId
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.isRankMeasure
import com.worksoc.goaicoach.persistence.GameHistoryStore
import com.worksoc.goaicoach.persistence.ReferenceGameHistoryId
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.customRank
import com.worksoc.goaicoach.shared.policy.customRankFallbackTier
import com.worksoc.goaicoach.ui.board.GoBoard
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppMotion
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.designsystem.BotCharacterSquareIcon
import com.worksoc.goaicoach.ui.designsystem.HomeLogoPalette
import com.worksoc.goaicoach.ui.designsystem.HomeTabBarPalette
import com.worksoc.goaicoach.ui.designsystem.PremiumGold
import com.worksoc.goaicoach.ui.designsystem.PremiumGoldDeep
import com.worksoc.goaicoach.ui.designsystem.PremiumGoldGradient
import com.worksoc.goaicoach.ui.designsystem.RankTierBadge
import com.worksoc.goaicoach.ui.foundation.FeatureFlags
import com.worksoc.goaicoach.ui.guide.GuideAnchor
import com.worksoc.goaicoach.ui.guide.GuideBlockingOverlays
import com.worksoc.goaicoach.ui.guide.guideTarget
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.StudyCategory
import com.worksoc.goaicoach.ui.l10n.UiStrings
import com.worksoc.goaicoach.ui.l10n.gameRecordsTitleFor
import com.worksoc.goaicoach.ui.l10n.homePageLabTabFor
import com.worksoc.goaicoach.ui.l10n.homePagePlayTabFor
import com.worksoc.goaicoach.ui.l10n.keepWordsWhole
import com.worksoc.goaicoach.ui.l10n.kgsRankLabelFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureOverwriteWarningFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureSubtitleFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureTitleFor
import com.worksoc.goaicoach.ui.l10n.studyBasicsTitleFor
import com.worksoc.goaicoach.ui.monetization.LocalPremiumUiState
import com.worksoc.goaicoach.ui.monetization.PremiumSubscribeDialog
import com.worksoc.goaicoach.ui.quickactions.AppQuickActions
import com.worksoc.goaicoach.vision.SharedBoardPhotoInbox
import kotlinx.coroutines.launch

/**
 * 0 Depth: 홈 — **세 페이지를 옆으로 넘긴다**(백로그 #280, 사용자 2026-10-22): 내 정보 ‹ **대국** › 학습 하기.
 * - 윗줄(빠른 버튼 · 설정)과 아래 탭 셋은 세 페이지 공통이라 넘겨도 움직이지 않는다. 탭은 지금 페이지를 보이고, 누르면 그 페이지로 간다.
 * - 켜면 늘 가운데(대국)다. 다른 화면에 갔다 오면 떠났던 페이지로 돌아온다([HomePageMemory]).
 * - 뒤로가기: 옆 페이지 → 가운데, 가운데 → 앱을 나간다(셸의 `BackHandler`는 홈에서 꺼져 있다).
 * - 내 정보 · 학습 하기의 내용은 셸이 넘긴다 — 그 화면들은 홈보다 같거나 위층이라 홈이 import하지 못한다(`UiPackageCycleRatchetTest`).
 *   한 줄 안내([topNotice])를 넘기는 것과 같은 방식이다.
 */
@Composable
internal fun GoCoachHomeScreen(
    onStartMatchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onGameHistoryClick: () -> Unit,
    /** 「바둑판 사진 분석」 — 대국 페이지 오른쪽 아래의 카메라 단추 · 실험실 페이지의 카드(#280), 그리고 다른 앱에서 「공유」로 받은 사진이 있을 때. */
    onBoardScanClick: () -> Unit,
    /** 대국 페이지의 타일 셋(#280, 사용자 2026-10-23) — 「명국 기보」 · 「사활 풀기」 · 「기초 학습」. 셸이 그 목적지로 보낸다. */
    onGameRecordsClick: () -> Unit,
    onLifeAndDeathClick: () -> Unit,
    onBasicsClick: () -> Unit,
    hasResumableSession: Boolean,
    /** 이어 둘 판의 한 줄 요약(#287 — `resumeSummaryFor`) — 이어하기 알약의 둘째 줄. 없으면 첫 줄만. */
    resumeSummary: String?,
    onResumeClick: () -> Unit,
    /**
     * 이어 둘 판을 지운다 — 「덮어쓰기」 경고를 확인한 순간(#287 뒤, 사용자 2026-10-24). 일반 대국은 셸의 [onStartMatchClick]이 같은 일을 하고,
     * 승급 대국은 확인 뒤 설정 창이 뜨므로 여기서 지운다 — 설정 창을 닫고 나와도 두 메뉴가 같다(이어하기 버튼이 사라진다).
     */
    onDiscardResumableSession: () -> Unit,
    /**
     * 「승급 대국」(백로그 #219)을 시작한다 — 설정 창에서 고른 진영·판 크기(와 최초 1회의 시작 기력)를 준다.
     * 창과 그 상태는 이 화면이 들고, 셸은 시작하는 일만 한다(셸의 훅 예산, 함정 3).
     */
    onStartRankMeasureGame: (RankMeasureStart) -> Unit,
    /**
     * 백로그 #181 — "캐릭터 대국" 카드 아이콘이 마지막으로 고른 AI 캐릭터를 보여주기 위해 받는다.
     * ⚠️ 새 상태 훅이 아니다 — `GoCoachApp.kt`가 이미 들고 있는 `screenState.playerSetup`(파생값)을
     * 그대로 흘려보낸 것뿐이다(셸의 훅 예산 42/42, 함정 3).
     */
    playerSetup: PlayerSetup,
    /**
     * 엔진 팩을 받는 동안의 한 줄 안내(#248) — 받기가 없으면 아무것도 그리지 않는다. 셸이 `ui.setup`의 배너를 넘긴다
     * (홈과 대국 설정은 같은 층이라 서로 import하지 못한다 — `UiPackageCycleRatchetTest`). 대국 페이지에만 선다(#280 ⑥).
     */
    topNotice: @Composable (Modifier) -> Unit,
    /** 왼쪽 페이지 「내 정보」(#280) — 셸이 `ui.account`의 화면을 넘긴다. */
    myInfoPage: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    var showOverwriteWarningDialog by remember { mutableStateOf(false) }
    // 승급 대국(백로그 #219) — 설정 창과, 진행 중인 대국이 있을 때 먼저 묻는 덮어쓰기 경고(저장 슬롯은 하나라 이 대국도
    // 그것을 밀어낸다). 제목은 「캐릭터 대국」의 것을 그대로 쓰고, 본문은 끝 문장만 다르다(`rankMeasureOverwriteWarningFor`).
    val rankMeasure = LocalRankMeasureUiState.current
    var showRankMeasureDialog by remember { mutableStateOf(false) }
    var showRankMeasureOverwriteWarning by remember { mutableStateOf(false) }
    // 백로그 #182 — 구독 여부로 로고·타이틀 색을 가른다. `isPurchased`만 본다(광고 1시간은
    // 제외, [[premium-character-unlock-policy]]와 같은 결). `PremiumSubscriptionCard`가
    // "구독 중" 문구를 결정하는 것과 같은 기준이라 새 상태가 아니라 CompositionLocal을 그대로 읽는다.
    val subscribed = LocalPremiumUiState.current.isPurchased
    val showsPremiumPrompt = !subscribed && FeatureFlags.isPurchaseEnabled
    var showSubscribeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 다른 앱에서 「공유」로 받은 바둑판 사진이 있으면 사진 분석으로 간다(백로그 #263) — 사진은 그 화면이 꺼낸다.
    // 셸이 아니라 여기서 하는 것은 셸의 상태 훅 여유가 0이라서다(함정 3). 다른 화면에 있으면 홈으로 돌아올 때 열린다. 어느 페이지에 있든 같다.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (SharedBoardPhotoInbox.hasPhoto(context)) onBoardScanClick()
    }

    // 지금 페이지는 이 화면이 든다 — 셸에 두면 셸 상태 원장에 한 칸이 는다(함정 3). 홈을 떠날 때 잃지 않게 [HomePageMemory]에 적어 둔다.
    val pagerState = rememberPagerState(initialPage = HomePageMemory.page.ordinal) { HomePage.entries.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page -> HomePageMemory.page = HomePage.entries[page] }
    }
    val scope = rememberCoroutineScope()
    // 탭을 누르면 Expressive 스프링으로 넘어간다(#281 A안) — 아래 탭의 알약이 페이지 위치를 따라가므로 알약도 같은 스프링으로 미끄러진다.
    val showPage: (HomePage) -> Unit = { page -> scope.launch { pagerState.animateScrollToPage(page.ordinal, animationSpec = AppMotion.defaultSpatial()) } }
    // 옆 페이지의 뒤로가기는 가운데로(#280 ⑦) — 예측 뒤로가기(#281): 뒤로 제스처를 끄는 동안 가운데 페이지가 손가락만큼 들어오고,
    // 놓으면 가운데로 넘어가며, 거두면 제자리로 돌아온다. 제스처의 진행을 주지 않는 기기(Android 13 이하 · 버튼 내비)에서는 곧바로 가운데로 간다.
    // ⚠️ 끄는 동안의 비율은 [PredictiveBackPeek]까지 — 반(0.5)을 넘으면 `currentPage`가 바뀌어 이 처리기가 꺼진다.
    // ⚠️ 마지막 넘김은 이 처리기의 코루틴이 아니라 **화면의 스코프**에서 돌린다 — 넘어가는 도중 가운데가 되면 처리기가 꺼지고, 꺼질 때
    //   `PredictiveBackHandlerCallback.setIsEnabled(false)`가 그 코루틴을 취소한다(activity-compose 1.10.1 바이트코드로 확인). 처리기 안에서 넘기면
    //   반쯤 가다 멈추고, 취소를 「거둔 제스처」로 읽으면 옆 페이지로 되돌아간다.
    PredictiveBackHandler(enabled = pagerState.currentPage != HomePage.Play.ordinal) { progress ->
        val from = pagerState.currentPage
        val toward = if (from < HomePage.Play.ordinal) 1f else -1f
        var committed = false
        try {
            progress.collect { event -> pagerState.scrollToPage(from, toward * event.progress * PredictiveBackPeek) }
            committed = true
        } finally {
            // 놓았으면 가운데로, 거뒀으면(취소) 제자리로.
            val target = if (committed) HomePage.Play.ordinal else from
            scope.launch { pagerState.animateScrollToPage(target, animationSpec = AppMotion.defaultSpatial()) }
        }
    }

    // 업데이트 안내(#270)의 코치마크가 화면 전체를 덮어야 해서 한 겹 감싼다.
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
        ) {
            HomeTopBar(
                subscribed = subscribed,
                showsPremiumPrompt = showsPremiumPrompt,
                onPremiumClick = { showSubscribeDialog = true },
                onSettingsClick = onSettingsClick,
            )
            // ⚠️ 페이지 안에 가로로 끄는 것을 두지 않는다 — 넘기기와 부딪친다. 학습의 하위 화면(판 · 도해)이 페이지 밖, 화면 전체로 열리는 이유다.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { index ->
                // 큰 화면(접는 폰 · 태블릿)에서 카드가 끝까지 늘어나지 않게 페이지 내용의 폭을 [HomePageMaxWidth]에서 멈추고 가운데에 둔다(#281). 폰 폭에는 아무 일도 없다.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(modifier = Modifier.widthIn(max = HomePageMaxWidth).fillMaxHeight()) {
                        when (HomePage.entries[index]) {
                            HomePage.MyInfo -> myInfoPage()
                            HomePage.Play -> HomePlayPage(
                                hasResumableSession = hasResumableSession,
                                resumeSummary = resumeSummary,
                                onResumeClick = onResumeClick,
                                onStartMatchClick = { if (hasResumableSession) showOverwriteWarningDialog = true else onStartMatchClick() },
                                onRankMeasureClick = { if (hasResumableSession) showRankMeasureOverwriteWarning = true else showRankMeasureDialog = true },
                                onGameHistoryClick = onGameHistoryClick,
                                onGameRecordsClick = onGameRecordsClick,
                                onLifeAndDeathClick = onLifeAndDeathClick,
                                onBasicsClick = onBasicsClick,
                                onBoardScanClick = onBoardScanClick,
                                playerSetup = playerSetup,
                                topNotice = topNotice,
                            )
                            HomePage.Lab -> HomeLabPage(onBoardScanClick = onBoardScanClick)
                        }
                    }
                }
            }
            HomePageTabBar(pagerState = pagerState, onSelect = showPage)
        }
        // 윗줄 배경음악 버튼의 업데이트 안내(#270 — 1.6.x에서 한 번). 「새로워진 점」·출석 팝업이 닫힌 뒤에 뜬다(둘 다 `TrackWhileShown`).
        // ⚠️ `Column` **뒤**에 둔다 — 코치마크의 흡수 층이 홈의 카드·칩보다 위에 있어야 어디를 눌러도 안내만 넘어간다(대국 화면 앵커와 같은 이유).
        GuideAnchor(surface = GuideSurface.HomeQuickActions, blocked = GuideBlockingOverlays.isShowing)
    }

    // 이전 대국 존재 상태에서 새 캐릭터 대국 선택 시 확인 경고 팝업
    if (showOverwriteWarningDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteWarningDialog = false },
            title = { Text(strings.overwriteWarningTitle, fontWeight = FontWeight.Bold) },
            text = { Text(strings.overwriteWarningMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOverwriteWarningDialog = false
                        onStartMatchClick()
                    },
                ) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteWarningDialog = false }) {
                    Text(strings.cancel)
                }
            },
        )
    }

    if (showRankMeasureOverwriteWarning) {
        AlertDialog(
            onDismissRequest = { showRankMeasureOverwriteWarning = false },
            title = { Text(strings.overwriteWarningTitle, fontWeight = FontWeight.Bold) },
            text = { Text(rankMeasureOverwriteWarningFor(strings.language)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRankMeasureOverwriteWarning = false
                        onDiscardResumableSession()
                        showRankMeasureDialog = true
                    },
                ) { Text(strings.confirm) }
            },
            dismissButton = { TextButton(onClick = { showRankMeasureOverwriteWarning = false }) { Text(strings.cancel) } },
        )
    }
    // 기력 변동 팝업·승급을 고르는 창(#219 · #268)은 셸이 홈 위에 띄운다 — 스플래시·엔진 준비 화면을 기다리고 출석 팝업과 겹치지 않게
    // 시작 팝업들의 줄에 서야 한다(`GoCoachApp`). 뒤로 가기로 기권한 판의 강급·승급도 홈에 나온 뒤 거기서 뜬다.
    if (showRankMeasureDialog) {
        RankMeasureSetupDialog(
            rankMeasure = rankMeasure,
            onStart = { start ->
                showRankMeasureDialog = false
                onStartRankMeasureGame(start)
            },
            onDismiss = { showRankMeasureDialog = false },
        )
    }

    // "Premium?" 탭으로 여는 구독 창구(백로그 #182). `PremiumSubscriptionCard`와 같은 패턴 —
    // 새 상태 훅이 아니라 이 컴포저블 로컬 `remember`.
    if (showSubscribeDialog) {
        PremiumSubscribeDialog(onDismiss = { showSubscribeDialog = false })
    }
}

/** 홈의 세 페이지 — **선언 순서가 왼쪽부터의 자리다**(#280): 내 정보 ‹ 대국 › 실험실(사용자 2026-10-22 — 셋째는 처음에 「학습 하기」였다). 저장하지 않는다(프로세스 안의 기억뿐). */
internal enum class HomePage { MyInfo, Play, Lab }

/**
 * 홈을 떠났다 돌아올 때 떠났던 페이지로 오게 하는 기억(#280 ①). 홈은 다른 화면으로 가면 컴포지션에서 빠져 페이지 상태를 잃는다.
 * 프로세스 안에만 있다 — 앱을 새로 켜면 늘 가운데(대국)다. 뒤로가기로 앱을 나가는 길도 가운데를 거친다.
 */
internal object HomePageMemory {
    var page: HomePage = HomePage.Play
}

/** 예측 뒤로가기를 끝까지 끌었을 때 가운데 페이지가 들어오는 비율(#281) — 스레드가 정한 값. 0.5를 넘기면 안 된다(`scrollToPage`의 범위 · 처리기가 꺼진다). */
private const val PredictiveBackPeek = 0.3f

/** 큰 화면에서 페이지 내용이 넓어질 수 있는 끝(#281) — 큰 화면의 문턱(sw 600dp)과 같은 값, 스레드가 정했다. */
private val HomePageMaxWidth = 600.dp

/**
 * 세 페이지 공통 윗줄(#280 ④) — 왼쪽에 앱의 로고 한 줄, 오른쪽 끝에 빠른 버튼 묶음(#270)과 설정. 「내 정보」 칩은 페이지가 되며 빠졌다(아래 탭이 대신한다).
 * 로고가 여기 있는 것은 사용자 결정이다(#280 ③ B안, 2026-10-22 — 처음에 고른 A안(대국 페이지 위의 큰 로고)을 같은 날 바꿨다). 세 페이지 어디서나 앱 이름이 보인다.
 * 빠른 버튼의 배경음악 버튼은 업데이트 안내의 동그라미 대상으로 자리를 알린다(그 보고는 **홈의 것만** 한다 — `guideTarget`의 KDoc).
 */
@Composable
private fun HomeTopBar(
    subscribed: Boolean,
    showsPremiumPrompt: Boolean,
    onPremiumClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HomeTopBarMinHeight)
            // 끝은 톱니 버튼의 터치 여백(48dp)이 바깥 여백을 대신한다.
            .padding(start = AppSpacing.Space16, end = AppSpacing.Space8, top = AppSpacing.Space4, bottom = AppSpacing.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 로고는 남는 폭을 혼자 갖고 그 안에서 왼쪽에 붙는다 — 빠른 버튼과 설정이 언제나 제 폭을 먼저 가진다(큰 글꼴에서 제목이 길면 말줄임).
        // ⚠️ 로고 뒤에 가중치 여백을 또 두지 않는다 — 가중치 둘이 남는 폭을 반씩 나눠 짧은 제목도 「포켓 …」으로 잘렸다.
        Box(modifier = Modifier.weight(1f)) {
            HomeTopBarLogo(subscribed = subscribed, showsPremiumPrompt = showsPremiumPrompt, onPremiumClick = onPremiumClick)
        }
        AppQuickActions(musicToggleModifier = Modifier.guideTarget(GuideTarget.MusicToggle))
        // 설정은 글자 없는 톱니다 — 로고가 왼쪽을 쓰면 360dp에서 영어 「Settings」 칩까지는 한 줄에 서지 못한다(함정 21).
        IconButton(onClick = onSettingsClick) {
            Icon(imageVector = Icons.Filled.Settings, contentDescription = strings.settingsTitle, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private val HomeTopBarMinHeight = 56.dp

/**
 * 윗줄 왼쪽의 로고 한 줄(#280 ③ B안) — 흑백 돌 엠블럼 + 앱 제목, 그 아래 「Premium?」(백로그 #182 — 구독 창구를 띄울 수 있을 때만).
 * 구독 중이면 제목이 금색이다. 구독 창구를 띄울 수 있으면 묶음 전체가 누르는 표적이다(엠블럼만이 아니라).
 */
@Composable
private fun HomeTopBarLogo(
    subscribed: Boolean,
    showsPremiumPrompt: Boolean,
    onPremiumClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.Corner10))
            .let { if (showsPremiumPrompt) it.clickable(onClick = onPremiumClick) else it }
            .padding(vertical = AppSpacing.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoStoneLogoBadge(subscribed = subscribed, showsPremiumPrompt = showsPremiumPrompt, badgeSize = HomeTopBarLogoSize)
        Spacer(modifier = Modifier.width(AppSpacing.Space8))
        Column {
            Text(
                text = strings.appTitle,
                fontSize = AppTextSize.Text16,
                fontWeight = FontWeight.ExtraBold,
                color = if (subscribed) PremiumGoldDeep else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showsPremiumPrompt) {
                Text(text = "Premium?", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PremiumGold, maxLines = 1)
            }
        }
    }
}

private val HomeTopBarLogoSize = 38.dp

/**
 * 가운데 페이지 「대국」(#280 → #288, 사용자 2026-10-24) — 한 줄 안내(오늘의 명언) → 이어하기 → **2열 대국 카드**(일반 대국 · 승급 대국)
 * → **가는 메뉴 줄 넷**(대국 기록 · 명국 기보 · 사활 풀기 · 기초 학습) → 오른쪽 아래 카메라 단추(바둑판 사진 분석).
 * 두는 메뉴와 아닌 메뉴의 위계는 색이 아니라 **크기**다(「서 있는 답」 #237) — 2열 카드는 키가 크고, 나머지는 글자 한 줄이다.
 */
@Composable
private fun HomePlayPage(
    hasResumableSession: Boolean,
    resumeSummary: String?,
    onResumeClick: () -> Unit,
    onStartMatchClick: () -> Unit,
    onRankMeasureClick: () -> Unit,
    onGameHistoryClick: () -> Unit,
    onGameRecordsClick: () -> Unit,
    onLifeAndDeathClick: () -> Unit,
    onBasicsClick: () -> Unit,
    onBoardScanClick: () -> Unit,
    playerSetup: PlayerSetup,
    topNotice: @Composable (Modifier) -> Unit,
) {
    val strings = LocalUiStrings.current
    val rankMeasure = LocalRankMeasureUiState.current
    val context = LocalContext.current
    // 기록은 지금의 좌석 배치로 정해지지 않을 때만 읽는다(`lastPlayedCharacterOrNull`) — 내 정보의 승률 머리말처럼 그 자리에서 한 번.
    val lastPlayedCharacter = remember(playerSetup, hasResumableSession) {
        lastPlayedCharacterOrNull(playerSetup, hasResumableSession) { GameHistoryStore(context).loadAll() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 이어하기 버튼이 뜨면 고정 자식만으로 페이지 세로를 넘길 수 있다. 스크롤이 없으면 Column이 그 부족분을 **가중치 자식**의 높이에서
            // 깎아내 제목이 잘린다(#28). ⚠️ 아래 가중치 Spacer와 **반드시 함께** 간다 — 페이지가 높이를 정해 주므로(최소 = 최대) 내용이 들어가면
            // 가중치 여백이 남는 공간을 나누고, 넘치면 0으로 줄어 스크롤이 된다.
            .verticalScroll(rememberScrollState())
            // 좌우 16dp — 2열 카드가 반 폭이라 360dp에서 부제가 설 폭을 남긴다(#288, 옛 24dp는 전폭 카드 시절의 값).
            .padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 한 줄 안내 — 엔진 팩을 받는 동안(#248) · 새로워진 점(#257) · 새 버전이 있을 때(#255) · 오늘의 한 줄 명언(#273). 차례는 `homeTopNoticeOf` 한 곳이 정한다.
        // **자리를 늘 비워 둔다**(#288, 사용자 2026-10-24: *"위에 한두줄이 항상 존재한다는 가정하에 여백을 두고 계산된 위치에서 시작"*) — 안내가 무엇이든,
        // 없든 대국 카드는 같은 자리에서 시작한다(#128 ③의 표적 원칙). 높이는 [homeTopNoticeSlotHeight]. 그보다 큰 안내(큰 글꼴의 세 줄 등)만 카드를 민다.
        // (전에는 메뉴가 남는 세로의 가운데에 서서, 안내 높이의 반만큼 카드가 오르내렸다.)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = homeTopNoticeSlotHeight()),
            contentAlignment = Alignment.TopCenter,
        ) {
            topNotice(Modifier.padding(top = AppSpacing.Space4))
        }
        Spacer(modifier = Modifier.height(AppSpacing.Space12))

        // 대국 카드 둘은 **한 줄에 나란히** 선다(#288, 사용자 2026-10-24) — 왼쪽이 「일반 대국」(「서 있는 답」 #239 — 첫 카드), 오른쪽이 「승급 대국」.
        // 키는 둘이 같다 — 줄의 키를 큰 쪽의 내용에 맞추고(`IntrinsicSize.Min`) 두 카드가 그 키를 채운다. 부제가 긴 언어 · 큰 글꼴에서도 둘이 함께 자란다.
        // ⚠️ 「승급 대국」은 사람 모델이 있는 기기에서만 보인다(`RankMeasureUiState.isAvailable`) — 없으면 상대가 그 급수처럼 두지 못해 잰 기력이 뜻을 잃는다.
        //   그때는 「일반 대국」이 줄을 혼자 채운다.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12),
        ) {
            // "일반 대국" (Start Match) 카드 — 이전 대국 존재 시 확인 팝업 분기(그 팝업은 홈이 든다)
            //
            // ⚠️ **첫돌이 말풍선을 이 열의 새 자식으로 넣지 말 것**(백로그 #128 ③). 자식을 더하면
            // 카드가 아래로 밀려 **사용자가 눌러야 할 표적이 움직이고**, #28이 만졌던 가중치·스크롤
            // 산수에 다시 손대는 셈이 된다. 그래서 **호출부만 `Box`로 감싸고** 말풍선은 그 안에서 겹친다.
            // ⚠️ 말풍선은 `ZeroSizeOverlay`가 **0×0으로 보고**하므로 배율이 올라 말풍선이 커져도
            //   Box는 카드 높이 그대로다(그 이유는 그 함수의 KDoc — 설계안 셋이 여기서 틀렸다).
            // 말풍선은 카드에 붙어 있어 이 페이지에서만 보인다(#280 ⑧).
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                MenuCard(
                    title = strings.startMatch,
                    subtitle = strings.homeStartMatchSubtitle,
                    onClick = onStartMatchClick,
                    icon = { StartMatchCardIcon(character = lastPlayedCharacter) },
                    modifier = Modifier.fillMaxHeight(),
                )
                // ⚠️ **스크림을 두지 않는다.** #125 스플래시가 터치를 일부러 먹는 것과 **반대**다 —
                // 여기서 터치를 먹으면 사용자가 이 카드를 누를 수 없어 자동 재생이 막다른 길이 된다
                // (다음 단계 ④는 이 카드를 눌러 대국 설정에 도착해야 열린다).
                // ⚠️ **팝업이 떠 있으면 억제한다** — 뒤에 깔린 채 "봤음"으로 기록되지 않게.
                //   2026-09-09까지 출석 팝업 **하나**만 셌는데, 엔진 안내·초기화 안내가 뜬 동안에는
                //   출석 팝업이 억제돼 게이트가 `false`였다 → ③이 그 뒤에 깔린 채 소진됐다
                //   (그 사유는 `GuideBlockingOverlays`의 KDoc).
                GuideAnchor(
                    surface = GuideSurface.Home,
                    blocked = GuideBlockingOverlays.isShowing,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            if (rankMeasure.isAvailable) {
                MenuCard(
                    title = rankMeasureTitleFor(strings.language),
                    subtitle = rankMeasureSubtitleFor(strings.language),
                    onClick = onRankMeasureClick,
                    icon = { RankMeasureIcon(rank = rankMeasure.state.rank, rankLabel = kgsRankLabelFor(strings.language, rankMeasure.state.rank)) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        // 저장된 대국이 있을 때 노출되는 "이전 대국 이어하기" 버튼 — 눈길을 끄는 움직임은 깜빡임이 아니라 **숨쉬기**다(#281 A안, 사용자 2026-10-23):
        // 글자가 흐려지지 않아 읽기 쉽고(옛 깜빡임은 투명도 0.35까지 내려갔다), 알약 전체가 천천히 커졌다 작아진다.
        // 자리는 대국 카드 **아래**다(#288, 사용자 2026-10-24) — 판이 생기고 없어져도 대국 카드는 움직이지 않는다. 밀리는 것은 아래 메뉴 칸이다.
        if (hasResumableSession) {
            val breath by rememberInfiniteTransition(label = "resumeBreath").animateFloat(
                initialValue = 1f,
                targetValue = ResumeBreathScale,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = ResumeBreathMillis, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "resumeBreathScale",
            )

            // 폭은 위 2열 카드와 같은 전폭이다(#288) — 가운데 뜬 알약보다 왼쪽 · 오른쪽 끝이 카드와 맞아 한 덩어리로 읽힌다. 글 두 줄(#287)은 그대로.
            Box(
                modifier = Modifier
                    .padding(top = AppSpacing.Space12)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = breath
                        scaleY = breath
                    }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onResumeClick)
                    .padding(horizontal = AppSpacing.Space20, vertical = AppSpacing.Space10),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "▶ " + strings.resumeTitle,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = AppTextSize.Text18,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                    // 어떤 판을 이어 두는지(#287, 사용자 2026-10-24) — 대국 · 상대 · 내 돌 · 판 크기와 수. 큰 글꼴 · 긴 언어에서는 두 줄까지.
                    resumeSummary?.let { summary ->
                        Text(
                            text = summary,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // 두는 것이 아닌 메뉴는 **2열의 작은 칸**이다(#288, 사용자 2026-10-24 — #237의 그림 타일 → 가는 줄 → 이것). 그림 없이 이름과 › 하나 — 위의 키 큰 대국 카드와 크기로 갈린다.
        // 칸 넷(#280): 대국 기록 · 명국 기보 / 사활 풀기 · 기초 학습(바둑 규칙 · 기초 행마 · 유튜브 강좌를 모은 목록).
        Spacer(modifier = Modifier.height(AppSpacing.Space12))
        HomeMenuGrid(
            rows = listOf(
                HomeMenuRow(strings.gameHistoryTitle, onGameHistoryClick),
                HomeMenuRow(gameRecordsTitleFor(strings.language), onGameRecordsClick),
                HomeMenuRow(strings.studyCategoryTitle(StudyCategory.LifeAndDeath), onLifeAndDeathClick),
                HomeMenuRow(studyBasicsTitleFor(strings.language), onBasicsClick),
            ),
        )

        Spacer(modifier = Modifier.weight(1f))

        // 「바둑판 사진 분석」은 페이지 오른쪽 아래의 **카메라 단추 하나**다(#280, 사용자 2026-10-22 — 아는 사람은 여기로, 이름 붙은 입구는 실험실 페이지).
        // #281 A안: 맨 아이콘이 아니라 타일과 같은 바탕의 둥근 네모에 담는다 — 누를 수 있는 것으로 읽히고, 누르면 타일처럼 모양이 바뀐다.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val corner by animateDpAsState(if (pressed) HomePressedCorner else BoardScanButtonCorner, AppMotion.fastSpatial(), label = "boardScanCorner")
            Card(
                onClick = onBoardScanClick,
                interactionSource = interaction,
                shape = RoundedCornerShape(corner),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.size(BoardScanButtonSize),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = BoardScanCameraIcon,
                        contentDescription = strings.featureShortName(FeatureId.BoardScan),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 아래 탭 셋(#280 ②) — 지금 페이지를 알약으로 보이고, 누르면 그 페이지로 넘긴다. 넘기기만으로는 옆에 무엇이 있는지 모르고 지나치기 쉽다.
 * 키는 최소 높이만 준다 — 글꼴 배율 1.3에서 이름이 두 줄이 돼도 잘리지 않게(함정 9).
 *
 * #281 A안(사용자 2026-10-23):
 * - 알약은 **하나**이고 페이지 위치(`currentPage + currentPageOffsetFraction`)를 그대로 따라간다 — 손가락으로 넘기면 손가락을, 탭을 누르면
 *   페이지의 스프링을 따라 미끄러진다. 알약은 아이콘 칸과 같은 높이에서 시작한다(아이콘 칸을 위에 붙여 큰 글꼴에서도 어긋나지 않는다).
 * - 구분선 대신 바탕 톤([HomeTabBarPalette])으로 페이지와 가른다. 고른 탭의 이름은 브랜드 초록이 아니라 짙은 글자(`onSurface`)다 —
 *   초록 12sp는 이 바탕에서 대비가 모자라다. 초록은 알약 안의 아이콘이 맡는다.
 * - 누를 때의 물결은 탭 칸 전체가 아니라 알약 모양 안에만 번진다(M3 내비게이션 막대와 같은 결).
 */
@Composable
private fun HomePageTabBar(pagerState: PagerState, onSelect: (HomePage) -> Unit) {
    val strings = LocalUiStrings.current
    val colors = MaterialTheme.colorScheme
    val current = HomePage.entries[pagerState.currentPage]
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(HomeTabBarPalette.Container)
            .navigationBarsPadding(),
    ) {
        val tabWidth = maxWidth / HomePage.entries.size
        Box(
            modifier = Modifier
                .padding(top = HomeTabTopPadding)
                .offset {
                    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                    IntOffset((tabWidth * (position + 0.5f) - HomeTabPillWidth / 2).roundToPx(), 0)
                }
                .size(width = HomeTabPillWidth, height = HomeTabPillHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.primary.copy(alpha = 0.14f)),
        )
        Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            HomePage.entries.forEach { page ->
                val selected = page == current
                val interaction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = HomeTabMinHeight)
                        .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = { onSelect(page) })
                        .padding(top = HomeTabTopPadding, bottom = AppSpacing.Space6),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = HomeTabPillWidth, height = HomeTabPillHeight)
                            .clip(RoundedCornerShape(percent = 50))
                            .indication(interaction, ripple()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = page.icon(),
                            contentDescription = null,
                            tint = if (selected) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        text = page.label(strings),
                        color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                        fontSize = AppTextSize.Text12,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = AppSpacing.Space4),
                    )
                }
            }
        }
    }
}

private val HomeTabMinHeight = 64.dp
private val HomeTabTopPadding = 8.dp

/** 탭 알약 — M3 내비게이션 막대의 활성 표시와 같은 64×32. */
private val HomeTabPillWidth = 64.dp
private val HomeTabPillHeight = 32.dp

private fun HomePage.label(strings: UiStrings): String = when (this) {
    HomePage.MyInfo -> strings.myPageTitle
    HomePage.Play -> homePagePlayTabFor(strings.language)
    HomePage.Lab -> homePageLabTabFor(strings.language)
}

private fun HomePage.icon(): ImageVector = when (this) {
    HomePage.MyInfo -> Icons.Filled.Person
    HomePage.Play -> PlayTabStonesIcon
    HomePage.Lab -> LabTabFlaskIcon
}

/**
 * 「대국」 탭 — 흑돌이 백돌에 겹친 모양(로고와 같은 짝). 백돌은 흑돌 둘레에 틈을 두고 끊긴 고리다 — 한 색으로 칠해도 두 돌로 읽힌다.
 * 고리는 선이 아니라 칠한 면이다(바깥 호 → 틈의 호 → 안쪽 호 → 틈의 호) — `materialPath`는 칠하기만 한다. 틈의 원은 흑돌 중심에서 반지름 6.7.
 */
private val PlayTabStonesIcon: ImageVector = materialIcon(name = "HomeTab.Stones") {
    materialPath {
        // 백돌 고리 — 중심 (15.5, 12) · 바깥 6.4 · 안 4.6.
        moveTo(12.28f, 6.47f)
        arcTo(6.4f, 6.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12.28f, 17.53f)
        arcTo(6.7f, 6.7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 13.7f, 16.23f)
        arcTo(4.6f, 4.6f, 0f, isMoreThanHalf = true, isPositiveArc = false, 13.7f, 7.77f)
        arcTo(6.7f, 6.7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12.28f, 6.47f)
        close()
        // 흑돌 — 중심 (8.5, 12) · 반지름 5.5.
        moveTo(3f, 12f)
        arcToRelative(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 11f, 0f)
        arcToRelative(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, -11f, 0f)
        close()
    }
}

/** 「실험실」 탭 — 플라스크(Material 「science」 모양을 옮겨 그렸다 — 이 앱은 아이콘 확장 세트를 싣지 않는다). */
private val LabTabFlaskIcon: ImageVector = materialIcon(name = "HomeTab.Flask") {
    materialPath {
        moveTo(19.8f, 18.4f)
        lineTo(14f, 10.67f)
        verticalLineTo(6.5f)
        lineToRelative(1.35f, -1.69f)
        curveTo(15.61f, 4.48f, 15.38f, 4f, 14.96f, 4f)
        horizontalLineTo(9.04f)
        curveTo(8.62f, 4f, 8.39f, 4.48f, 8.65f, 4.81f)
        lineTo(10f, 6.5f)
        verticalLineToRelative(4.17f)
        lineTo(4.2f, 18.4f)
        curveTo(3.71f, 19.06f, 4.18f, 20f, 5f, 20f)
        horizontalLineToRelative(14f)
        curveTo(19.82f, 20f, 20.29f, 19.06f, 19.8f, 18.4f)
        close()
    }
}

/** 대국 페이지 오른쪽 아래의 사진 분석 단추(#280) — 카메라(Material 「photo camera」 모양을 옮겨 그렸다). 몸체 · 렌즈 테 · 렌즈를 홀짝 채우기로 가른다. */
private val BoardScanCameraIcon: ImageVector = materialIcon(name = "Home.BoardScanCamera") {
    materialPath(pathFillType = PathFillType.EvenOdd) {
        moveTo(9f, 2f)
        lineTo(7.17f, 4f)
        horizontalLineTo(4f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(12f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(16f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(6f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        horizontalLineToRelative(-3.17f)
        lineTo(15f, 2f)
        close()
        moveTo(7f, 12f)
        arcToRelative(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 10f, 0f)
        arcToRelative(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -10f, 0f)
        close()
        moveTo(8.8f, 12f)
        arcToRelative(3.2f, 3.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6.4f, 0f)
        arcToRelative(3.2f, 3.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, -6.4f, 0f)
        close()
    }
}

/**
 * 백로그 #182 — 테두리로 프리미엄 상태를 알린다: 구독 중이면 금색 실선, 구독 창구를
 * 보여줄 수 있으면(미구독 + [FeatureFlags.isPurchaseEnabled]) 금색 점선, 그 외엔 원래
 * 중립 테두리. `Modifier.border`엔 점선이 없어 점선만 `drawWithContent`로 직접 그린다.
 */
@Composable
private fun GoStoneLogoBadge(subscribed: Boolean, showsPremiumPrompt: Boolean, badgeSize: Dp) {
    // 처음 그린 크기(125dp)를 기준으로 돌 · 겹침 · 그림자 · 점선을 함께 줄인다(#280 — 윗줄의 작은 로고).
    val scale = badgeSize / GoStoneLogoBaseSize
    val stoneSizeDp = 51.dp * scale
    // 광원 위치/반경을 실제 픽셀 기준으로 계산해, 하드코딩된 px 값이 스톤 크기와 어긋나
    // 그라데이션이 중앙의 작은 얼룩으로만 보이던 문제(특히 흑돌에서 두드러짐)를 없앤다.
    val stoneSizePx = with(LocalDensity.current) { stoneSizeDp.toPx() }
    val highlightCenter = Offset(stoneSizePx * 0.32f, stoneSizePx * 0.28f)
    val highlightRadius = stoneSizePx * 0.85f
    val dashedStrokeWidthPx = with(LocalDensity.current) { (2.dp * scale).coerceAtLeast(1.dp).toPx() }

    val borderModifier = when {
        subscribed -> Modifier.border(AppBorderWidth.Strong, PremiumGoldGradient, CircleShape)
        showsPremiumPrompt -> Modifier.drawWithContent {
            drawContent()
            drawCircle(
                color = PremiumGold,
                radius = (size.minDimension - dashedStrokeWidthPx) / 2f,
                style = Stroke(
                    width = dashedStrokeWidthPx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f * scale, 8f * scale)),
                ),
            )
        }
        else -> Modifier.border(AppBorderWidth.Hairline, HomeLogoPalette.RingBorder, CircleShape)
    }

    Box(
        modifier = Modifier
            .size(badgeSize)
            .shadow(elevation = 8.dp * scale, shape = CircleShape, clip = false)
            .background(HomeLogoPalette.Backdrop, CircleShape)
            .then(borderModifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // 겹침은 offset()이 아니라 spacedBy(음수)로 표현한다 — offset()은 레이아웃 크기에는
            // 반영되지 않고 그리기 위치만 바꾸므로, 부모 Box의 가운데 정렬 계산이 겹친 만큼을
            // 반영하지 못해 전체가 살짝 왼쪽으로 치우쳐 보이는 문제가 있었다.
            horizontalArrangement = Arrangement.spacedBy((-13).dp * scale),
        ) {
            // 흑돌 (Black Stone) — 백돌과 동일한 광원 위치에 밝은 하이라이트를 두어
            // 같은 수준의 입체감/광택이 느껴지도록 4단계 그라데이션을 사용한다.
            Box(
                modifier = Modifier
                    .size(stoneSizeDp)
                    .shadow(elevation = 4.dp * scale, shape = CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = HomeLogoPalette.BlackStoneGradient,
                            center = highlightCenter,
                            radius = highlightRadius,
                        ),
                        shape = CircleShape,
                    ),
            )

            // 백돌 (White Stone) with 3D Radial Gradient, Border & Shadow
            Box(
                modifier = Modifier
                    .size(stoneSizeDp)
                    .shadow(elevation = 4.dp * scale, shape = CircleShape)
                    .border(AppBorderWidth.Hairline, HomeLogoPalette.WhiteStoneBorder, CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = HomeLogoPalette.WhiteStoneGradient,
                            center = highlightCenter,
                            radius = highlightRadius,
                        ),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

private val GoStoneLogoBaseSize = 125.dp

/**
 * 홈의 **대국 카드** — 「일반 대국」과 「승급 대국」, 두는 메뉴 둘이 같은 모습으로 **한 줄에 반 폭씩** 선다(백로그 #237 → #288, 2026-10-24 사용자).
 * 위쪽은 그림(정사각형) 옆에 두 줄로 접은 이름(「일반 / 대국」), 아래쪽은 카드 폭 전체를 쓰는 부제다.
 *
 * ## 초록 바탕을 버린 이유
 * 전에는 「캐릭터 대국」만 브랜드 초록을 칠했다. 「승급 대국」도 두는 메뉴라 같은 대접을 해야 했는데, 둘 다 칠하면 화면
 * 위쪽이 초록 벽이 되고 **구간의 테두리(`RankTierBadge`)가 초록에 묻힌다**(플래티넘의 청록이 특히). 그래서 바탕은 흰색으로
 * 두고 살짝 띄운다. 오른쪽의 초록 ▶는 반 폭에 설 자리가 없어 #288에서 뺐다(사용자의 카드 구성에 없다). 아래 메뉴와의 위계는 색이 아니라
 * **크기**가 만든다([HomeMenuGrid]).
 *
 * ⚠️ **`private`이 아니라 `internal`인 이유는 가이드 다시보기 하나뿐이다**(백로그 #128).
 * 그 화면이 ③을 설명할 때 **정적 삽화나 캡처가 아니라 이 진짜 카드**를 같은 문구로 그린다 —
 * 이 저장소는 그림이 낡는 사고를 네 번 겪었다(#87·#97·#124·#127, §0 B-2).
 * ⚠️ **일반 카드 API로 쓰지 말 것.** 이 함수의 레이아웃 근거(#28·#29)는 홈 화면의 열에 묶여
 * 있어서, 다른 화면에서 쓰면 그 사유가 함께 따라가지 않는다. 호출부를 **두 파일로 못박아**
 * 둔다(`FirstDolGuideContractTest`).
 */
@Composable
internal fun MenuCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    /** 카드 왼쪽 위의 정사각형 그림(백로그 #181 → #288) — 마지막으로 둔 캐릭터(없으면 흑백 돌), 또는 구간의 테두리에 담긴 내 기력. */
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalUiStrings.current.language
    // ⚠️ 누르기는 `Card(onClick)`에 맡긴다 — 바깥 modifier에 `clip` + `clickable`을 걸면 그림자까지 잘려 카드가 떠 보이지 않는다.
    // #281 A안(사용자 2026-10-23): 누르면 카드가 살짝 줄고 모서리가 덜 둥글어진다(Expressive의 「누를 때 모양이 바뀐다」).
    // 크기는 그리기 단계(`graphicsLayer`)에서만 바꾼다 — 자리와 키가 그대로라 아래 메뉴가 움직이지 않는다(#128 ③의 표적 원칙).
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) HomePressedCorner else MenuCardCorner, AppMotion.fastSpatial(), label = "menuCardCorner")
    val scale by animateFloatAsState(if (pressed) MenuCardPressedScale else 1f, AppMotion.fastSpatial(), label = "menuCardScale")
    Card(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = MenuCardElevation),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 고정 높이가 아니라 **최소** 높이다(#29) — 부제가 넉 줄이 되는 언어·글꼴 배율에서도 썰리지 않고 카드가 자란다.
                // 홈은 #28에서 스크롤을 얻었으므로 카드가 커져도 화면이 깨지지 않는다.
                // ⚠️ 하한은 `Card`가 아니라 이 열에 건다 — M3 `Card`는 내용을 modifier 없는 `Column`으로 감싼다.
                .heightIn(min = MenuCardMinHeight)
                .padding(AppSpacing.Space16),
        ) {
            // 윗줄은 **그림 반 · 이름 반**으로 나누고 각자 그 반의 가운데에 선다(사용자 2026-10-24, #288).
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ⚠️ **배경 없이 카드 색 위에 그대로 얹는다**(2026-09-21 사용자 결정). 그림 모두 스스로 또렷하다 —
                // 캐릭터는 제 그림이, 흑백 돌은 제 테두리가, 구간의 테두리는 어두운 판을 두른다.
                // 그림은 [MenuCardIconSize]까지 — 좁은 폰에서 반 폭이 그보다 좁으면 그 폭에 맞춰 줄어든다(정사각형 그대로).
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = MenuCardIconSize)
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        icon()
                    }
                }
                // 이름은 두 줄로 접는다(「일반 / 대국」 — 사용자의 카드 구성). 큰 글꼴 배율 · 긴 언어에서 한 줄이 반 폭을 넘으면 글자를 줄인다(20 → 14sp).
                BasicText(
                    text = twoLineCardTitle(title),
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = MenuCardTitleLineHeight,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = AppTextSize.Text14, maxFontSize = AppTextSize.Text20),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.Space12))

            Text(
                // 한국어는 어절 안에서 줄을 바꾸지 않는다 — 반 폭이라 줄이 짧아 「오르내리 / 며」처럼 끊기기 쉽다(`keepWordsWhole`).
                text = keepWordsWhole(subtitle, language),
                color = MaterialTheme.colorScheme.secondary,
                fontSize = AppTextSize.Text13,
                // 줄 높이를 상속(24sp)에 맡기지 않는다 — 반 폭의 서너 줄 부제가 카드를 지나치게 키우지 않게.
                lineHeight = AppTextSize.Text18,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

/**
 * 대국 카드의 이름을 두 줄로 접는다(#288) — 첫 띄어쓰기에서(「일반 대국」 → 「일반 / 대국」 · 「Normal Match」 → 「Normal / Match」),
 * 띄어쓰기가 없는 일본어 · 중국어는 가운데에서(「通常対局」 → 「通常 / 対局」 · 「升级对局」 → 「升级 / 对局」). 한 글자 이름은 그대로 둔다.
 */
internal fun twoLineCardTitle(title: String): String {
    val space = title.indexOf(' ')
    return when {
        space > 0 -> title.substring(0, space) + "\n" + title.substring(space + 1)
        title.length >= 2 -> title.substring(0, title.length / 2) + "\n" + title.substring(title.length / 2)
        else -> title
    }
}

/** [MenuCard] 그림 자리(정사각형)의 한 변. */
private val MenuCardIconSize = 56.dp
/** 2열 카드의 최소 키(#288 — 사용자: *"세로로도 좀 더 크게"*) — 그림 · 두 줄 부제 · 위아래 여백이 들어가는 키. 옛 전폭 카드는 104dp였다. */
private val MenuCardMinHeight = 156.dp
private val MenuCardElevation = 3.dp
/** 글자 크기의 배수다 — 영어 이름이 14sp까지 줄어도 두 줄 사이가 벌어지지 않게. */
private val MenuCardTitleLineHeight = 1.2.em

/*
 * #281 A안의 모양 — 값은 사용자가 고른 시안(2026-10-23)을 옮긴 것이고 스레드가 정했다.
 * 쉴 때는 크게 둥글고, 누르면 [HomePressedCorner]로 덜 둥글어진다. 대국 카드 · 타일 · 카메라 단추가 같은 「누른 모서리」를 쓴다.
 */
private val MenuCardCorner = 28.dp
/** 반 폭 타일의 모서리 — 실험실의 「준비 중」 칸(`HomeLabPage.kt`)도 같은 값을 쓴다. */
internal val MenuTileCorner = 22.dp
private val BoardScanButtonCorner = 16.dp
private val BoardScanButtonSize = 48.dp
private val HomePressedCorner = 12.dp
private const val MenuCardPressedScale = 0.965f
private const val MenuTilePressedScale = 0.95f

/**
 * 「이전 대국 이어하기」의 숨쉬기 — 1.0 ↔ 1.02를 한 번에 1.2초씩. #281의 1.04는 가운데 뜬 짧은 알약의 값이었다 — 전폭(#288)에서는
 * 360dp에서 양옆으로 7dp씩 카드 끝을 넘어 나가 1.02로 줄였다(한쪽 3dp, 페이지 여백 16dp 안). 스레드가 정했다.
 */
private const val ResumeBreathScale = 1.02f
private const val ResumeBreathMillis = 1200

/**
 * 대국 페이지 맨 위 안내 자리의 높이(#288) — **두 줄짜리 오늘의 명언**이 들어가는 높이: 위 여백 4dp + 위아래 안쪽 여백 6dp씩 + 글 두 줄(줄 높이 24sp — 명언 줄이 물려받는 본문 줄 높이).
 * 줄 높이를 sp로 더하므로 글꼴을 키운 사람에게도 두 줄이 들어간다. 한 줄 알림(44dp) · 엔진 받기(진행 막대 포함 50dp) · 명언 한 줄(48dp)은 모두 이 안에 든다. 스레드가 정했다.
 */
@Composable
private fun homeTopNoticeSlotHeight(): Dp = with(LocalDensity.current) { AppSpacing.Space4 + AppSpacing.Space6 * 2 + (HomeTopNoticeLineHeight * 2).toDp() }

private val HomeTopNoticeLineHeight = 24.sp

/** 대국 페이지의 메뉴 칸 하나 — 이름과 누르면 갈 곳. */
internal class HomeMenuRow(val title: String, val onClick: () -> Unit)

/**
 * 대국 페이지의 **2열 메뉴 칸**(#288, 사용자 2026-10-24) — 두는 것이 아닌 메뉴(대국 기록 · 명국 기보 · 사활 풀기 · 기초 학습)가 두 줄 두 칸으로 선다.
 * 그림 · 부제 없이 이름과 › 하나. 위의 대국 카드보다 **작고 납작하며 그림자가 없는 톤 바탕**이다 — 크기와 바탕으로 한 단계 아래임이 읽힌다(「서 있는 답」 #237).
 * 처음은 전폭의 가는 줄이었다 — 줄이 길고 위아래로 붙어 있어 터치가 빗나갔다(사용자 2026-10-24). 칸 사이의 12dp 틈이 이웃 칸을 잘못 누르는 일을 막는다.
 * 키는 최소 높이만 준다 — 큰 글꼴 · 긴 언어에서 이름이 두 줄이 돼도 잘리지 않고, 한 줄의 두 칸은 키가 같다(함정 9).
 *
 * ⚠️ 다시보기(`FirstDolGuideReplay`)가 이 칸을 빌려 그린다 — 홈이 바뀌면 다시보기도 함께 바뀐다(그 파일의 KDoc). 칸이 하나면 왼쪽 반 폭에 선다.
 */
@Composable
internal fun HomeMenuGrid(rows: List<HomeMenuRow>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
        rows.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12),
            ) {
                pair.forEach { row -> HomeMenuTextTile(row = row, modifier = Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** [HomeMenuGrid]의 칸 하나 — 누르면 살짝 줄고 모서리가 덜 둥글어진다([MenuCard] · [MenuTile]과 같은 움직임, 크기는 그리기 단계에서만). */
@Composable
private fun HomeMenuTextTile(row: HomeMenuRow, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) HomePressedCorner else HomeMenuTextTileCorner, AppMotion.fastSpatial(), label = "menuTextTileCorner")
    val scale by animateFloatAsState(if (pressed) MenuTilePressedScale else 1f, AppMotion.fastSpatial(), label = "menuTextTileScale")
    Card(
        onClick = row.onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = HomeMenuTextTileMinHeight)
                .padding(start = AppSpacing.Space16, end = AppSpacing.Space8, top = AppSpacing.Space12, bottom = AppSpacing.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = AppTextSize.Text16,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            // 장식이다 — 누르는 표적은 칸 전체라 따로 읽어 줄 이름이 없다.
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(HomeMenuTextTileChevronSize),
            )
        }
    }
}

/** 칸의 최소 키 — 대국 카드(156dp)의 반이 안 되고, 손가락 표적(48dp)보다 넉넉하다. 스레드가 정했다. */
private val HomeMenuTextTileMinHeight = 64.dp
private val HomeMenuTextTileCorner = 18.dp
private val HomeMenuTextTileChevronSize = 20.dp

/** 홈의 반 폭 타일 하나 — 제목과 그림, 누르면 갈 곳. */
internal class HomeMenuTile(val title: String, val onClick: () -> Unit, val icon: @Composable () -> Unit)

/**
 * 홈의 **반 폭 타일** — 실험실 페이지의 「바둑판 사진 분석」이 쓴다(백로그 #237 — 대국 페이지의 타일 넷은 #288에서 그림 없는 칸([HomeMenuGrid])이 됐다).
 * [MenuCard]보다 작고 납작하며(그림자 없음) **부제가 없다** — 제목만으로 무엇인지 읽히는 메뉴들이고, 반 폭에는 부제가 설 자리가 없다.
 */
@Composable
internal fun MenuTile(tile: HomeMenuTile, modifier: Modifier = Modifier) {
    // #281 A안: 누르면 살짝 줄고 모서리가 덜 둥글어진다 — [MenuCard]와 같은 움직임, 크기만 그리기 단계에서 바꾼다.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) HomePressedCorner else MenuTileCorner, AppMotion.fastSpatial(), label = "menuTileCorner")
    val scale by animateFloatAsState(if (pressed) MenuTilePressedScale else 1f, AppMotion.fastSpatial(), label = "menuTileScale")
    Card(
        onClick = tile.onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.Space8, top = AppSpacing.Space10, end = AppSpacing.Space12, bottom = AppSpacing.Space10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(MenuTileIconSize), contentAlignment = Alignment.Center) { tile.icon() }
            Spacer(modifier = Modifier.width(AppSpacing.Space10))
            // 제목이 긴 언어에서는 두 줄이 된다 — 그림이 더 커서 타일의 키는 그대로다.
            Text(
                text = tile.title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = AppTextSize.Text16,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
        }
    }
}

private val MenuTileIconSize = 52.dp

/**
 * 이 좌석 배치의 AI가 쓰는 캐릭터 — AI 좌석이 없으면 `null`. 둘 다 AI면 백을 먼저 본다.
 * 캐릭터 그룹(`FastBeginner`)이 아니면 급수를 직접 정한 상대(백로그 #217 — 지금은 일반 설정에 남지 않지만, 남아 있어도)는 그 급수가 속한 구간의
 * 캐릭터 얼굴을 빌리고, 숨긴 옛 그룹(초급/중급/고급)은 레벨 3(「수제자 반상」)으로 떨어진다 — 없는 캐릭터를 가리키지 않게.
 * (#181의 `currentAiCharacterOrDefault`가 #288에서 이것이 됐다 — 「기본 설정이면 수제자 반상」은 흑백 돌 그림이 대신한다.)
 */
internal fun aiSeatCharacterOrNull(playerSetup: PlayerSetup): BotCharacter? {
    val aiPlayLevel = listOf(playerSetup.white, playerSetup.black)
        .firstOrNull { side -> side.controller == SeatController.Ai }
        ?.playLevel
        ?: return null
    val fastBeginnerLevel = if (aiPlayLevel.group == PlayLevelGroup.FastBeginner) {
        aiPlayLevel.safeLevel
    } else {
        aiPlayLevel.customRank()?.let(::customRankFallbackTier) ?: FallbackAiCharacterLevel
    }
    return BotCharacterCatalog.forPlayLevel(PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = fastBeginnerLevel))
}

private val DefaultPlayerSetup = PlayerSetup()
private const val FallbackAiCharacterLevel = 3

/**
 * 「일반 대국」 카드 그림의 캐릭터(#288, 사용자 2026-10-24: *"마지막 플레이한 캐릭터 보여주기. 선택되었던 캐릭터가 없을 경우 아이콘"*) — **마지막으로 둔 일반 대국**의 상대.
 * 1. 지금의 좌석 배치가 일반 대국이고 쓰이고 있으면(이어 둘 판이 있거나 첫 설정에서 바꿨으면) 그 상대.
 * 2. 아니면(첫 설정 그대로 · 승급 대국의 좌석) 대국 기록에서 가장 최근의 일반 대국의 상대. 기록은 이때만 읽는다([history]는 그래서 함수다).
 * 3. 일반 대국을 둔 적이 없으면 `null` — 카드는 흑백 돌을 그린다. 마지막 판이 둘이서였으면 역시 `null`이다(상대가 캐릭터가 아니다).
 * ⚠️ 첫 설정(흑 사람 · 백 판다)만으로는 「한 번도 안 뒀다」와 「기본 판다와 뒀다」를 가를 수 없다 — 그래서 2가 기록을 본다.
 * 승급 대국의 상대는 급수로 정한 AI라 캐릭터가 아니다 — 그 판의 좌석은 건너뛴다.
 */
internal fun lastPlayedCharacterOrNull(
    playerSetup: PlayerSetup,
    hasResumableSession: Boolean,
    history: () -> List<GameHistoryEntry>,
): BotCharacter? {
    if (!playerSetup.isRankMeasure() && (hasResumableSession || playerSetup != DefaultPlayerSetup)) {
        return aiSeatCharacterOrNull(playerSetup)
    }
    val lastNormalGame = history()
        .filter { entry -> entry.id != ReferenceGameHistoryId && !entry.playerSetup.isRankMeasure() }
        .maxByOrNull { entry -> entry.playedAtMillis }
        ?: return null
    return aiSeatCharacterOrNull(lastNormalGame.playerSetup)
}

/**
 * 「일반 대국」 카드의 그림(#288) — 마지막으로 둔 캐릭터의 얼굴, 고른 캐릭터가 없으면([lastPlayedCharacterOrNull]가 `null`) 흑백 돌 한 쌍.
 * (옛 그림은 판 위에 캐릭터를 70%로 얹은 것이었다 — #192. 반 폭 카드의 56dp에서는 판과 얼굴이 함께 읽히지 않아 얼굴만 둔다.)
 *
 * ⚠️ 다시보기(`FirstDolGuideReplay`)가 이 컴포저블을 함께 쓴다 — 다시보기 ③이 홈의 **진짜 카드**를 조립한다.
 */
@Composable
internal fun StartMatchCardIcon(character: BotCharacter?) {
    if (character != null) BotCharacterSquareIcon(character = character) else StonePair()
}

/** 흑돌이 백돌에 살짝 겹친 한 쌍 — 윗줄 로고와 같은 돌 그림(광택 그라데이션 · 백돌의 가는 테두리)을 배경 없이. */
@Composable
private fun StonePair() {
    val stoneSize = MenuCardIconSize * StonePairStoneFraction
    val stoneSizePx = with(LocalDensity.current) { stoneSize.toPx() }
    val highlightCenter = Offset(stoneSizePx * 0.32f, stoneSizePx * 0.28f)
    val highlightRadius = stoneSizePx * 0.85f
    // 겹침은 offset()이 아니라 spacedBy(음수)다 — 로고([GoStoneLogoBadge])와 같은 이유(가운데 정렬이 겹친 만큼을 반영한다).
    Row(horizontalArrangement = Arrangement.spacedBy(-stoneSize * StonePairOverlapFraction)) {
        Box(
            modifier = Modifier
                .size(stoneSize)
                .background(Brush.radialGradient(HomeLogoPalette.BlackStoneGradient, highlightCenter, highlightRadius), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(stoneSize)
                .border(AppBorderWidth.Hairline, HomeLogoPalette.WhiteStoneBorder, CircleShape)
                .background(Brush.radialGradient(HomeLogoPalette.WhiteStoneGradient, highlightCenter, highlightRadius), CircleShape),
        )
    }
}

/** 그림 자리 한 변 대비 돌 하나의 지름 · 두 돌이 겹치는 비율 — 둘을 합쳐 그림 자리 폭 안에 든다(0.56 × 2 − 0.56 × 0.25 = 0.98). 스레드가 정했다. */
private const val StonePairStoneFraction = 0.56f
private const val StonePairOverlapFraction = 0.25f

/**
 * 「승급 대국」 카드의 아이콘 — **지금의 내 기력**을 글자로 보인다(`20급`). 「일반 대국」 카드의 그림과 같은 자리·같은 크기다.
 * 카드를 열지 않아도 기력이 보이고, 그 기력이 속한 **구간의 테두리**가 둘러선다(백로그 #236 — 브론즈부터 그랜드 마스터까지 일곱,
 * 구간의 경계는 `KgsRankTier`, 테두리의 모양과 색은 [RankTierBadge]).
 */
@Composable
internal fun RankMeasureIcon(rank: KgsRank, rankLabel: String) {
    RankTierBadge(rank = rank, label = rankLabel, modifier = Modifier.fillMaxSize())
}

/**
 * 타일 그림의 정적 국면 — 실제 대국 규칙(`BoardRules.play`)을 거치지 않고 초반 포석
 * 몇 수를 바로 앉힌다(백로그 #181, 합법성 검사가 필요 없는 순수 장식용). 처음은 「학습 하기」 타일의 것이었고, 지금은 실험실의 「사진 분석」 타일만 쓴다. `object`가 아니라
 * `private val`인 이유는 컴포지션마다 새로 만들 이유가 없어서다 — 상태가 없는 상수 데이터다.
 */
private val StudyPreviewGameState: GameState = GameState.empty(
    boardSize = BoardSize.Nine,
    ruleset = Ruleset.Japanese,
).copy(
    stones = mapOf(
        BoardCoordinate(row = 2, column = 6) to StoneColor.Black,
        BoardCoordinate(row = 6, column = 2) to StoneColor.White,
        BoardCoordinate(row = 6, column = 6) to StoneColor.Black,
    ),
)

/** 판 한 변 대비 카메라 배지의 비율(기존 50%에서 120% 확대한 60%). */
private const val BoardScanBadgeSizeFraction = 0.6f

/**
 * "바둑판 사진 분석" 카드 아이콘 — 바둑판 위에 카메라 심볼을 겹친 프리뷰 배지.
 */
@Composable
internal fun BoardScanPreviewIcon() {
    Box(modifier = Modifier.fillMaxSize()) {
        GoBoard(
            gameState = StudyPreviewGameState,
            candidateMoves = emptyList(),
            moveReviews = emptyList(),
            ownershipEstimate = null,
            uxOptions = KaTrainUxOptions(),
            inputEnabled = false,
            engineActivityIndicator = null,
            modifier = Modifier.fillMaxSize(),
            tentativeMove = null,
            onCoordinateTap = {},
            isGameEnded = false,
            isEngineBusy = false,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxSize(BoardScanBadgeSizeFraction)
                .clip(CircleShape)
                // 2026-09-22 사용자 요청 — 흰 배경 불투명도를 75%로 낮춰 뒤판이 살짝 비친다.
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "📷",
                fontSize = AppTextSize.Text20,
                textAlign = TextAlign.Center,
            )
        }
    }
}
