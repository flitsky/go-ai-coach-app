package com.worksoc.goaicoach.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId

/**
 * 착수 드래그 중 판 위에 그릴 것(#39 → #131 → #138).
 *
 * @property touch 손가락이 가리키는 자리. 가늠돌은 이 좌표에서 가장 가까운 교차점에 그린다.
 * @property below 확대창을 손가락 **아래**에 띄울지. [magnifier]가 `false`면 쓰이지 않는다.
 * @property magnifier 확대창을 그릴지. ⚠️ **가늠돌과 확대창은 다른 시점에 뜬다**(#138) —
 *   가늠돌은 **누르는 순간**, 확대창은 **꾹 누름 임계를 넘긴 뒤**(그리고 토글이 켜져 있을 때)만.
 *   모든 탭에 말풍선이 번쩍이면 안 되기 때문이다. 이 플래그가 없던 때는 "가늠돌이 있다"가 곧
 *   "확대창을 그린다"여서 가늠돌을 임계 앞으로 당길 수 없었다.
 */
internal data class PlayDrag(val touch: Offset, val below: Boolean, val magnifier: Boolean)

/** [followDrag]의 결과 — 가늠돌을 그릴 자리와, 이제 손가락을 따라가는 중인지. */
internal data class DragFollow(val target: Offset, val following: Boolean)

/**
 * 가늠돌이 손가락을 **언제부터** 따라갈지(#138).
 *
 * ## ⚠️ 왜 곧바로 따라가지 않는가 — 터치 슬롭
 * 빠른 탭이 이제 **뗀 자리**(가늠돌이 보이는 그 자리)에 놓인다. 그런데 손가락은 누르고 떼는 사이에
 * 몇 픽셀 흔들린다. 그 떨림까지 따라가면 교차점 경계 근처의 탭이 **옆 칸에 놓이는** 회귀가 된다 —
 * #39가 *"살짝 미끄러진 탭이 엉뚱한 곳에 놓인다"* 며 빠른 탭을 **누른 자리**에 묶어 둔 이유가 정확히
 * 그것이었다. 그 걱정을 이제 이 함수가 맡는다.
 *
 * 그래서 **슬롭 안의 움직임은 처음 누른 자리를 유지**하고, 한 번 슬롭을 넘으면 그때부터는 **다시
 * 가까워져도 계속 따라간다** — 되돌아온 손가락을 처음 자리로 튕기면 더 헷갈린다.
 * 슬롭은 플랫폼 값(`viewConfiguration.touchSlop`, 약 8dp)이라 칸 반쪽(교차점 사이 중간선)보다 훨씬
 * 작다 — 의도한 끌기는 곧바로 넘기 때문에 *"즉시 끌려와야 한다"* 는 요구와 부딪히지 않는다.
 * 정확히 슬롭만큼은 아직 떨림이다(초과여야 넘는다).
 */
internal fun followDrag(
    down: Offset,
    current: Offset,
    touchSlop: Float,
    following: Boolean,
): DragFollow {
    val nowFollowing = following || (current - down).getDistance() > touchSlop
    return DragFollow(target = if (nowFollowing) current else down, following = nowFollowing)
}

/**
 * 꾹 누름 임계 **전** 단계(#138) — 손가락을 따라가며 **이벤트를 판이 가진다.**
 *
 * ## ⚠️ 판이 항상 우선이다 (2026-09-11 사용자 결정)
 * 대국 화면은 `GoCoachContent`의 `verticalScroll` 안에 있다. 첫 구현은 여기서 이동을 **소비하지 않고**
 * 스크롤에 양보했는데, 실기에서 **0.4초 안의 빠른 세로 조정이 스크롤에 빼앗겨 취소**됐다 — 가로는
 * 스크롤이 가져가지 않아 따라왔으니, **방향에 따라 동작이 갈렸다.** 그 기기(Pixel 7 기본 글꼴)에서는
 * 화면이 스크롤되지도 않는데 가져갔다. 사용자 결정: *"판이 항상 우선하면서, 누르자마자 임시 돌이
 * 보이고, 즉시 터치 드래그하더라도 바로 끌려와야 합니다."*
 *
 * 그래서 이동을 **소비한다** — 조상(스크롤)은 소비된 변화를 보고 끼어들지 않는다.
 * ⚠️ **알고 받아들인 대가 둘**(백로그 함정 44):
 *   ⓐ 판 위에서 시작한 스와이프로는 대국 화면이 스크롤되지 않는다 — 화면이 넘치는 작은 기기·큰
 *      글꼴에서는 판 **밖**(위·아래 여백, 버튼 영역)에서 스크롤해야 한다.
 *   ⓑ 바로 착수 모드에서 판 위를 빠르게 튕기면 **튕김이 끝난 자리에 착수**된다(판 밖에서 떼면 취소).
 * ⚠️ **Final 단계의 소비 검사를 되살리지 말 것.** 첫 구현은 `waitForUpOrCancellation`처럼 조상의
 *   가로채기를 Final 단계에서 알아챘는데, 이제는 **우리가 소비하므로** 그 검사가 제 소비를 조상의
 *   가로채기로 오인해 **모든 끌기를 취소**한다.
 *
 * ⚠️ 판 **밖**으로 나가는 것은 취소가 아니다 — 계속 따라가고, 판 밖에서 떼면 좌표가 없어 조용히
 * 놓이지 않는다. 임계 이후 단계와 같은 취소 경로다(그래서 `waitForUpOrCancellation`을 쓰지 않는다 —
 * 그쪽은 경계 밖으로 나가는 순간 취소한다).
 *
 * @return `true` = 손을 뗐다(그 자리에 놓는다), `false` = 판보다 먼저 누가 가져갔다(아무것도 안 한다)
 */
internal suspend fun AwaitPointerEventScope.trackPressUntilUp(
    pointerId: PointerId,
    onMove: (Offset) -> Unit,
): Boolean {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return false
        if (!change.pressed) {
            change.consume()
            return true
        }
        // 판보다 **먼저**(Initial 단계에서) 누가 가져갔다면 양보한다 — 판 위에 겹친 무언가의 몫이다.
        if (change.isConsumed) return false
        change.consume()
        onMove(change.position)
    }
}
