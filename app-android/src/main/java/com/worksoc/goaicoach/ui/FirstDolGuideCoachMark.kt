package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.guide.GuideTarget
import kotlin.math.roundToInt

/**
 * 대국 화면 코치마크(백로그 #128 ⑤, 2026-09-09 사용자 지시로 한 장 → **버튼별 넷**).
 *
 * 요구는 셋이었다: **버튼마다 쪼개고**, **버튼을 가리지 않게 그 옆에서** 말하고, 그 버튼에
 * **손으로 그린 듯한 동그라미**를 쳐서 눈에 띄게.
 *
 * ## ⚠️ 그래서 좌표를 화면 밖으로 흘린다 — ③에서 기각했던 그 방법이다
 *
 * ③ 말풍선은 *"카드 우측 상단"* 이라는 고정된 자리라 좌표 없이 풀 수 있었고, 설계 심사도
 * `onGloballyPositioned`를 *"얻는 것이 없다"* 며 기각했다. 그런데 *"그 버튼에 동그라미"* 는
 * **대상의 실제 자리를 알아야만** 성립한다 — 요구가 달라졌으므로 판정도 달라진다.
 * ⚠️ 이 저장소에 `onGloballyPositioned` 선례는 이것이 처음이다(그전까지 0건).
 *
 * ## 좌표계
 *
 * 컨트롤은 [GuideTargetSpots.report]에 **루트 기준**(`boundsInRoot`) 사각형을 넘긴다. 오버레이도
 * 자기 루트 원점을 재서 빼므로, 중간에 스크롤·패딩이 몇 겹이든 좌표가 어긋나지 않는다.
 * ⚠️ **루트 기준이 아닌 좌표를 넘기지 말 것** — 판 위 토글은 스크롤 안에 있고 버튼 둘은 그 밖에
 * 있어서, 부모 기준으로 재면 둘이 서로 다른 기준을 갖는다.
 */
internal object GuideTargetSpots {
    private val spots = mutableStateMapOf<GuideTarget, Rect>()

    fun report(target: GuideTarget, bounds: Rect) {
        spots[target] = bounds
    }

    fun forget(target: GuideTarget) {
        spots.remove(target)
    }

    fun boundsOf(target: GuideTarget): Rect? = spots[target]

    internal fun resetForTest() = spots.clear()
}

/**
 * 이 컨트롤의 자리를 코치마크에게 알려 준다. 컨트롤 쪽 비용은 **이 한 줄**이다.
 *
 * ⚠️ 화면을 떠날 때 지운다 — 남겨 두면 다음 대국에서 **옛 좌표에 동그라미**가 그려진다.
 */
@Composable
internal fun Modifier.guideTarget(target: GuideTarget): Modifier {
    val current = this
    return current.onGloballyPositioned { coordinates ->
        GuideTargetSpots.report(target, coordinates.boundsInRoot())
    }
}

/**
 * 대상 버튼에 **동그라미**를 치고 그 옆에서 첫돌이가 한 마디 한다.
 *
 * ## ⚠️ 버튼을 가리지 않는다
 *
 * 말풍선은 대상이 화면 **아래쪽**에 있으면 위에, 위쪽에 있으면 아래에 붙인다. 동그라미는 대상보다
 * 조금 크게 둘러 **테두리만** 그리므로 버튼의 글자를 덮지 않는다.
 *
 * ## ⚠️ 스크림을 두지 않는다
 *
 * 전면을 덮으면 사용자가 **설명받은 그 버튼을 눌러 볼 수 없다.** 이 오버레이는 배경을 칠하지 않고
 * 포인터도 잡지 않는다 — 누를 것은 말풍선 안의 두 글자뿐이다(#125 스플래시가 터치를 일부러
 * 먹는 것과 정반대의 이유다).
 */
