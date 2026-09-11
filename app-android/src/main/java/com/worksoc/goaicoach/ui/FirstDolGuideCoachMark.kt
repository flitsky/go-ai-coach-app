package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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

    fun boundsOf(target: GuideTarget): Rect? = spots[target]

    internal fun resetForTest() = spots.clear()
}

/**
 * 이 컨트롤의 자리를 코치마크에게 알려 준다. 컨트롤 쪽 비용은 **이 한 줄**이다.
 *
 * ⚠️ **화면을 떠날 때 지우지 않는다** — 한때 `forget()`을 두었는데 부르는 곳이 없어 걷어냈다.
 * 그래도 되는 이유: 코치마크는 **대국 화면 표면에서만** 그려지고, 그 화면에 들어오면 컨트롤들이
 * 컴포즈되며 **좌표를 곧바로 다시 보고**한다. 즉 옛 좌표가 쓰이는 창이 없다.
 * ⚠️ 그 전제가 깨지는 변경(대국 화면 밖에서 코치마크를 그리게 되는 것)을 하려면 **먼저 지우는
 * 경로를 만들 것** — 없으면 화면이 바뀐 뒤에도 옛 자리에 동그라미가 남는다.
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
 * ## ⚠️ 화면 어디를 눌러도 **다음 코치마크로** 넘어간다 (백로그 #140 — #128의 판단을 뒤집었다)
 *
 * #128은 *"전면을 덮으면 설명받은 그 버튼을 눌러 볼 수 없다"* 며 포인터를 잡지 않았다. 그런데 그
 * 틈으로 **판이 터치를 받아 착수가 됐다**(사용자 피드백: *"가이드가 떴을 때 바둑판을 누르면 착수가
 * 되어버림"*). 사용자 결정(2026-09-11): 가이드가 떠 있으면 **어디를 누르든 가이드를 넘기는 것으로**
 * 받고, 그 터치는 아래로 보내지 않는다. 넘기는 것은 **하나**다 — 전체 종료가 아니다.
 * - 흡수 층은 이 오버레이의 **첫 자식**(= 맨 아래)이다. 말풍선의 `알겠어요`는 그 위에서 자기 터치를
 *   받는다. ⚠️ 층을 **부모**에 걸지 말 것 — 조상과 자식은 한 터치를 함께 받으므로 버튼과 층이 **둘 다**
 *   넘겨 코치마크 하나를 건너뛴다. 말풍선의 글자·배경에는 포인터 입력이 없어 그 터치는 층이 받는다.
 * - 판은 이 오버레이의 **아래 형제**다(`GoCoachContent`가 앵커를 `Column` 뒤에 둔다). Compose는 겹친
 *   형제 중 **위의 것이 받으면 아래로 보내지 않는다.** ⚠️ #138 이후 판은 누르는 순간 가늠돌을 띄우므로
 *   (함정 44) 층이 판보다 아래에 놓이면 판이 먼저 가져간다.
 * - **떼는 순간** 넘긴다 — 누르는 순간 넘기면 손가락이 닿은 채 다음 코치마크가 뜬다.
 * - 배경은 여전히 칠하지 않는다(스크림 없음) — 요청은 "터치를 막아라"였지 "가려라"가 아니었다.
 */
@Composable
internal fun GuideCoachMark(
    target: GuideTarget,
    text: String,
    onNext: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val density = LocalDensity.current
    var overlay by remember { mutableStateOf(Rect.Zero) }
    // ⚠️ 흡수 층의 `pointerInput(Unit)`은 **처음 붙잡은 람다**로 계속 돈다 — 다음 코치마크로 넘어가도
    //   이 자리는 그대로라 재시작되지 않는다. 그래서 늘 **최신** `onNext`를 부르게 한다.
    // ⚠️ **`rememberUpdatedState`를 쓰면 안 된다 — 실기에서 그렇게 멈췄다.** 그건 **구조적 동등성**으로
    //   갱신을 거르는데, 호출부가 넘기는 `::ack`(지역 함수 참조)는 컴포지션마다 **새 클로저**(그때의
    //   단계를 쥔)이면서 **서로 `==`** 다. 그래서 갱신이 걸러져 첫 코치마크의 `ack`가 계속 불렸고,
    //   둘째부터는 판을 눌러도 이미 본 첫 단계만 다시 기록하며 넘어가지 않았다(`알겠어요`는 멀쩡했다 —
    //   `clickable`은 `onClick`을 **참조로** 비교한다). 그래서 **참조 동등성**으로 들고 있는다.
    val latestOnNext = remember { mutableStateOf(onNext, referentialEqualityPolicy()) }
    latestOnNext.value = onNext
    val bounds = GuideTargetSpots.boundsOf(target) ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlay = it.boundsInRoot() },
    ) {
        // 흡수 층 — **맨 먼저**(= 맨 아래) 둔다. 사유는 위 KDoc.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        latestOnNext.value()
                    }
                },
        )
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
