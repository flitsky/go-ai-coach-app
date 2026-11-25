package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 프리미엄 모드 전용 시각 아이덴티티 상수. 대국 설정의 프리미엄 카드, 인게임 잠긴 버튼
 * 테두리 등 프리미엄과 관련된 색/보더/그라디언트는 하드코딩하지 않고 이 파일만 참조한다
 * — 톤을 바꿀 때 한 곳만 고치면 되도록.
 */
internal val PremiumGold = Color(0xFFCA9B3E)
internal val PremiumGoldLight = Color(0xFFF3E1AC)
internal val PremiumGoldDeep = Color(0xFF8A6416)

internal val PremiumCardShape = RoundedCornerShape(16.dp)

internal val PremiumGoldGradient =
    Brush.horizontalGradient(listOf(PremiumGoldDeep, PremiumGold, PremiumGoldLight))

/**
 * ## ⚠️ 금색은 **"프리미엄 기능이다"** 라는 뜻이다 — *"지금 못 쓴다"* 가 아니다 (2026-09-18)
 *
 * 이 규칙은 대국 **메뉴**가 먼저 정한 것이다(#149, 사용자 결정 ⓐ안): 프리미엄 옵션은 **라벨이
 * 항상 금색**이고, 못 쓸 때는 **스위치만 흐려진다.** 색과 잠김은 서로 다른 말을 하므로 겹쳐
 * 걸지 않는다.
 *
 * 그런데 인게임 버튼은 오랫동안 **반대로** 말하고 있었다 — 잠겼을 때만 금색이고 권한이 생기면
 * 금색이 **사라졌다.** 그래서 **구독을 사는 순간 버튼이 더 평범해졌다** — 낸 값이 화면에서
 * 사라지고, 해지했을 때도 *"왜 갑자기 금테가 생겼지"* 로 뒤늦게 알아채게 된다.
 *
 * 이제 두 화면이 같은 말을 한다:
 * - **금색이 있다** = 이 기능은 프리미엄 축이다(잠겼든 열렸든).
 * - **굵기/채움** = 지금 상태다. 잠김 [PremiumLockedBorder] 2dp · 열림 [PremiumUnlockedBorder] 1dp ·
 *   켜짐은 아예 채운다(테두리 없음).
 *
 * ⚠️ **1회권으로 열린 경우도 열림으로 본다.** 금색은 *"이 기능이 프리미엄 축이다"* 이지
 * *"네가 구독자다"* 가 아니다 — 구독자 신원 표시는 로비의 👑(`GameSetupLobby`)이 맡는다.
 */
internal val PremiumLockedBorder = BorderStroke(2.dp, PremiumGold.copy(alpha = 0.9f))

/**
 * 프리미엄 기능이 **지금 쓸 수 있는** 상태일 때의 금색 테두리.
 *
 * 잠김([PremiumLockedBorder])보다 **얇고 옅다** — 같은 금색이라 "프리미엄"은 그대로 읽히지만,
 * 굵기 차이로 "지금 쓸 수 있다"가 구별된다. 일반 버튼 테두리와 **같은 1dp**라 행의 무게도 안 흔든다.
 */
internal val PremiumUnlockedBorder = BorderStroke(1.dp, PremiumGold.copy(alpha = 0.55f))

/**
 * 광고/구독으로 운을 바꾼 「프리미엄 운세」의 시각 팔레트(1.8.7) — 앱 공식 [PremiumGold] 축과 종이 질감(`paperTexture`)을
 * 밝은 모드(금박 한지) · 야간 모드(옻칠 금박) 양쪽 모두 고대비로 맞춘다.
 * 모든 `Color(0x...)` 리터럴은 `ui/designsystem`에만 둔다(`DesignTokenLiteralRatchetTest`).
 */
@Immutable
internal data class BoostedFortunePalette(
    val paperBase: Color,
    val paperGrain: Color,
    val gradientColors: List<Color>,
    val borderColors: List<Color>,
    val accentText: Color,
    val badgeBg: Color,
    val badgeBorder: Color,
    val tipBg: Color,
    val tipBorder: Color,
    val shimmerColor: Color,
) {
    val backgroundBrush: Brush get() = Brush.linearGradient(gradientColors)
    val borderBrush: Brush get() = Brush.linearGradient(borderColors)
}

@Composable
internal fun boostedFortunePalette(): BoostedFortunePalette =
    if (LocalAppDarkTheme.current) DarkBoostedFortunePalette else LightBoostedFortunePalette

private val LightBoostedFortunePalette = BoostedFortunePalette(
    paperBase = Color(0xFFFFFBF0),
    paperGrain = Color(0x228A6416),
    gradientColors = listOf(
        Color(0xFFFFFDF6).copy(alpha = 0.88f),
        Color(0xFFFAF0D3).copy(alpha = 0.84f),
        Color(0xFFF3E1AC).copy(alpha = 0.78f),
    ),
    borderColors = listOf(
        PremiumGoldDeep.copy(alpha = 0.72f),
        PremiumGold,
        PremiumGoldLight,
        PremiumGold.copy(alpha = 0.85f),
    ),
    accentText = PremiumGoldDeep,
    badgeBg = PremiumGold.copy(alpha = 0.16f),
    badgeBorder = PremiumGold.copy(alpha = 0.45f),
    tipBg = Color.White.copy(alpha = 0.68f),
    tipBorder = PremiumGold.copy(alpha = 0.32f),
    shimmerColor = Color.White.copy(alpha = 0.55f),
)

private val DarkBoostedFortunePalette = BoostedFortunePalette(
    paperBase = Color(0xFF282217),
    paperGrain = Color(0x1AF3E1AC),
    gradientColors = listOf(
        Color(0xFF2B2418).copy(alpha = 0.90f),
        Color(0xFF3A2F1C).copy(alpha = 0.86f),
        Color(0xFF262016).copy(alpha = 0.92f),
    ),
    borderColors = listOf(
        PremiumGold.copy(alpha = 0.85f),
        PremiumGoldLight.copy(alpha = 0.90f),
        PremiumGold.copy(alpha = 0.65f),
    ),
    accentText = PremiumGoldLight,
    badgeBg = PremiumGold.copy(alpha = 0.22f),
    badgeBorder = PremiumGoldLight.copy(alpha = 0.42f),
    tipBg = Color(0xFF1A1610).copy(alpha = 0.58f),
    tipBorder = PremiumGold.copy(alpha = 0.35f),
    shimmerColor = PremiumGoldLight.copy(alpha = 0.28f),
)
