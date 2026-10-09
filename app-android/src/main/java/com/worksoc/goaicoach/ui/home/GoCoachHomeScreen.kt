package com.worksoc.goaicoach.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.guide.GuideSurface
import com.worksoc.goaicoach.application.premium.state.FeatureId
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.persistence.ExperimentalFeaturesStore
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
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.designsystem.BotCharacterSquareIcon
import com.worksoc.goaicoach.ui.designsystem.HomeLogoPalette
import com.worksoc.goaicoach.ui.designsystem.PremiumGold
import com.worksoc.goaicoach.ui.designsystem.PremiumGoldDeep
import com.worksoc.goaicoach.ui.designsystem.PremiumGoldGradient
import com.worksoc.goaicoach.ui.designsystem.RankTierBadge
import com.worksoc.goaicoach.ui.foundation.FeatureFlags
import com.worksoc.goaicoach.ui.guide.GuideAnchor
import com.worksoc.goaicoach.ui.guide.GuideBlockingOverlays
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.kgsRankLabelFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureOverwriteWarningFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureSubtitleFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureTitleFor
import com.worksoc.goaicoach.ui.monetization.LocalPremiumUiState
import com.worksoc.goaicoach.ui.monetization.PremiumSubscribeDialog

/**
 * 0 Depth: 홈 화면 (Home Screen)
 * - 사용자가 앱 진입 시 최초로 마주하는 엔트리 화면입니다.
 * - "대국 하기" (대국 설정 로비로 이동) 및 "학습 하기" ([StudyScreen]으로 이동) 메뉴를 제공합니다.
 * - 시스템 샌드위치/소프트키 및 상단 상태바 영역 침범 방지 적용.
 */
