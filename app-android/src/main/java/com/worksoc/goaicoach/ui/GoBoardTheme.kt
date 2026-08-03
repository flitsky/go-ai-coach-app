package com.worksoc.goaicoach.ui

import androidx.compose.ui.graphics.Color

internal data class GoBoardColors(
    val boardBackgroundActive: Color,
    val boardBackgroundEnded: Color,
    val boardBorder: Color,
    val gridLine: Color,
    val lastMoveMark: Color,
    val lastMoveNeutral: Color,
    val engineActivityText: Color,
) {
    companion object {
        val Default = GoBoardColors(
            boardBackgroundActive = Color(0xFFE8C674),
            boardBackgroundEnded = Color(0xFFD2A248),
            boardBorder = Color(0xFF7A4D20),
            gridLine = Color(0x8C281C0A),
            lastMoveMark = Color(0xFFE53935),
            lastMoveNeutral = BrandSecondaryColor,
            engineActivityText = BrandPrimaryColor,
        )

        // 향후 추가 확장 가능한 모던/다크 테마 예시 토큰
        val Dark = GoBoardColors(
            boardBackgroundActive = Color(0xFF302C26),
            boardBackgroundEnded = Color(0xFF221F1A),
            boardBorder = Color(0xFF1A1713),
            gridLine = Color(0xFF554D42),
            lastMoveMark = Color(0xFFFF5252),
            lastMoveNeutral = Color(0xFF8A8478),
            engineActivityText = Color(0xFF35C9A6),
        )
    }
}
