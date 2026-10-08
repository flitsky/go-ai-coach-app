package com.worksoc.goaicoach.ui.board

/**
 * 판의 **선과 화점의 굵기**(backlog #222, 사용자 피드백 2026-10-08: 「선, 화점 굵게」).
 *
 * 예전에는 선이 **1.5픽셀 고정**이었다 — 화면 밀도와 무관해서 고해상도 폰일수록 가늘고(S23에서 0.5dp), 칸이 넓은 9줄에서는
 * 돌에 견주어 실처럼 보였다. 이제 **칸 간격에 비례**한다. 칸이 좁은 큰 판(19줄)에서는 비례만으로 너무 가늘어지므로 dp 하한을 둔다.
 * 테두리와 화점도 같은 잣대(칸 간격)에서 나온다 — 판 크기·화면 크기가 달라도 선·테두리·화점의 비가 같다.
 *
 * ⚠️ **dp 하한은 칸이 아주 좁은 판에서는 물러선다**([MaxGridLineShare] · [MaxStarPointRadiusShare]). 같은 그리기를 홈 메뉴 카드의
 * 작은 판(68dp)과 대국 설정의 미리보기 판도 쓴다 — 거기서 1dp 선·2.5dp 화점을 고집하면 칸의 1/7이 선이고 화점이 돌만 해진다.
 *
 * 값은 스레드가 정했다(사용자에게 알렸다 — 바꿀 때는 이 숫자들만 고친다). 실물 바둑판은 선이 칸의 약 4.5%, 화점 지름이 약 18%다.
 */
internal object BoardLineStyle {
    /** 격자선의 굵기 ÷ 칸 간격. */
    const val GridLineShare: Float = 0.035f

    /** 격자선의 굵기 하한(dp) — 19줄처럼 칸이 좁아도 이보다 가늘어지지 않는다. */
    const val MinGridLineDp: Float = 1f

    /** 그 하한이 넘지 못하는 선 — 칸 간격의 이만큼. 아주 작은 판(메뉴 카드의 그림)에서는 하한보다 이것이 먼저다. */
    const val MaxGridLineShare: Float = 0.08f

    /** 테두리의 굵기 ÷ 격자선의 굵기. */
    const val BorderToGridLine: Float = 2f

    /** 화점의 반지름 ÷ 칸 간격(예전 0.08). */
    const val StarPointRadiusShare: Float = 0.11f

    /** 화점의 반지름 하한(dp). */
    const val MinStarPointRadiusDp: Float = 2.5f

    /** 그 하한이 넘지 못하는 선 — 칸 간격의 이만큼(돌의 반지름은 칸의 절반 가까이다). */
    const val MaxStarPointRadiusShare: Float = 0.16f

    /** 칸 간격이 [spacingPx]인 판의 격자선 굵기(픽셀). [density]는 1dp의 픽셀 수다. */
    fun gridLineWidthPx(spacingPx: Float, density: Float): Float =
        maxOf(spacingPx * GridLineShare, minOf(MinGridLineDp * density, spacingPx * MaxGridLineShare))

    fun borderWidthPx(spacingPx: Float, density: Float): Float =
        gridLineWidthPx(spacingPx, density) * BorderToGridLine

    fun starPointRadiusPx(spacingPx: Float, density: Float): Float =
        maxOf(spacingPx * StarPointRadiusShare, minOf(MinStarPointRadiusDp * density, spacingPx * MaxStarPointRadiusShare))
}
