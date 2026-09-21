package com.worksoc.goaicoach.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.guide.GuideSurface
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor

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
    hasResumableSession: Boolean,
    onResumeClick: () -> Unit,
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
    val lastSelectedAiCharacter = remember(playerSetup) { currentAiCharacterOrDefault(playerSetup) }
    // 백로그 #182 — 구독 여부로 로고·타이틀 색을 가른다. `isPurchased`만 본다(광고 1시간은
    // 제외, [[premium-character-unlock-policy]]와 같은 결). `PremiumSubscriptionCard`가
    // "구독 중" 문구를 결정하는 것과 같은 기준이라 새 상태가 아니라 CompositionLocal을 그대로 읽는다.
    val subscribed = LocalPremiumUiState.current.isPurchased
    val showsPremiumPrompt = !subscribed && FeatureFlags.isPurchaseEnabled
    var showSubscribeDialog by remember { mutableStateOf(false) }

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
                .padding(24.dp),
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
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = strings.appTitle,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (subscribed) PremiumGoldDeep else MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = strings.homeTagline,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
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
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onResumeClick)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "▶ " + strings.resumeTitle,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleColor = Color.White,
                    subtitleColor = Color.White.copy(alpha = 0.85f),
                    onClick = {
                        if (hasResumableSession) {
                            showOverwriteWarningDialog = true
                        } else {
                            onStartMatchClick()
                        }
                    },
                    icon = { BotCharacterSquareIcon(character = lastSelectedAiCharacter) },
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

            Spacer(modifier = Modifier.height(16.dp))

            // "대국 기록" (Game History) 카드 — 대국 하기 바로 아래다(백로그 #45, 2026-08-30
            // 사용자 지시). 두고 → 돌아보는 동선이 앱 안에서 이어지는데, 학습이 사이에 끼면
            // 그 흐름이 유튜브 링크로 한 번 끊긴다.
            MenuCard(
                title = strings.gameHistoryTitle,
                subtitle = strings.homeGameHistorySubtitle,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                subtitleColor = MaterialTheme.colorScheme.secondary,
                onClick = onGameHistoryClick,
                icon = { GameHistoryBoardIcon() },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // "학습 하기" (Study Mode) 카드
            MenuCard(
                title = strings.study,
                subtitle = strings.homeStudySubtitle,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                subtitleColor = MaterialTheme.colorScheme.secondary,
                onClick = onStudyClick,
                icon = { StudyPreviewIcon() },
            )

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
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "$emoji $label",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
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
        subscribed -> Modifier.border(2.dp, PremiumGoldGradient, CircleShape)
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
        else -> Modifier.border(1.dp, Color(0xFFE5DDD0), CircleShape)
    }

    Box(
        modifier = Modifier
            .size(125.dp)
            .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
            .background(Color(0xFFF5F0E6), CircleShape)
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
                            colors = listOf(
                                Color(0xFF7A7A7A),
                                Color(0xFF3D3D3D),
                                Color(0xFF161616),
                                Color(0xFF000000),
                            ),
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
                    .border(1.dp, Color(0xFFD3C9B8), CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFF7F3EB),
                                Color(0xFFD6CCC0),
                            ),
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
    containerColor: Color,
    titleColor: Color,
    subtitleColor: Color,
    onClick: () -> Unit,
    /**
     * 카드 좌측의 정사각형 아이콘(백로그 #181). 없으면 예전 레이아웃(제목·부제만)과 1px도
     * 다르지 않다 — `null`이 기본값이라 이 함수의 다른 호출부(`FirstDolGuideReplay.kt`가 넘기지
     * 않는 카드가 생기더라도)는 손댈 필요가 없다.
     */
    icon: (@Composable () -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 카드 높이를 정하는 건 이 상자다 — 고정 `height`가 아니라 **최소** 높이인
                // 이유가 #29다.
                //
                // 산수: 120dp 고정이면 패딩 24dp를 위아래로 뺀 72dp 안에 다 들어가야 한다.
                // 그런데 아래 두 `Text`는 `fontSize`만 덮고 **줄 높이는 상속한다** — M3가
                // `LocalTextStyle`로 깔아 둔 `bodyLarge`의 `lineHeight = 24.sp`다. 그래서
                // 13sp 부제도 한 줄에 24dp를 먹는다(실측 줄 피치 정확히 24.0dp). 제목 24 +
                // 간격 4 + 부제 1줄 24 = 52dp는 들어가지만, 부제가 **두 줄이 되는 순간**
                // 76dp라 72dp를 넘겨 마지막 줄이 `clip`에 썰렸다. 두 줄은 드문 일이 아니다 —
                // 일본어는 411dp 기본 배율에서 이미, 한국어는 360dp에서 그렇게 된다.
                //
                // 최소 높이면 평소 모습은 1px도 바뀌지 않고(내용이 72dp에 들면 카드는 여전히
                // 120dp) 넘칠 때만 자란다. 홈은 #28에서 스크롤을 얻었으므로 카드가 커져도
                // 화면이 깨지지 않는다.
                //
                // ⚠️ 하한을 `Card` modifier로 올리지 마라. M3 `Card`는 내용을 modifier 없는
                // `Column`으로 감싸는데, 그러면 maxHeight가 Infinity가 돼 배경을 칠하는 이
                // 상자만 내용 높이로 줄고 **카드 아래에 칠하지 않은 띠**가 드러난다. 실제로
                // 그렇게 짰다가 봤다.
                .heightIn(min = 120.dp)
                .background(containerColor)
                // ⚠️ **아이콘이 있으면 왼쪽만 극단적으로 좁힌다**(2026-09-21 사용자 요청) —
                // 제목·부제 앞의 위·아래·오른쪽 여백(24dp)은 그대로 두고, 아이콘과 카드 왼쪽
                // 테두리 사이만 좁혀 아이콘이 카드에 바짝 붙게 한다.
                .padding(
                    start = if (icon != null) MenuCardIconStartPadding else 24.dp,
                    top = 24.dp,
                    end = 24.dp,
                    bottom = 24.dp,
                ),
            // 카드가 최소 높이일 때 내용을 세로 가운데에 둔다. 예전에는 `fillMaxSize` 자식
            // Column의 `Arrangement.Center`가 하던 일인데, 높이가 내용에 따라 달라진 지금은
            // 높이를 아는 쪽이 상자뿐이라 여기로 옮겼다.
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    // ⚠️ **배경 없이 카드 색 위에 그대로 얹는다**(2026-09-21 사용자 결정 — 흰 배경
                    // 제거). 아이콘 셋 다 자체적으로 또렷하다(캐릭터 원화는 투명 배경, 보드는
                    // 스스로 나무판 배경을 그린다) — 별도 컨테이너 배경이 없어도 안 묻힌다.
                    Box(
                        modifier = Modifier
                            .size(MenuCardIconSize)
                            // 2026-09-21 사용자 요청 — 좌측은 최소, 상·하·우측은 적당히.
                            // 카드 왼쪽 테두리와의 거리는 `MenuCardIconStartPadding`이 이미
                            // 맡고 있어 이 안쪽 여백까지 왼쪽에 더 주면 이중으로 벌어진다.
                            .padding(start = 0.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        icon()
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        color = titleColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = subtitle,
                        color = subtitleColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * [MenuCard] 아이콘 슬롯(바깥 정사각형)의 한 변. 안쪽 그림은 비대칭 여백만큼 더 작다.
 * 2026-09-21 사용자 요청으로 최초 값(56dp)의 150%(84dp)로 키웠다가, 같은 날 다시 90%인
 * 75.6dp로 줄였다.
 */
private val MenuCardIconSize = 75.6.dp

/** 아이콘이 있을 때 카드 왼쪽 테두리와 아이콘 사이의 여백(2026-09-21 사용자 요청 — 극단적으로 좁힘). */
private val MenuCardIconStartPadding = 4.dp

/**
 * "대국 하기" 카드 아이콘 — 마지막으로 고른 AI 캐릭터, 없으면 기본값(레벨1, "문하생 판다")을
 * 돌려준다(백로그 #181). [PlayerSetup]의 두 좌석 중 AI가 맡은 쪽을 찾고, 그 쪽이 없거나
 * `FastBeginner` 그룹이 아니면(로컬 2인 대국·구 그룹 등) 레벨 1로 떨어진다 —
 * `PlayerSetupPanel.kt`의 캐릭터 픽커가 쓰는 것과 같은 산수다.
 */
internal fun currentAiCharacterOrDefault(playerSetup: PlayerSetup): BotCharacter {
    val aiPlayLevel = listOf(playerSetup.white, playerSetup.black)
        .firstOrNull { side -> side.controller == SeatController.Ai }
        ?.playLevel
    val fastBeginnerLevel = if (aiPlayLevel?.group == PlayLevelGroup.FastBeginner) {
        aiPlayLevel.safeLevel
    } else {
        1
    }
    return BotCharacterCatalog.forPlayLevel(
        PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = fastBeginnerLevel),
    ) ?: BotCharacterCatalog.fastBeginnerRoster.first()
}

/**
 * [currentAiCharacterOrDefault]가 고른 캐릭터를 정사각형 그대로 그린다. `BotCharacterAvatar`를
 * 재사용하지 않는 이유는 그쪽이 원형 클립·잠금 회색조용이기 때문이다 — 여기서는 항상
 * "보유·선택된" 캐릭터만 다루므로 그 상태들이 필요 없다. 원화가 이미 투명 배경이라(직접 확인)
 * 정사각형으로 그대로 둬도 잘린 티가 나지 않는다.
 */
@Composable
internal fun BotCharacterSquareIcon(character: BotCharacter) {
    val res = botAvatarRes(character) ?: return
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
}

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
 * 「보드 미리보기」(`GameSetupLobby.kt`)와 같은 `GoBoard`를 아이콘 크기로 그린다. `isGameEnded =
 * true`로 둬 끝난 대국다운 톤(무채색 쪽에 가까운 돌 렌더링)을 준다 — 실제로 끝난 판이다.
 */
@Composable
private fun GameHistoryBoardIcon() {
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
 * "학습 하기" 카드 아이콘 — 바둑판(초반 포석 세 점) 위에 돋보기를 판 한 변의 절반 크기로
 * 겹친다(백로그 #181, 사용자가 준 조합 스펙 그대로). 돋보기는 이모지 대신 `Icons.Filled.Search`
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
                .background(MaterialTheme.colorScheme.surface),
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

/** 판 한 변 대비 돋보기 배지의 비율(사용자 스펙: "판의 50% 사이즈"). */
private const val StudyPreviewMagnifierSizeFraction = 0.5f
