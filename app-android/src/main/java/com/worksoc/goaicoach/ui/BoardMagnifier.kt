package com.worksoc.goaicoach.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.worksoc.goaicoach.application.preferences.MagnifierSettings

/**
 * 돋보기 말풍선의 자리와 배율(백로그 #39). **순수 계산이라 테스트로 고정한다** — 이 항목에서
 * 실제로 틀리기 쉬운 곳은 그림이 아니라 이 배치다.
 */
internal data class MagnifierPlacement(
    /** 말풍선 중심(캔버스 좌표). */
    val center: Offset,
    val radius: Float,
    /** 확대 배율. */
    val scale: Float,
    /** 손가락 **아래**에 떴는가. 드래그 시작 때 한 번 정해져 그 드래그 동안 바뀌지 않는다. */
    val below: Boolean,
)

/**
 * 말풍선을 **손가락 아래**에 둬야 하는가. 위쪽에 원이 온전히 들어가지 않으면 참이다.
 *
 * ⚠️ **드래그가 시작될 때 한 번만 묻고 그 드래그 동안 고정한다.** 매 프레임 다시 물으면
 * 손가락이 경계선을 지날 때 말풍선이 위아래로 **뒤집히며 튄다** — 조준 중에 창이 반대편으로
 * 순간이동하는 것이 이 연출에서 가장 나쁜 손맛이다(#39 착수 중 계산으로 확인).
 *
 * 19줄 판에서 그 경계는 대략 판 높이의 **위쪽 40%** 다. 즉 상변 근처를 누르면 아래로 뜬다.
 */
internal fun magnifierPrefersBelow(
    touch: Offset,
    canvasSize: Size,
    cellSpacing: Float,
    fingerGapPx: Float,
    sizeScale: Float = MagnifierSettings.defaultSizeScale,
    zoom: Float = MagnifierSettings.defaultZoom,
): Boolean {
    val radius = magnifierRadius(canvasSize, cellSpacing, sizeScale, zoom)
    return touch.y - fingerGapPx - radius - radius < 0f
}

/**
 * 말풍선의 자리와 배율(2026-08-31 사용자 결정 ⓐ — 손가락 위 말풍선).
 *
 * ⚠️ **배율과 창 크기는 이제 사용자 설정이다**(백로그 #85) — `MagnifierSettings`가 값 목록을
 * 갖고, 여기서는 받은 값을 그대로 쓴다. 기본은 창 1.2배 · 배율 1.5배로, #39 당시(창 1.0 · 배율
 * 2.0)보다 **보이는 칸 수가 1.6배**다. 실기 피드백이 *"너무 좁은 영역만 보여 준다"* 였다.
 *
 * ⚠️ 그래도 **칸 수를 지시대로 고정하지는 않는다.** "5×5 셀"을 글자대로 지키면 작은 판에서 창이
 * 판의 절반을 넘는다 — 9줄 판은 칸이 이미 크기 때문이다. 지름 상한이 그것을 막고, 그래서
 * 실제로 보이는 칸 수는 판 크기에 따라 달라진다.
 *
 * [below]는 [magnifierPrefersBelow]가 드래그 시작 때 정한 값을 그대로 받는다 — 여기서 다시
 * 판단하지 않는 것이 요점이다.
 */
internal fun magnifierPlacement(
    touch: Offset,
    canvasSize: Size,
    cellSpacing: Float,
    fingerGapPx: Float,
    below: Boolean,
    sizeScale: Float = MagnifierSettings.defaultSizeScale,
    zoom: Float = MagnifierSettings.defaultZoom,
): MagnifierPlacement {
    val radius = magnifierRadius(canvasSize, cellSpacing, sizeScale, zoom)
    val rawCenterY = if (below) {
        touch.y + fingerGapPx + radius
    } else {
        touch.y - fingerGapPx - radius
    }
    return MagnifierPlacement(
        center = Offset(
            // 좌우로 캔버스를 넘지 않게 붙인다 — 넘으면 원이 잘려 반쪽만 보인다.
            x = touch.x.coerceIn(radius, maxOf(radius, canvasSize.width - radius)),
            // ⚠️ 위아래 어디에도 온전히 안 들어가는 캔버스(돋보기보다 작은 판)에서는 잘리는 것보다
            // 손가락에 겹치는 편이 낫다 — 그래서 마지막에 한 번 더 가둔다.
            y = rawCenterY.coerceIn(radius, maxOf(radius, canvasSize.height - radius)),
        ),
        radius = radius,
        scale = zoom,
        below = below,
    )
}

/**
 * 말풍선 반지름. **[sizeScale]이 창을, [zoom]이 확대를 따로 정한다**(백로그 #85).
 *
 * ⚠️ **두 값이 보이는 칸 수에 반대로 작용한다** — 보이는 칸 수는 대략 `지름 / (칸 간격 × 배율)`
 * 이므로, 창을 키우면 늘고 배율을 올리면 준다. 사용자가 *"영역은 키우고 배율은 낮춰라"* 라고
 * 한 것이 정확히 이 둘을 같은 방향으로 미는 조합이다.
 *
 * ⚠️ 상한([MagnifierMaxDiameterRatio])에도 [sizeScale]을 곱한다 — 19줄 판에서는 **상한 쪽이
 * 걸리기 때문에**(칸이 작아 첫 항이 훨씬 크다) 상한을 키우지 않으면 창이 전혀 커지지 않는다.
 * 처음에 첫 항에만 곱했다가 19줄에서 아무 변화가 없는 것을 계산으로 확인하고 고쳤다.
 */