@Composable
internal fun GoCoachHomeScreen(
    onStartMatchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStudyClick: () -> Unit,
    onGameHistoryClick: () -> Unit,
    onMyPageClick: () -> Unit,
    onBoardScanClick: () -> Unit,
    hasResumableSession: Boolean,
    onResumeClick: () -> Unit,
    /**
     * 「기력 측정 대국」(백로그 #219)을 시작한다 — 설정 창에서 고른 진영·판 크기(와 최초 1회의 시작 기력)를 준다.
     * 창과 그 상태는 이 화면이 들고, 셸은 시작하는 일만 한다(셸의 훅 예산, 함정 3).
     */
    onStartRankMeasureGame: (RankMeasureStart) -> Unit,
    /**
     * 백로그 #181 — "대국 하기" 카드 아이콘이 마지막으로 고른 AI 캐릭터를 보여주기 위해 받는다.
     * ⚠️ 새 상태 훅이 아니다 — `GoCoachApp.kt`가 이미 들고 있는 `screenState.playerSetup`(파생값)을
     * 그대로 흘려보낸 것뿐이다(셸의 훅 예산 42/42, 함정 3).
     */
    playerSetup: PlayerSetup,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    var showOverwriteWarningDialog by remember { mutableStateOf(false) }
    // 기력 측정 대국(백로그 #219) — 설정 창과, 진행 중인 대국이 있을 때 먼저 묻는 덮어쓰기 경고(저장 슬롯은 하나라 이 대국도
    // 그것을 밀어낸다). 제목은 「대국 하기」의 것을 그대로 쓰고, 본문은 끝 문장만 다르다(`rankMeasureOverwriteWarningFor`).
    val rankMeasure = LocalRankMeasureUiState.current
    var showRankMeasureDialog by remember { mutableStateOf(false) }
    var showRankMeasureOverwriteWarning by remember { mutableStateOf(false) }
    val lastSelectedAiCharacter = remember(playerSetup) { currentAiCharacterOrDefault(playerSetup) }
    // 백로그 #182 — 구독 여부로 로고·타이틀 색을 가른다. `isPurchased`만 본다(광고 1시간은
    // 제외, [[premium-character-unlock-policy]]와 같은 결). `PremiumSubscriptionCard`가
    // "구독 중" 문구를 결정하는 것과 같은 기준이라 새 상태가 아니라 CompositionLocal을 그대로 읽는다.
    val subscribed = LocalPremiumUiState.current.isPurchased
    val showsPremiumPrompt = !subscribed && FeatureFlags.isPurchaseEnabled
    var showSubscribeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isCameraBoardScanEnabled = ExperimentalFeaturesStore(context).isCameraBoardScanEnabled()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // 이어하기 버튼이 뜨면 고정 자식(상단 Row + 버튼 + 카드 4장)만으로 화면 세로를
                // 넘긴다. 스크롤이 없으면 Column이 그 부족분을 **가중치 자식**의 높이에서 깎아내고,
                // 그 안의 제목 `Text`가 maxHeight 몇 dp로 측정돼 `clipRect`로 잘려 나간다 — 잘린
                // 그 선에서 이어하기 버튼이 시작하니 "제목 위에 겹쳐 보이는" 것이다(#28).
                //
                // ⚠️ 아래 Spacer 전환과 **반드시 함께** 가야 한다. 이것만 넣으면 maxHeight가
                // Infinity가 돼 가중치 자식이 0으로 붕괴하고 로고가 통째로 사라진다.
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.Space24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 좌=마이 페이지, 우=설정(#34, 2026-08-30 사용자 지시). 언어 칩이 설정 안으로
            // 들어가면서 우측이 비었고, 그 자리를 설정이 받고 좌측을 마이 페이지가 가져갔다.
            // 마이 페이지는 #24에서 하단 카드로 났는데, 카드 넉 장이 세로를 빠듯하게 만든
            // 장본인이기도 했다(#28).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HomeTopChip(emoji = "🧑", label = strings.myPageTitle, onClick = onMyPageClick)
                HomeTopChip(emoji = "⚙", label = strings.settingsTitle, onClick = onSettingsClick)
            }

            // 남는 세로 공간을 위·아래로 나눠 로고 블록을 가운데 둔다. 예전에는 **블록 자체가**
            // `weight(1f)`였는데, 그러면 "남는 공간"이 곧 블록의 **최대 높이**가 된다 — 공간이
            // 모자라는 순간 제목과 부제가 잘려 나갔다. 가중치를 여백으로 옮기면 0까지 줄어드는
            // 쪽은 여백이고, 블록은 제 크기를 지킨 채 스크롤 대상이 된다.
            Spacer(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 백로그 #182 — "Premium?"은 아이콘 우상단, 점선 원 테두리 바로 옆에서 이어지듯
                // 배치한다(2026-09-21 사용자 지시). 배경 없이 금색 글자만 두고, 아이콘과 글자
                // 둘 다 탭하면 구독 다이얼로그가 뜬다 — 표적을 하나로 묶지 않은 이유는 "Premium?"이
                // `.offset()`으로 배지 바깥까지 밀려나 있어(오프셋은 그리기 위치만 바꾸고 부모
                // 레이아웃 크기엔 반영되지 않는다) 부모 하나에만 `clickable`을 걸면 그 영역을
                // 못 덮기 때문이다.
                val premiumBadgeClickModifier = if (showsPremiumPrompt) {
                    Modifier.clip(CircleShape).clickable { showSubscribeDialog = true }
                } else {
                    Modifier
                }

                Box {
                    Box(modifier = premiumBadgeClickModifier) {
                        GoStoneLogoBadge(subscribed = subscribed, showsPremiumPrompt = showsPremiumPrompt)
                    }

                    if (showsPremiumPrompt) {
                        Text(
                            text = "Premium?",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PremiumGold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 40.dp, y = (-2).dp)
                                .clickable { showSubscribeDialog = true }
                                .padding(horizontal = 7.dp, vertical = AppSpacing.Space2),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.Space20))

                Text(
                    text = strings.appTitle,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (subscribed) PremiumGoldDeep else MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = strings.homeTagline,
                    fontSize = AppTextSize.Text14,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = AppSpacing.Space6),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 저장된 대국이 있을 때 노출되는 확대 및 깜빡이는 "이전 대국 이어하기" 버튼
            if (hasResumableSession) {
                val infiniteTransition = rememberInfiniteTransition(label = "resumeBlink")
                val blinkingAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "blinkingAlpha",
                )

                Box(
                    modifier = Modifier
                        .padding(bottom = AppSpacing.Space16)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onResumeClick)
                        .padding(horizontal = AppSpacing.Space18, vertical = AppSpacing.Space10),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "▶ " + strings.resumeTitle,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = AppTextSize.Text18,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer { alpha = blinkingAlpha },
                    )
                }
            }

            // "대국 하기" (Start Match) 카드 — 이전 대국 존재 시 확인 팝업 분기
            //
            // ⚠️ **첫돌이 말풍선을 이 열의 새 자식으로 넣지 말 것**(백로그 #128 ③). 자식을 더하면
            // 카드가 아래로 밀려 **사용자가 눌러야 할 표적이 움직이고**, #28이 만졌던 가중치·스크롤
            // 산수에 다시 손대는 셈이 된다. 그래서 **호출부만 `Box`로 감싸고** 말풍선은 그 안에서
            // 겹친다 — `MenuCard`의 본문과 시그니처는 한 글자도 바뀌지 않았다.
            // ⚠️ 말풍선은 `ZeroSizeOverlay`가 **0×0으로 보고**하므로 배율이 올라 말풍선이 커져도
            //   Box는 카드 높이 그대로다(그 이유는 그 함수의 KDoc — 설계안 셋이 여기서 틀렸다).
            Box(modifier = Modifier.fillMaxWidth()) {
                MenuCard(
                    title = strings.startMatch,
                    subtitle = strings.homeStartMatchSubtitle,
                    onClick = {
                        if (hasResumableSession) {
                            showOverwriteWarningDialog = true
                        } else {
                            onStartMatchClick()
                        }
                    },
                    icon = { GamePlayPreviewIcon(character = lastSelectedAiCharacter) },
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

            // 「기력 측정 대국」 — **두 번째 메뉴**(백로그 #219, 2026-10-06 사용자). 자기 기력과 같은 급수의 AI와 두고 결과가 기력을 옮긴다.
            // ⚠️ 사람 모델이 있는 기기에서만 보인다(`RankMeasureUiState.isAvailable`) — 없으면 상대가 그 급수처럼 두지 못해 잰 기력이 뜻을 잃는다.
            if (rankMeasure.isAvailable) {
                Spacer(modifier = Modifier.height(AppSpacing.Space12))
                MenuCard(
                    title = rankMeasureTitleFor(strings.language),
                    subtitle = rankMeasureSubtitleFor(strings.language),
                    onClick = { if (hasResumableSession) showRankMeasureOverwriteWarning = true else showRankMeasureDialog = true },
                    icon = { RankMeasureIcon(rank = rankMeasure.state.rank, rankLabel = kgsRankLabelFor(strings.language, rankMeasure.state.rank)) },
                )
            }

            // 두는 것이 아닌 메뉴는 **반 폭 타일**이다(백로그 #237) — 위의 대국 카드 둘보다 작고 납작해서, 색이 아니라 크기로 한 단계 아래임이 읽힌다.
            // 순서는 그대로다: 「대국 기록」이 먼저(백로그 #45 — 두고 → 돌아보는 동선이 앱 안에서 이어진다), 그다음 「학습 하기」.
            // 🧪 「사진 분석」은 실험실 기능(#179)이라 설정 > 실험실에서 켠 경우에만 셋째 타일로 붙는다 — 홀로 남은 타일도 반 폭을 지킨다.
            val tiles = buildList {
                add(HomeMenuTile(strings.gameHistoryTitle, onGameHistoryClick) { GameHistoryBoardIcon() })
                add(HomeMenuTile(strings.study, onStudyClick) { StudyPreviewIcon() })
                if (isCameraBoardScanEnabled) {
                    add(HomeMenuTile(strings.featureShortName(FeatureId.BoardScan), onBoardScanClick) { BoardScanPreviewIcon() })
                }
            }
            tiles.chunked(2).forEach { row ->
                Spacer(modifier = Modifier.height(AppSpacing.Space12))
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
                    row.forEach { tile -> MenuTile(tile = tile, modifier = Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }

            // 마이 페이지 카드는 여기 없다 — 좌상단 칩으로 올라갔다(#34). 목적지 자체는
            // 그대로이고 진입점만 옮겼다.
        }
    }

    // 이전 대국 존재 상태에서 새 대국 하기 선택 시 확인 경고 팝업
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
                        showRankMeasureDialog = true
                    },
                ) { Text(strings.confirm) }
            },
            dismissButton = { TextButton(onClick = { showRankMeasureOverwriteWarning = false }) { Text(strings.cancel) } },
        )
    }
    // 기력 변동 팝업(사용자 2026-10-07) — 뒤로 가기로 기권한 판은 나온 뒤 여기서 알린다(2연패 강급 등).
    RankMeasureChangeDialog(rankMeasure)
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

