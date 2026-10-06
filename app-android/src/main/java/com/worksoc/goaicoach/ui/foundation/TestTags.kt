package com.worksoc.goaicoach.ui.foundation

import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.shared.domain.StoneColor

/**
 * Stable Compose test tags for instrumented UI tests. Kept separate from
 * production strings so locating these nodes in tests doesn't depend on
 * locale-specific copy.
 */
internal object TestTags {
    const val GoBoard = "go_board"

    /**
     * Black/White each render both a Human and an AI pill with the same label
     * ("유저"/"AI" etc.), and they end up as flat semantics siblings (Row/Column
     * without their own semantics don't nest in the tree) -- so text alone can't
     * tell the two colors' pills apart. This tag can.
     */
    fun seatControllerPill(color: StoneColor, controller: SeatController): String =
        "seat_controller_${color.name}_${controller.name}"

    /** 좌석 아래의 급수 버튼(커스텀 대국, 백로그 #217) — 흑·백 좌석에 하나씩 있어 글자만으로는 못 가른다. */
    fun customRankSeatButton(color: StoneColor): String = "custom_rank_seat_${color.name}"
}
