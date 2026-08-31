package com.worksoc.goaicoach.application.attendance

import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.consumable.ConsumableInventory

/** 도장판 한 칸의 상태(백로그 #55). */
enum class AttendanceCellState {
    /** 출석했고 보상도 받아 갔다 — 도장이 찍힌 칸. */
    Stamped,

    /** 출석은 했는데 아직 안 받았다 — 지금 Claim하면 들어온다. */
    Claimable,

    /** 아직 그 회차에 닿지 않았다. */
    Upcoming,
}

data class AttendanceBoardCell(
    val tier: Int,
    val state: AttendanceCellState,
    val rewards: List<AttendanceReward>,
)

/**
 * 출석 보상 도장판(백로그 #55). **10칸이 전부다** — 1~6일차 여섯 칸과 7·14·21·28일차 네 칸.
 *
 * ⚠️ **28일차가 정책의 끝은 아니다.** `isRewardedTier`는 35·42…로 계속 이어지므로, 28을 넘긴
 * 사용자는 열 칸이 모두 찍힌 상태가 되고 이후 보상은 [beyondBoard]로 빠진다. 화면은 그 사실을
 * "이후 7일마다 반복"으로 알리고, 실제 받을 것은 상세 영역이 보여준다 — 칸을 무한히 늘리는
 * 대신 **판은 고정하고 넘치는 회차를 따로 들고 있는** 선택이다.
 */
data class AttendanceBoard(
    /** 한 행에 여섯 칸. */
    val daily: List<AttendanceBoardCell>,
    /** 위 여섯 칸과 같은 너비를 네 칸이 나눠 쓴다(주 단위라 더 크고 넓게). */
    val weekly: List<AttendanceBoardCell>,
    /** 판 밖(29일차 이후)에서 아직 받지 않은 회차들. */
    val beyondBoard: List<AttendanceRewardTier>,
) {
    val cells: List<AttendanceBoardCell> get() = daily + weekly

    /** 지금 Claim하면 들어올 회차가 하나라도 있는가. */
    val hasClaimable: Boolean
        get() = cells.any { it.state == AttendanceCellState.Claimable } || beyondBoard.isNotEmpty()
}

/** 판에 그려지는 회차. 1~6일차와 주 단위 네 회차. */
val AttendanceBoardDailyTiers: List<Int> = (1..6).toList()
val AttendanceBoardWeeklyTiers: List<Int> = listOf(7, 14, 21, 28)

/**
 * 저장된 출석 상태에서 도장판을 만든다. **순수 함수다** — 지급은 하지 않는다.
 *
 * [collection]은 이미 다 모은 캐릭터의 조각 줄을 걸러내는 데 쓴다(기존 `pendingTiers`와 같은 이유).
 */
fun buildAttendanceBoard(
    state: AttendanceState,
    collection: BotCollectionState = BotCollectionState(),
): AttendanceBoard {
    fun cell(tier: Int) = AttendanceBoardCell(
        tier = tier,
        state = when {
            state.isTierClaimed(tier) -> AttendanceCellState.Stamped
            tier <= state.attendanceCount -> AttendanceCellState.Claimable
            else -> AttendanceCellState.Upcoming
        },
        rewards = AttendanceRewardPolicy.rewardsFor(tier),
    )

    val onBoard = (AttendanceBoardDailyTiers + AttendanceBoardWeeklyTiers).toSet()
    return AttendanceBoard(
        daily = AttendanceBoardDailyTiers.map(::cell),
        weekly = AttendanceBoardWeeklyTiers.map(::cell),
        beyondBoard = AttendanceRewardPolicy.pendingTiers(state, collection)
            .filterNot { it.tier in onBoard },
    )
}

/**
 * 이 보상이 **실제로 몇 개 들어가는가**. 소모품이 아니면 `null`.
 *
 * ⚠️ **이것이 없으면 팝업이 거짓말을 한다.** `withGranted`가 상한을 넘는 만큼 조용히 버리므로,
 * 광고 스킵권이 9개인 사용자는 *"광고 스킵권 3개"* 라고 안내받고 **0개를 받는다.** 상한이 99일
 * 때는 거의 드러나지 않았지만, 스킵권 상한이 9로 낮아지면서(#55) 4일차 3 + 14일차 3 + 21일차 3
 * = 정확히 9라 **35일차부터 매 반복마다** 벌어진다.
 */
fun grantedAmountOf(reward: AttendanceReward, inventory: ConsumableInventory): Int? =
    (reward as? AttendanceReward.Consumable)?.let { consumable ->
        inventory.grantableAmount(consumable.item.id, consumable.amount)
    }