private fun magnifierRadius(
    canvasSize: Size,
    cellSpacing: Float,
    sizeScale: Float,
    zoom: Float,
): Float {
    val diameter = minOf(
        MagnifierVisibleCells * cellSpacing * zoom * sizeScale,
        canvasSize.minDimension * MagnifierMaxDiameterRatio * sizeScale,
    )
    return diameter / 2f
}

/**
 * [content]를 [placement] 원 안에 [MagnifierPlacement.scale]배로 확대해 그린다.
 *
 * ⚠️ **변환 선언 순서가 뜻을 정한다.** `translate`를 먼저 선언하고 `scale`을 나중에 선언하면
 * 내용에는 `scale`이 먼저 적용된다(선언 역순) — 즉 판 위의 점 `p`가
 * `center + scale × (p - touch)`로 옮겨진다. 두 줄을 뒤집으면 확대 중심이 어긋난다.
 *
 * [content]는 호출부가 넘긴다 — 판을 그리는 함수들이 `GoBoard.kt`의 `private`이라 여기서 부를 수
 * 없고, 그래도 **같은 함수를 재사용하는 것**이 이 연출의 요점이다(그림을 두 벌 만들면 어긋난다).
 */
internal fun DrawScope.drawMagnifier(
    placement: MagnifierPlacement,
    touch: Offset,
    background: Color,
    border: Color,
    content: DrawScope.() -> Unit,
) {
    // ⚠️ **그림자와 밝힘이 없으면 렌즈로 안 읽힌다.** 처음에는 원 하나에 얇은 테두리만 그렸는데,
    // 배경색이 판과 같고 격자가 이어져 **"판에 원을 그려 놓은 것"** 으로 보였다(실기 확인).
    // 확대됐다는 유일한 단서가 격자 간격뿐이라 눈에 들어오지 않는다. 그래서 셋을 더한다 —
    // 아래 그림자로 띄우고, 안쪽을 살짝 밝혀 판과 구분하고, 테두리를 두 겹으로 둘러 어떤
    // 판 색에서도 윤곽이 살게 한다.
    drawCircle(
        color = Color.Black.copy(alpha = MagnifierShadowAlpha),
        radius = placement.radius + MagnifierShadowSpread,
        center = Offset(placement.center.x, placement.center.y + MagnifierShadowOffsetY),
    )

    val circle = Path().apply {
        addOval(Rect(center = placement.center, radius = placement.radius))
    }
    clipPath(circle) {
        // 판 바깥이 원에 들어올 수 있다(귀·변을 누를 때) — 배경을 깔아 뒤가 비치지 않게 한다.
        drawCircle(color = background, radius = placement.radius, center = placement.center)
        withTransform({
            translate(placement.center.x - touch.x, placement.center.y - touch.y)
            scale(placement.scale, placement.scale, pivot = touch)
        }) {
            content()
        }
        // 렌즈 안쪽만 아주 살짝 밝힌다 — 확대된 영역의 경계가 색으로도 읽히게 한다.
        drawCircle(
            color = Color.White.copy(alpha = MagnifierLensTintAlpha),
            radius = placement.radius,
            center = placement.center,
        )
    }
    drawCircle(
        color = Color.White.copy(alpha = MagnifierOuterRingAlpha),
        radius = placement.radius,
        center = placement.center,
        style = Stroke(width = MagnifierOuterRingWidth),
    )
    drawCircle(
        color = border,
        radius = placement.radius - MagnifierOuterRingWidth / 2f,
        center = placement.center,
        style = Stroke(width = MagnifierInnerRingWidth),
    )
}

/** 지시받은 시야("5×5 셀"). 큰 판에서는 그대로, 작은 판에서는 지름 상한에 눌려 줄어든다. */
private const val MagnifierVisibleCells = 5

/**
 * 말풍선 지름 상한 — 판 최소변 대비. 크게 잡으면 판을 가리고, **위쪽에 들어갈 자리가 없어져
 * 아래로 뒤집히는 영역이 넓어진다**(0.42로 잡았을 때는 판 한가운데서도 뒤집혔다).
 */
private const val MagnifierMaxDiameterRatio = 0.32f

/** 렌즈를 판 위로 띄우는 그림자. */
private const val MagnifierShadowAlpha = 0.22f
private const val MagnifierShadowSpread = 4f
private const val MagnifierShadowOffsetY = 6f

/** 렌즈 안쪽을 밝히는 정도. 아주 옅게 — 돌 색과 격자를 흐리게 하면 조준에 방해가 된다. */
private const val MagnifierLensTintAlpha = 0.07f

private const val MagnifierOuterRingAlpha = 0.85f
private const val MagnifierOuterRingWidth = 6f
private const val MagnifierInnerRingWidth = 2.5f