@Composable
internal fun GuideCoachMark(
    target: GuideTarget,
    text: String,
    onNext: () -> Unit,
    onStop: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val density = LocalDensity.current
    var overlay by remember { mutableStateOf(Rect.Zero) }
    val bounds = GuideTargetSpots.boundsOf(target) ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlay = it.boundsInRoot() },
    ) {
        val local = bounds.translate(-overlay.left, -overlay.top)
        val ringPadding = with(density) { 6.dp.toPx() }
        val ring = Rect(
            left = local.left - ringPadding,
            top = local.top - ringPadding,
            right = local.right + ringPadding,
            bottom = local.bottom + ringPadding,
        )
        val markerColor = MaterialTheme.colorScheme.primary

        // 손으로 그린 듯한 동그라미. ⚠️ **난수를 쓰지 않는다** — 매 프레임 흔들리면 손그림이 아니라
        // 지진이 된다. 고정된 흔들림 값으로 두 번 겹쳐 그려 "한 번 더 덧그린" 느낌만 만든다.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawPath(
                        path = sketchyEllipse(ring, wobble = 0.045f, sweepFrom = 0.10f),
                        color = markerColor,
                        style = Stroke(width = with(density) { 3.dp.toPx() }),
                    )
                    drawPath(
                        path = sketchyEllipse(ring, wobble = 0.075f, sweepFrom = 0.55f),
                        color = markerColor.copy(alpha = 0.55f),
                        style = Stroke(width = with(density) { 2.dp.toPx() }),
                    )
                },
        )

        // 말풍선을 대상의 **반대쪽**에 붙인다 — 대상이 화면 아래쪽이면 위에, 위쪽이면 아래에.
        // ⚠️ 오버레이 높이가 아직 0인 첫 프레임에는 위에 붙이는 쪽으로 둔다(판 위 토글이 화면
        //   중앙보다 위에 있어, 아래로 붙이면 반상을 덮는다).
        val putAbove = overlay.height > 0f && local.center.y > overlay.height / 2f
        // ⚠️ **자기 높이를 재서 올린다.** 처음에는 `layout(w, 0)`으로 보고하는 커스텀 `Layout`을
        // 썼는데, 실기에서 **배경과 버튼은 그려지고 글자만 안 그려졌다**(시맨틱 트리에는 정상
        // 좌표로 있었다). 원인을 좁히는 대신 선례가 있는 방식으로 바꿨다 — `GoBoard.kt`가 쓰는
        // `onSizeChanged` + `offset`이다. 첫 프레임만 10dp 어긋나고 다음 프레임에 자리를 잡는다.
        var bubbleHeight by remember { mutableIntStateOf(0) }
        val gapPx = with(density) { 10.dp.roundToPx() }
        Column(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = 0,
                        y = if (putAbove) {
                            (ring.top - gapPx).roundToInt() - bubbleHeight
                        } else {
                            (ring.bottom + gapPx).roundToInt()
                        },
                    )
                }
                .onSizeChanged { bubbleHeight = it.height }
                .padding(horizontal = 12.dp)
                .widthIn(max = 320.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                .border(1.dp, markerColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                FirstDolAvatar(size = 30.dp)
                Text(
                    text = text,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onStop) { Text(strings.guideStopAction, fontSize = 13.sp) }
                TextButton(onClick = onNext) { Text(strings.guideAckAction, fontSize = 13.sp) }
            }
        }
    }
}

/**
 * 타원을 **여덟 점 + 3차 곡선**으로 그리되 반지름에 고정된 흔들림을 준다 — 자를 안 대고 그린 선처럼.
 *
 * @param wobble 반지름을 흔드는 비율. 크면 더 삐뚤다.
 * @param sweepFrom 시작 각도(회전). 두 번 겹쳐 그릴 때 이것만 달리하면 겹친 선이 갈라져 보인다.
 */
private fun sketchyEllipse(rect: Rect, wobble: Float, sweepFrom: Float): Path {
    val cx = rect.center.x
    val cy = rect.center.y
    val rx = rect.width / 2f
    val ry = rect.height / 2f
    // 고정 흔들림 여덟 개. 난수를 쓰지 않는 이유는 위 KDoc.
    val jitter = floatArrayOf(1.03f, 0.96f, 1.05f, 0.98f, 1.02f, 0.94f, 1.06f, 0.99f)
    val points = (0 until 8).map { i ->
        val angle = ((i / 8f) + sweepFrom) * 2f * Math.PI.toFloat()
        val scale = 1f + (jitter[i] - 1f) * (wobble / 0.05f)
        Offset(
            x = cx + rx * scale * kotlin.math.cos(angle),
            y = cy + ry * scale * kotlin.math.sin(angle),
        )
    }
    return Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in points.indices) {
            val current = points[i]
            val next = points[(i + 1) % points.size]
            val afterNext = points[(i + 2) % points.size]
            val control1 = Offset(
                x = current.x + (next.x - points[(i - 1 + points.size) % points.size].x) / 5f,
                y = current.y + (next.y - points[(i - 1 + points.size) % points.size].y) / 5f,
            )
            val control2 = Offset(
                x = next.x - (afterNext.x - current.x) / 5f,
                y = next.y - (afterNext.y - current.y) / 5f,
            )
            cubicTo(control1.x, control1.y, control2.x, control2.y, next.x, next.y)
        }
        close()
    }
}
