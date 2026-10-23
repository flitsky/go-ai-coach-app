package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

internal data class GoBoardColors(
    val boardBackgroundActive: Color,
    val boardBackgroundEnded: Color,
    val boardBorder: Color,
    val gridLine: Color,
    /** 판 가장자리의 좌표 글자(A~T · 1~19). 판 바탕 위에 바로 쓰이므로 바탕과 함께 바뀐다. */
    val coordinateLabel: Color,
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
            coordinateLabel = Color(0xFF4A2F17),
            lastMoveMark = Color(0xFFE53935),
            lastMoveNeutral = BrandSecondaryColor,
            engineActivityText = BrandPrimaryColor,
        )

        /**
         * **야간 모드**의 판(사용자 2026-10-23 — *"대국 준비중일 때 바둑판이 어둡게 보이는데, 그 정도 색상이면"*).
         * 「새 대국을 위해 준비 중」 때 판에 덮이는 검정 35% 막(`GoBoard.kt`)을 [Default]의 바탕 · 테두리 · 선 · 좌표 글자에
         * **똑같이** 겹친 색이다 — 바탕만 어둡게 하면 선과 좌표가 묻힌다(선 대비 3.2 → 2.3). 그래서 [Default]를 고치면 여기도 같은 셈으로 고친다.
         * 돌 · 마지막 수 테두리는 돌 위에 그려지므로 그대로다(흑은 검고 백은 희어야 한다 — [GoBoardPalette]).
         * 「AI 생각 중」 글자만 따로 골랐다 — 브랜드 초록은 이 바탕에서 대비 1.1로 사라져, 연한 민트로 둔다(대비 3.25, 밝은 판의 초록은 2.55).
         */
        val Dark = GoBoardColors(
            boardBackgroundActive = Color(0xFF97814B),
            boardBackgroundEnded = Color(0xFF88692F),
            boardBorder = Color(0xFF4F3215),
            gridLine = Color(0x8C1A1207),
            coordinateLabel = Color(0xFF301F0F),
            lastMoveMark = Color(0xFFE53935),
            lastMoveNeutral = BrandSecondaryColor,
            engineActivityText = Color(0xFFD4F5EA),
        )

        /** 지금 화면 모드의 판 — 판을 그리는 쪽의 기본값이다(그리기 블록 안에서는 컴포지션 값을 읽을 수 없어 여기서 집는다). */
        val current: GoBoardColors
            @Composable @ReadOnlyComposable
            get() = if (LocalAppDarkTheme.current) Dark else Default
    }
}
