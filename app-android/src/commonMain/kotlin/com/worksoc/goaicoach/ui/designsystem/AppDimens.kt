package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 치수 토큰(refactor backlog #51). ⚠️ **값에 이름을 붙였을 뿐 값을 고르지 않았다** — 화면 파일에
 * 흩어져 있던 dp/sp 리터럴 중 **같은 뜻으로 3번 이상** 나오던 값만 그대로 옮겼다(전후 스크린샷이
 * 픽셀까지 같다: `DesignTokenScreenshotTest`). 그래서 이름이 `Space12`처럼 **값을 그대로 말한다** —
 * 의미 이름(Small/Medium…)은 간격 체계를 실제로 정리할 때 붙일 것이고, 지금 붙이면 12와 14 중 무엇이
 * "Medium"인지를 이 추출이 **결정해 버린다.**
 *
 * 뜻이 같다는 판정은 **쓰인 자리**로 했다:
 * - [AppSpacing] — `padding(…)`·`PaddingValues(…)`·`Arrangement.spacedBy(…)`·`Spacer`의 `height/width`.
 * - [AppRadius] — `RoundedCornerShape(…)`.
 * - [AppBorderWidth] — `border(…)`·`BorderStroke(…)`의 굵기.
 * - [AppElevation] — `tonalElevation`·`shadowElevation`.
 * - [AppTextSize] — `fontSize = …`.
 *
 * 한두 번뿐인 값·크기(아이콘·카드 높이 등)·그림 좌표는 **일부러 리터럴로 남겼다** — 한 곳만 쓰는 값에
 * 이름을 붙이면 찾아가야 할 곳만 늘어난다. 이미 파일 안에 이름이 있는 값(`GameScreenEdgePadding` 등)도 그대로다.
 */

internal object AppSpacing {
    val Space2 = 2.dp
    val Space3 = 3.dp
    val Space4 = 4.dp
    val Space5 = 5.dp
    val Space6 = 6.dp
    val Space8 = 8.dp
    val Space10 = 10.dp
    val Space12 = 12.dp
    val Space14 = 14.dp
    val Space16 = 16.dp
    val Space18 = 18.dp
    val Space20 = 20.dp
    val Space24 = 24.dp
}

internal object AppRadius {
    val Corner6 = 6.dp
    val Corner8 = 8.dp
    val Corner10 = 10.dp
    val Corner12 = 12.dp
    val Corner14 = 14.dp
    val Corner16 = 16.dp
    val Corner18 = 18.dp
    val Corner24 = 24.dp
}

internal object AppBorderWidth {
    /** 일반 테두리. */
    val Hairline = 1.dp

    /** 선택·강조 테두리. */
    val Emphasis = 1.5.dp

    val Strong = 2.dp
}

/** Material 3의 표면 높이 단계(Level0=0dp, Level1=1dp, Level2=3dp)와 같은 값이다. */
internal object AppElevation {
    val Level0 = 0.dp
    val Level1 = 1.dp
    val Level2 = 3.dp
}

internal object AppTextSize {
    val Text12 = 12.sp
    val Text13 = 13.sp
    val Text14 = 14.sp
    val Text15 = 15.sp
    val Text16 = 16.sp
    val Text18 = 18.sp
    val Text20 = 20.sp
}
