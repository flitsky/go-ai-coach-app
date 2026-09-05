package com.worksoc.goaicoach.application.preferences

/**
 * 착수 돋보기의 **창 크기**와 **확대 배율** 설정(백로그 #85, 2026-09-05 사용자 발주).
 *
 * ## 왜 둘을 나눴는가 — 서로 다른 불편을 고친다
 * 실기 피드백은 *"너무 좁은 영역만 보여줘서 돋보기로 실제 착수를 하기 어렵다"* 였다. 그 원인이
 * 둘이고 **방향이 반대**다.
 * · **창이 작다** → 보이는 판이 좁다. [MagnifierSizeScales]가 창을 키운다.
 * · **너무 확대한다** → 같은 창에 더 적은 칸이 들어간다. [MagnifierZoomScales]가 배율을 낮춘다.
 *
 * 즉 *"보이는 칸 수"* 는 **창 크기 ÷ 배율**이다. 한쪽만 만지면 다른 쪽이 상쇄해 버리므로
 * 두 손잡이를 따로 준다.
 *
 * ## ⚠️ 배율 1.0은 "돋보기를 끈 것"이 아니다
 * [MagnifierZoomScales]의 첫 값 `1.0`은 *"판과 같은 크기로 보여 준다"* 는 뜻이다. 확대는 없어도
 * **손가락이 가린 자리를 손가락 위에 띄워 보여 주는 것** 자체가 값이라(#39가 이 기능을 만든
 * 이유가 오착수 방지다) 여전히 쓸모가 있다. 돋보기를 아예 끄는 것은 별개 토글이다
 * (`isPlayMagnifierEnabled`).
 *
 * ## ⚠️ 기본값은 "지금보다 넓게 보이는 쪽"이다
 * 창 **1.2배** + 배율 **1.5배**. 이전 동작은 창 1.0배 + 배율 2.0배였으므로, 보이는 칸 수가
 * `1.2 / 1.5 ÷ (1.0 / 2.0)` = **1.6배**로 늘어난다. 사용자 지시(*"영역을 1.2배로 키우고 확대
 * 비율은 조금 낮게"*)를 그대로 옮긴 값이다.
 *
 * ⚠️ **값을 더하거나 뺄 때는 `sanitize`가 옛 저장분을 접어 준다는 것을 전제로 할 것** —
 * 목록에서 값을 빼면 그 값을 골라 둔 기기는 기본값으로 돌아온다(#83이 글꼴 배율에서 밟은 경로).
 */
object MagnifierSettings {

    /** 돋보기 창 크기 배수. `1.0`이 #39 당시의 크기다. */
    val sizeScales: List<Float> = listOf(1.0f, 1.2f)

    /** 확대 배율. `1.0`은 판과 같은 크기(확대 없음)이고, 그래도 손가락 가림은 해소된다. */
    val zoomScales: List<Float> = listOf(1.0f, 1.2f, 1.5f)

    const val defaultSizeScale: Float = 1.2f
    const val defaultZoom: Float = 1.5f

    fun sanitizeSizeScale(stored: Float): Float =
        if (sizeScales.any { scale -> scale == stored }) stored else defaultSizeScale

    fun sanitizeZoom(stored: Float): Float =
        if (zoomScales.any { scale -> scale == stored }) stored else defaultZoom
}
