package com.worksoc.goaicoach.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
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