/**
 * 홈 화면 상단 양 끝에 놓이는 칩 버튼(#34). 좌측 마이 페이지와 우측 설정이 **같은 모양**을
 * 써야 해서, 설정 전용이던 `HomeSettingsButton`을 이모지와 라벨만 받는 형태로 일반화했다.
 *
 * 언어 선택 칩(`HomeLanguageSelector`)도 같은 겉모습이었지만 그쪽은 드롭다운을 품고 있어
 * 합치지 않았다 — 지금은 설정 화면 안에서만 쓰인다.
 */
@Composable
private fun HomeTopChip(emoji: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadius.Corner18))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.Space14, vertical = AppSpacing.Space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space4),
    ) {
        Text(
            text = "$emoji $label",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = AppTextSize.Text12,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 백로그 #182 — 테두리로 프리미엄 상태를 알린다: 구독 중이면 금색 실선, 구독 창구를
 * 보여줄 수 있으면(미구독 + [FeatureFlags.isPurchaseEnabled]) 금색 점선, 그 외엔 원래
 * 중립 테두리. `Modifier.border`엔 점선이 없어 점선만 `drawWithContent`로 직접 그린다.
 */
@Composable
private fun GoStoneLogoBadge(subscribed: Boolean, showsPremiumPrompt: Boolean) {
    val stoneSizeDp = 51.dp
    // 광원 위치/반경을 실제 픽셀 기준으로 계산해, 하드코딩된 px 값이 스톤 크기와 어긋나
    // 그라데이션이 중앙의 작은 얼룩으로만 보이던 문제(특히 흑돌에서 두드러짐)를 없앤다.
    val stoneSizePx = with(LocalDensity.current) { stoneSizeDp.toPx() }
    val highlightCenter = Offset(stoneSizePx * 0.32f, stoneSizePx * 0.28f)
    val highlightRadius = stoneSizePx * 0.85f
    val dashedStrokeWidthPx = with(LocalDensity.current) { 2.dp.toPx() }

    val borderModifier = when {
        subscribed -> Modifier.border(AppBorderWidth.Strong, PremiumGoldGradient, CircleShape)
        showsPremiumPrompt -> Modifier.drawWithContent {
            drawContent()
            drawCircle(
                color = PremiumGold,
                radius = (size.minDimension - dashedStrokeWidthPx) / 2f,
                style = Stroke(
                    width = dashedStrokeWidthPx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                ),
            )
        }
        else -> Modifier.border(AppBorderWidth.Hairline, HomeLogoPalette.RingBorder, CircleShape)
    }

    Box(
        modifier = Modifier
            .size(125.dp)
            .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
            .background(HomeLogoPalette.Backdrop, CircleShape)
            .then(borderModifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // 겹침은 offset()이 아니라 spacedBy(음수)로 표현한다 — offset()은 레이아웃 크기에는
            // 반영되지 않고 그리기 위치만 바꾸므로, 부모 Box의 가운데 정렬 계산이 겹친 만큼을
            // 반영하지 못해 전체가 살짝 왼쪽으로 치우쳐 보이는 문제가 있었다.
            horizontalArrangement = Arrangement.spacedBy((-13).dp),
        ) {
            // 흑돌 (Black Stone) — 백돌과 동일한 광원 위치에 밝은 하이라이트를 두어
            // 같은 수준의 입체감/광택이 느껴지도록 4단계 그라데이션을 사용한다.
            Box(
                modifier = Modifier
                    .size(stoneSizeDp)
                    .shadow(elevation = 4.dp, shape = CircleShape)
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
                    .shadow(elevation = 4.dp, shape = CircleShape)
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

/**
 * 홈의 **대국 카드** — 「대국 하기」와 「기력 측정 대국」, 두는 메뉴 둘이 같은 모습으로 쓴다(백로그 #237, 2026-10-09 사용자).
 *
 * ## 초록 바탕을 버린 이유
 * 전에는 「대국 하기」만 브랜드 초록을 칠했다. 「기력 측정 대국」도 두는 메뉴라 같은 대접을 해야 했는데, 둘 다 칠하면 화면
 * 위쪽이 초록 벽이 되고 **구간의 테두리(`RankTierBadge`)가 초록에 묻힌다**(플래티넘의 청록이 특히). 그래서 바탕은 흰색으로
 * 두고 살짝 띄우며, 초록은 오른쪽의 ▶ 하나로만 남긴다 — "누르면 둔다"는 표시다. 아래 메뉴와의 위계는 색이 아니라
 * **크기**가 만든다([MenuTile]).
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
    /** 카드 좌측의 정사각형 그림(백로그 #181) — 상대 캐릭터가 앉은 판, 또는 구간의 테두리에 담긴 내 기력. */
    icon: @Composable () -> Unit,
) {
    // ⚠️ 누르기는 `Card(onClick)`에 맡긴다 — 바깥 modifier에 `clip` + `clickable`을 걸면 그림자까지 잘려 카드가 떠 보이지 않는다.
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(AppRadius.Corner14),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = MenuCardElevation),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 고정 높이가 아니라 **최소** 높이다(#29) — 부제가 석 줄이 되는 언어·글꼴 배율에서도 썰리지 않고 카드가 자란다.
                // 평소(부제 두 줄까지)에는 내용이 이 높이 안에 들어 카드 둘의 키가 같다. 홈은 #28에서 스크롤을 얻었으므로
                // 카드가 커져도 화면이 깨지지 않는다.
                // ⚠️ 하한은 `Card`가 아니라 이 줄에 건다 — M3 `Card`는 내용을 modifier 없는 `Column`으로 감싼다.
                .heightIn(min = MenuCardMinHeight)
                .padding(
                    start = AppSpacing.Space8,
                    top = AppSpacing.Space16,
                    end = AppSpacing.Space16,
                    bottom = AppSpacing.Space16,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ⚠️ **배경 없이 카드 색 위에 그대로 얹는다**(2026-09-21 사용자 결정). 그림 둘 다 스스로 또렷하다 —
            // 판은 제 나무 바탕을 그리고, 구간의 테두리는 어두운 판을 두른다.
            Box(
                modifier = Modifier
                    .size(MenuCardIconSize)
                    .padding(AppSpacing.Space4),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(AppSpacing.Space10))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = AppTextSize.Text20,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(AppSpacing.Space4))

                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = AppTextSize.Text13,
                    // 줄 높이를 상속(24sp)에 맡기지 않는다 — 두 줄 부제가 평소 높이 안에 들어야 카드 둘의 키가 같다.
                    lineHeight = AppTextSize.Text18,
                    fontWeight = FontWeight.Normal,
                )
            }
            Spacer(modifier = Modifier.width(AppSpacing.Space10))
            // 장식이다 — 누르는 표적은 카드 전체라 따로 읽어 줄 이름이 없다.
            Box(
                modifier = Modifier
                    .size(MenuCardPlayButtonSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(MenuCardPlayGlyphSize),
                )
            }
        }
    }
}

/** [MenuCard] 그림 자리(바깥 정사각형)의 한 변 — 안쪽 그림은 사방 4dp씩 작은 60dp다. */
private val MenuCardIconSize = 68.dp
private val MenuCardMinHeight = 104.dp
private val MenuCardElevation = 3.dp
private val MenuCardPlayButtonSize = 36.dp
private val MenuCardPlayGlyphSize = 22.dp

/** 홈의 반 폭 타일 하나 — 제목과 그림, 누르면 갈 곳. */
private class HomeMenuTile(val title: String, val onClick: () -> Unit, val icon: @Composable () -> Unit)

/**
 * 홈의 **반 폭 타일** — 두는 것이 아닌 메뉴(대국 기록 · 학습 하기 · 실험실의 사진 분석)가 쓴다(백로그 #237).
 * [MenuCard]보다 작고 납작하며(그림자 없음) **부제가 없다** — 제목만으로 무엇인지 읽히는 메뉴들이고, 반 폭에는 부제가 설 자리가 없다.
 */
@Composable
private fun MenuTile(tile: HomeMenuTile, modifier: Modifier = Modifier) {
    Card(
        onClick = tile.onClick,
        shape = RoundedCornerShape(AppRadius.Corner14),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier,
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
 * "대국 하기" 카드 아이콘 — 마지막으로 고른 AI 캐릭터, 없거나 기본 설정이면 기본값(레벨3, "수제자 반상")을
 * 돌려준다(백로그 #181, 2026-09-21 사용자 요청으로 기본값을 판다에서 수제자 반상으로 변경).
 * [PlayerSetup]이 기본값(흑 사람, 백 AI 1단계)인 초기 상태이거나, 두 좌석 중 AI가 없거나
 * `FastBeginner` 그룹이 아니면 기본값인 레벨 3("수제자 반상")을 돌려준다.
 */
internal fun currentAiCharacterOrDefault(playerSetup: PlayerSetup): BotCharacter {
    if (playerSetup == DefaultPlayerSetup) {
        return defaultAiCharacter()
    }
    val aiPlayLevel = listOf(playerSetup.white, playerSetup.black)
        .firstOrNull { side -> side.controller == SeatController.Ai }
        ?.playLevel
    val fastBeginnerLevel = if (aiPlayLevel?.group == PlayLevelGroup.FastBeginner) {
        aiPlayLevel.safeLevel
    } else {
        // 급수를 직접 정한 상대(백로그 #217 — 지금은 일반 설정에 남지 않지만, 남아 있어도)는 그 급수가 속한 구간의 캐릭터 얼굴을 빌린다.
        aiPlayLevel?.customRank()?.let(::customRankFallbackTier) ?: DefaultAiCharacterLevel
    }
    return BotCharacterCatalog.forPlayLevel(
        PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = fastBeginnerLevel),
    ) ?: defaultAiCharacter()
}

private val DefaultPlayerSetup = PlayerSetup()
private const val DefaultAiCharacterLevel = 3
private fun defaultAiCharacter(): BotCharacter =
    BotCharacterCatalog.forPlayLevel(
        PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = DefaultAiCharacterLevel),
    ) ?: BotCharacterCatalog.fastBeginnerRoster.getOrNull(2)
        ?: BotCharacterCatalog.fastBeginnerRoster.first()

/**
 * "대국 하기" 카드의 정적 국면 — **두 점**만 놓는다(2026-09-22 사용자 지시). 「학습 하기」가
 * 세 점인 것과 일부러 다르다: 이쪽은 *"이제 두기 시작한다"* 이고 저쪽은 *"공부할 모양이 있다"* 다.
 * `BoardRules.play`를 거치지 않고 바로 앉히는 것은 [StudyPreviewGameState]와 같은 이유다(장식용).
 */
private val GamePlayPreviewGameState: GameState = GameState.empty(
    boardSize = BoardSize.Nine,
    ruleset = Ruleset.Japanese,
).copy(
    stones = mapOf(
        BoardCoordinate(row = 2, column = 6) to StoneColor.Black,
        BoardCoordinate(row = 6, column = 2) to StoneColor.White,
    ),
)

/**
 * "대국 하기" 카드 아이콘 — 바둑판(두 점) 위 **좌상단**에 상대 캐릭터를 70%로 얹는다
 * (2026-09-22 사용자 지시, 백로그 #192).
 *
 * ## ⚠️ 전에는 캐릭터가 슬롯을 통째로 채웠다
 * 그래서 카드 넷 중 이것만 판이 없었다 — 「대국 기록」·「학습 하기」·「사진 분석」은 전부 판 위에
 * 무언가를 얹은 모양이다. 판을 깔면서 **넷이 한 식구로 읽힌다.**
 *
 * ## ⚠️ 배지 자리가 다른 것은 일부러다
 * 「학습 하기」의 돋보기는 **우하단**이고 이쪽 캐릭터는 **좌상단**이다(사용자 지시). 캐릭터는
 * 원형 배지가 아니라 **그림 그대로**라, 우하단에 두면 판의 초반 포석과 겹쳐 지저분해진다.
 *
 * ⚠️ **[FirstDolGuideReplay]가 이 컴포저블을 함께 쓴다** — 다시보기 ③이 홈의 **진짜 카드**를
 * 조립하기 때문이다. 여기만 바꾸고 그쪽을 두면 **다시보기가 없는 화면을 보여 준다**(그 파일이
 * 네 번 겪었다고 적어 둔 바로 그 사고). `internal`인 이유가 그것이다.
 */
@Composable
internal fun GamePlayPreviewIcon(character: BotCharacter) {
    Box(modifier = Modifier.fillMaxSize()) {
        GoBoard(
            gameState = GamePlayPreviewGameState,
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
        BotCharacterSquareIcon(
            character = character,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxSize(GamePlayPreviewCharacterFraction),
        )
    }
}

/** 판 한 변 대비 캐릭터 크기 — 슬롯을 꽉 채우던 것의 **70%**(2026-09-22 사용자 지시). */
private const val GamePlayPreviewCharacterFraction = 0.7f

/**
 * "대국 기록" 카드 아이콘이 그릴 9x9 종국 국면(백로그 #181, 2026-09-21 사용자 요청으로
 * 13x13 137수 참고 기보에서 교체). 13x13은 아이콘 크기(84dp)에서 169칸이 너무 촘촘해
 * 돌인지 격자선인지 구분이 안 됐다 — 9x9(81칸)가 그 크기에서 읽힌다.
 *
 * ⚠️ **가짜로 지어낸 배치가 아니다.** 에뮬레이터에서 실제로 둔 9x9 대국(문하생 판다 대
 * 관장 천원, 105수, 흑 6.5집승, `game_history/replay/1789951644440-318459.json`)을
 * `GameReplayCodec.decode` + `buildGameReplayTimeline`으로 그대로 재생해 얻은 **종국 결과**를
 * 옮겼다 — 따낸 돌까지 규칙대로 반영된 진짜 종국 모양이다(원본 파일은 이 기기에만 있는 테스트
 * 데이터라 에셋으로 묶지 않고, 그 결과만 고정값으로 옮겨 왔다).
 */
private val GameHistoryPreviewGameState: GameState = GameState.empty(
    boardSize = BoardSize.Nine,
    ruleset = Ruleset.Japanese,
).copy(
    stones = mapOf(
        BoardCoordinate(row = 0, column = 1) to StoneColor.White,
        BoardCoordinate(row = 0, column = 2) to StoneColor.Black,
        BoardCoordinate(row = 0, column = 3) to StoneColor.Black,
        BoardCoordinate(row = 0, column = 4) to StoneColor.White,
        BoardCoordinate(row = 0, column = 5) to StoneColor.White,
        BoardCoordinate(row = 0, column = 7) to StoneColor.White,
        BoardCoordinate(row = 1, column = 0) to StoneColor.White,
        BoardCoordinate(row = 1, column = 2) to StoneColor.Black,
        BoardCoordinate(row = 1, column = 3) to StoneColor.White,
        BoardCoordinate(row = 1, column = 4) to StoneColor.White,
        BoardCoordinate(row = 1, column = 5) to StoneColor.White,
        BoardCoordinate(row = 1, column = 8) to StoneColor.White,
        BoardCoordinate(row = 2, column = 0) to StoneColor.Black,
        BoardCoordinate(row = 2, column = 1) to StoneColor.Black,
        BoardCoordinate(row = 2, column = 2) to StoneColor.Black,
        BoardCoordinate(row = 2, column = 3) to StoneColor.Black,
        BoardCoordinate(row = 2, column = 4) to StoneColor.White,
        BoardCoordinate(row = 2, column = 5) to StoneColor.White,
        BoardCoordinate(row = 2, column = 6) to StoneColor.White,
        BoardCoordinate(row = 2, column = 7) to StoneColor.White,
        BoardCoordinate(row = 2, column = 8) to StoneColor.White,
        BoardCoordinate(row = 3, column = 0) to StoneColor.Black,
        BoardCoordinate(row = 3, column = 2) to StoneColor.Black,
        BoardCoordinate(row = 3, column = 3) to StoneColor.Black,
        BoardCoordinate(row = 3, column = 4) to StoneColor.Black,
        BoardCoordinate(row = 3, column = 5) to StoneColor.White,
        BoardCoordinate(row = 3, column = 6) to StoneColor.Black,
        BoardCoordinate(row = 3, column = 7) to StoneColor.Black,
        BoardCoordinate(row = 3, column = 8) to StoneColor.Black,
        BoardCoordinate(row = 4, column = 3) to StoneColor.Black,
        BoardCoordinate(row = 4, column = 4) to StoneColor.Black,
        BoardCoordinate(row = 4, column = 5) to StoneColor.Black,
        BoardCoordinate(row = 4, column = 6) to StoneColor.Black,
        BoardCoordinate(row = 4, column = 7) to StoneColor.White,
        BoardCoordinate(row = 4, column = 8) to StoneColor.White,
        BoardCoordinate(row = 5, column = 0) to StoneColor.Black,
        BoardCoordinate(row = 5, column = 2) to StoneColor.Black,
        BoardCoordinate(row = 5, column = 3) to StoneColor.Black,
        BoardCoordinate(row = 5, column = 4) to StoneColor.Black,
        BoardCoordinate(row = 5, column = 5) to StoneColor.White,
        BoardCoordinate(row = 5, column = 6) to StoneColor.Black,
        BoardCoordinate(row = 5, column = 7) to StoneColor.White,
        BoardCoordinate(row = 5, column = 8) to StoneColor.White,
        BoardCoordinate(row = 6, column = 4) to StoneColor.Black,
        BoardCoordinate(row = 6, column = 5) to StoneColor.White,
        BoardCoordinate(row = 6, column = 6) to StoneColor.White,
        BoardCoordinate(row = 7, column = 0) to StoneColor.Black,
        BoardCoordinate(row = 7, column = 2) to StoneColor.Black,
        BoardCoordinate(row = 7, column = 3) to StoneColor.Black,
        BoardCoordinate(row = 7, column = 4) to StoneColor.Black,
        BoardCoordinate(row = 7, column = 5) to StoneColor.Black,
        BoardCoordinate(row = 7, column = 6) to StoneColor.White,
        BoardCoordinate(row = 7, column = 7) to StoneColor.White,
        BoardCoordinate(row = 7, column = 8) to StoneColor.White,
        BoardCoordinate(row = 8, column = 1) to StoneColor.Black,
        BoardCoordinate(row = 8, column = 2) to StoneColor.Black,
        BoardCoordinate(row = 8, column = 3) to StoneColor.Black,
        BoardCoordinate(row = 8, column = 4) to StoneColor.Black,
        BoardCoordinate(row = 8, column = 5) to StoneColor.White,
        BoardCoordinate(row = 8, column = 6) to StoneColor.White,
    ),
)

/**
 * 「기력 측정 대국」 카드의 아이콘 — **지금의 내 기력**을 글자로 보인다(`20급`). 다른 카드의 판 그림과 같은 자리·같은 크기다.
 * 카드를 열지 않아도 기력이 보이고, 그 기력이 속한 **구간의 테두리**가 둘러선다(백로그 #236 — 브론즈부터 그랜드 마스터까지 일곱,
 * 구간의 경계는 `KgsRankTier`, 테두리의 모양과 색은 [RankTierBadge]).
 */
@Composable
private fun RankMeasureIcon(rank: KgsRank, rankLabel: String) {
    RankTierBadge(rank = rank, label = rankLabel, modifier = Modifier.fillMaxSize())
}

/**
 * 「보드 미리보기」(`GameSetupLobby.kt`)와 같은 `GoBoard`를 아이콘 크기로 그린 뒤, **「기록」을
 * 뜻하는 배지**를 얹는다(2026-09-22 사용자 지시, 백로그 #192 — 「학습 하기」를 참고하라는 지시였다).
 * `isGameEnded = true`로 둬 끝난 대국다운 톤을 준다 — 실제로 끝난 판이다.
 *
 * ## ⚠️ 이모지가 아니라 벡터다 — 지시를 그대로 옮기지 않은 자리
 * 사용자는 *"기록을 의미하는 이모지"* 라고 했지만, **같은 지시가 참고하라고 가리킨
 * [StudyPreviewIcon]이 이모지를 일부러 쓰지 않는다**(#181: *"기기·글꼴에 따라 렌더링이 갈리는
 * 이모지보다 크기·색이 항상 예측 가능하다"*). 배지는 원형 흰 바탕에 **틴트된 심볼**이라 이모지를
 * 넣으면 그 안에서 혼자 총천연색이 되고 기기마다 크기가 튄다. 그래서 **모양은 참고 대상에 맞추고
 * 뜻만 「기록」으로** 바꿨다 — 그것이 두 지시를 동시에 만족시키는 유일한 해석이다.
 *
 * ⚠️ **`Icons.Filled.History`를 쓰고 싶었지만 못 썼다** — 그것은 `material-icons-extended`에 있고
 * 이 앱은 **코어 세트만** 의존한다(확장 세트는 APK를 크게 불린다). 코어 안에서 「지나간 대국의
 * 목록」에 가장 가까운 것이 `AutoMirrored.Filled.List`이고, **대국 기록 화면이 실제로 목록**이다.
 * ⚠️ `AutoMirrored`인 이유는 RTL에서 좌우가 뒤집혀야 하기 때문이다(이 앱은 아직 RTL 언어가
 * 없지만, 뒤집히면 안 되는 심볼과 섞이지 않게 기본형을 지킨다).
 */
@Composable
private fun GameHistoryBoardIcon() {
    Box(modifier = Modifier.fillMaxSize()) {
        GoBoard(
            gameState = GameHistoryPreviewGameState,
            candidateMoves = emptyList(),
            moveReviews = emptyList(),
            ownershipEstimate = null,
            uxOptions = KaTrainUxOptions(),
            inputEnabled = false,
            engineActivityIndicator = null,
            modifier = Modifier.fillMaxSize(),
            tentativeMove = null,
            onCoordinateTap = {},
            isGameEnded = true,
            isEngineBusy = false,
        )
        // ⚠️ **자리·크기·바탕을 [StudyPreviewIcon]과 똑같이 맞춘다** — 두 카드가 나란히 서므로
        // 하나만 어긋나면 그것이 먼저 눈에 띈다. 상수도 그쪽 것을 그대로 쓴다.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxSize(StudyPreviewMagnifierSizeFraction)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(0.7f),
            )
        }
    }
}

/**
 * "학습 하기" 카드의 정적 국면 — 실제 대국 규칙(`BoardRules.play`)을 거치지 않고 초반 포석
 * 몇 수를 바로 앉힌다(백로그 #181, 합법성 검사가 필요 없는 순수 장식용). `object`가 아니라
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

/**
 * "학습 하기" 카드 아이콘 — 바둑판(초반 포석 세 점) 위에 돋보기를 판 한 변의 60% 크기로
 * 겹친다(백로그 #181, 2026-09-22 120% 확대). 돋보기는 이모지 대신 `Icons.Filled.Search`
 * 벡터를 쓴다 — 기기·글꼴에 따라 렌더링이 갈리는 이모지보다 크기·색이 항상 예측 가능하다.
 */
@Composable
private fun StudyPreviewIcon() {
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
                .fillMaxSize(StudyPreviewMagnifierSizeFraction)
                .clip(CircleShape)
                // 2026-09-22 사용자 요청 — 흰 배경 불투명도를 75%로 낮춰 뒤판이 살짝 비친다.
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(0.7f),
            )
        }
    }
}

/** 판 한 변 대비 돋보기·카메라 배지의 비율(기존 50%에서 120% 확대한 60%). */
private const val StudyPreviewMagnifierSizeFraction = 0.6f

/**
 * "바둑판 사진 분석" 카드 아이콘 — 바둑판 위에 카메라 심볼을 겹친 프리뷰 배지.
 */
@Composable
private fun BoardScanPreviewIcon() {
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
                .fillMaxSize(StudyPreviewMagnifierSizeFraction)
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
