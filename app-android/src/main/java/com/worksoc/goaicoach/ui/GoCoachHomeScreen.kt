package com.worksoc.goaicoach.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    var showOverwriteWarningDialog by remember { mutableStateOf(false) }

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
                GoStoneLogoBadge()

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = strings.appTitle,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
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
            )

            Spacer(modifier = Modifier.height(16.dp))

            // "대국 기록" (Game History) 카드
            MenuCard(
                title = strings.gameHistoryTitle,
                subtitle = strings.homeGameHistorySubtitle,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                subtitleColor = MaterialTheme.colorScheme.secondary,
                onClick = onGameHistoryClick,
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

@Composable
private fun GoStoneLogoBadge() {
    val stoneSizeDp = 51.dp
    // 광원 위치/반경을 실제 픽셀 기준으로 계산해, 하드코딩된 px 값이 스톤 크기와 어긋나
    // 그라데이션이 중앙의 작은 얼룩으로만 보이던 문제(특히 흑돌에서 두드러짐)를 없앤다.
    val stoneSizePx = with(LocalDensity.current) { stoneSizeDp.toPx() }
    val highlightCenter = Offset(stoneSizePx * 0.32f, stoneSizePx * 0.28f)
    val highlightRadius = stoneSizePx * 0.85f

    Box(
        modifier = Modifier
            .size(125.dp)
            .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
            .background(Color(0xFFF5F0E6), CircleShape)
            .border(1.dp, Color(0xFFE5DDD0), CircleShape),
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

@Composable
private fun MenuCard(
    title: String,
    subtitle: String,
    containerColor: Color,
    titleColor: Color,
    subtitleColor: Color,
    onClick: () -> Unit,
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
                .padding(24.dp),
            // 카드가 최소 높이일 때 내용을 세로 가운데에 둔다. 예전에는 `fillMaxSize` 자식
            // Column의 `Arrangement.Center`가 하던 일인데, 높이가 내용에 따라 달라진 지금은
            // 높이를 아는 쪽이 상자뿐이라 여기로 옮겼다.
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
