package com.worksoc.goaicoach.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId

/**
 * 끌어서 두는 동안의 가늠돌 상태(백로그 #138·#154).
 *
 * ⚠️ **확대창은 2026-09-22에 사라졌다**(백로그 #188) — 가늠돌과 끌어서 두기는 그대로다.
 * 그때 [below]가 뜻을 잃었고(놓을 말풍선이 없다) 남은 이유는 그 필드의 주석에 있다.
 */
internal data class PlayDrag(
    /**
     * **조준점** — 가늠돌이 놓일 자리다. 끌고 있을 때는 손가락보다 [PlayDragLiftCells]칸 **위**다(#154).
     */
    val touch: Offset,
    /**
     * ⚠️ **확대창이 사라진 뒤로 늘 `false`다**(백로그 #188) — 말풍선을 손가락 위/아래 어디에 놓을지
     * 정하던 값인데, 놓을 말풍선이 없어졌다. **지우지 않고 남긴 것은 [DragFollow]·[followDrag]와
     * 함께 이 자료형을 읽는 그물이 있기 때문**이고, 다음에 손댈 때 함께 걷어낼 것.
     */
    val below: Boolean = false,
    /**
     * **실제 손가락 자리.** ⚠️ **기본값이 [touch]인 것은 일부러다** — 띄움이 없던 자리(누르는 순간)는
     * 조준점과 손가락이 같다.
     */
    val finger: Offset = touch,
)

/**
 * 끌고 있을 때 가늠돌을 손가락보다 이만큼 **위로** 띄운다 — 단위는 **칸**(백로그 #154).
 *
 * ## ⚠️ dp가 아니라 칸인 이유
 * 같은 28dp가 판 크기에 따라 **0.45칸~1.94칸**으로 벌어진다(판 한 변 280dp[MinFittedBoardSide] ~ 562dp).
 * dp로 잡으면 촘촘한 판에서는 두 칸 가까이 건너뛰어 엉뚱해 보이고, 성긴 판에서는 손가락을 못 벗어난다.
 * 칸으로 잡으면 어느 판에서든 *"손끝 바로 한 줄 위"* 로 같게 읽힌다.
 *
 * ## ⚠️ 맨 아랫줄은 나빠진다 — 알고 받아들인 것이다 (2026-09-18 사용자)
 * *"맨 아랫줄의 우려가 있으나 우선 구현한다. 맨 아랫줄을 놓을 때는 이미 종국에 다다랐을 것이고,
 * 끌기 없이 착수하는 경험이 쌓일 것으로 보자."* — 맨 아랫줄에 두려면 손가락이 그 아래 여백에
 * 있어야 하는데 자리가 모자란다. **끌지 않고 그냥 탭하면 띄움이 없으므로 여전히 놓인다.**
 *
 * ## 1칸 → 1.5칸 (2026-09-22, 백로그 #189)
 * #188이 **확대 창을 없앴다.** 손가락에 가린 자리를 확대해 보여 주던 것이 사라졌으니, 가늠돌이
 * 손가락을 더 벗어나는 쪽으로 대신 메운다 — *"손가락에 가려 원하지 않는 자리에 두는 일"* 을
 * 막는 것이 둘의 공통 목적이다. **위 「맨 아랫줄」의 대가는 그만큼 커졌다** — 판 아래 여백이
 * 1.5칸을 못 받치므로 맨 아랫줄은 끌어서 두기로는 사실상 닿지 않는다고 보는 편이 맞고,
 * 탈출구는 여전히 **그냥 탭**이다.
 *
 * ⛔ 터치 **면적**(`touchMajor`)으로 띄움을 정하는 안은 일부러 안 골랐다(2026-09-22 사용자 결정
 * 「50%만 올린다」) — Compose가 터치 크기를 공개 API로 주지 않아 View 레벨 `MotionEvent`로
 * 내려가야 하고, 판의 입력 경로는 이 앱에서 가장 예민한 곳이다(함정 44·47).
 *
 * 재조정은 이 한 줄이다(#145 패턴).
 */
internal const val PlayDragLiftCells: Float = 1.5f

/** [followDrag]의 결과 — 가늠돌을 그릴 자리와, 이제 손가락을 따라가는 중인지. */
internal data class DragFollow(
    /** 가늠돌을 그릴 자리 — 따라가는 중이면 손가락보다 [PlayDragLiftCells]칸 위다(#154). */
    val target: Offset,
    val following: Boolean,
    /** 띄우기 **전**의 손가락 자리. 확대창이 이것을 쓴다. 띄움이 없으면 [target]과 같다. */
    val finger: Offset = target,
)

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
    liftPx: Float = 0f,
): DragFollow {
    val nowFollowing = following || (current - down).getDistance() > touchSlop
    // ⚠️ **띄움은 따라가는 중에만 건다**(#154). 슬롭 안의 떨림은 아직 탭이고, 탭까지 띄우면
    // 누른 곳과 다른 데 놓여 #39가 막으려던 바로 그 사고가 된다. 그래서 `down`은 그대로 돌려준다.
    if (!nowFollowing) return DragFollow(target = down, following = false, finger = down)
    return DragFollow(
        target = Offset(current.x, current.y - liftPx),
        following = true,
        finger = current,
    )
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
