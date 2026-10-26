package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// GoBoardTheme.kt의 기본 팔레트(engineActivityText/lastMoveNeutral)와 공유하는 브랜드 토큰 —
// 한 곳만 바꾸면 앱 전체 색상 스킴과 바둑판 색상이 함께 갱신된다.
internal val BrandPrimaryColor = Color(0xFF0E8C72)
internal val BrandSecondaryColor = Color(0xFF6B6459)

internal val AppLightColorScheme: ColorScheme = lightColorScheme(
    primary = BrandPrimaryColor,
    secondary = BrandSecondaryColor,
    background = Color(0xFFF6F1E7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0EAD9),
)

/**
 * **야간 모드**의 색(백로그 #272, 사용자 2026-10-20). 밝은 쪽의 크림 · 나무 결을 그대로 어둡게 — 차가운 회색이 아니라 **따뜻한 짙은 갈색**이다.
 * 브랜드 초록은 어두운 바탕에서 글자로도 읽히게 조금 밝혔고(`#0E8C72` → `#26A585`), 그 위 글자는 밝은 쪽처럼 흰색으로 둔다
 * (초록 버튼의 흰 글자 — `AppPalette.OnAccent`와 같은 모습).
 * 돌 · 캐릭터 그림 · 홈 로고 배지는 바꾸지 않는다(사용자 결정) — 그 색은 `GoBoardPalette` · `StonePalette` · `HomeLogoPalette`에 고정이다.
 * 바둑판은 한 단 어두운 판으로 바뀐다(사용자 2026-10-23 — 「준비 중」 때의 판 정도, `GoBoardColors.Dark`).
 * ⚠️ 밝은 바탕을 전제로 한 이름 붙은 색(형세 그래프 판 · 좌석 카드)은 [LocalAppDarkTheme]을 보고 어두운 짝을 고른다.
 */
internal val AppDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF26A585),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFB7AD9E),
    onSecondary = Color(0xFF1A1815),
    background = Color(0xFF1A1815),
    onBackground = Color(0xFFECE5D8),
    surface = Color(0xFF24211D),
    onSurface = Color(0xFFECE5D8),
    surfaceVariant = Color(0xFF302C26),
    onSurfaceVariant = Color(0xFFCFC5B6),
    outline = Color(0xFF7A7166),
    outlineVariant = Color(0xFF4A443C),
)

/**
 * 지금 야간 모드인가 — `MaterialTheme.colorScheme`의 역할로 표현되지 않는 고정색(그래프 판 · 좌석 카드)이 짝을 고를 때 본다.
 * 값은 앱의 맨 바깥 테마(`ui/settings/AppTheme.kt`)가 넣는다. 그 바깥에서 그리는 미리보기 · 계측 테스트는 밝은 쪽이다.
 */
internal val LocalAppDarkTheme = staticCompositionLocalOf { false }
