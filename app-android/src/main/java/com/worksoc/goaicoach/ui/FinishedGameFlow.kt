package com.worksoc.goaicoach.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 끝난 판의 흐름이 **화면 밖에서** 들고 있어야 하는 것들(백로그 #185).
 *
 * 하나는 「복기 하기」 요청 — **방금 끝난 판을 다시보기로 곧장 열어 달라**는 신호.
 *
 * ## ⚠️ 왜 셸의 상태가 아니라 모듈 내 object인가
 * 요청을 만드는 곳은 **대국 화면**이고 받는 곳은 **대국 기록 화면**인데, 그 둘 사이에
 * `GoCoachApp`이 있다. 그 셸의 **상태 훅 예산은 42/42로 여유가 0**이라(함정 3,
 * `LayeringContractTest`) `var pendingReview by remember`를 하나 두는 순간 그물이 깨진다.
 * `AttendanceClaimReplaySignal`·`AppFontScaleState`가 같은 처방으로 먼저 간 길이다.
 *
 * ## ⚠️ 왜 기록 id가 아니라 수순 개수인가
 * 대국 화면은 **자기 기록의 id를 모른다.** 기록은 셸의 효과(`runGameHistoryAppendIfCompleted`)가
 * 붙이고 id를 돌려주지 않는다. 그래서 *"가장 최근 기록을 열어라"* 로 요청하되, 그것이 정말
 * 그 판인지 **수순 개수로 확인**한다.
 *
 * ⚠️ **확인 없이 최신 기록을 열면 엉뚱한 판이 열린다.** 기록 붙이기가 실패했거나 아직 안
 * 돌았으면 최신 기록은 **직전 대국**이고, 화면은 아무 말 없이 그것을 보여 준다 — 사용자는
 * 자기 판을 보고 있다고 믿는다. 빈 화면보다 나쁘다.
 */
internal object FinishedGameFlow {

    /** 열어 달라고 요청된 판의 수순 개수. `null`이면 요청 없음. */
    var pendingMoveCount by mutableStateOf<Int?>(null)
        private set

    fun request(moveCount: Int) {
        pendingMoveCount = moveCount
    }

    /**
     * 요청을 **한 번만** 꺼낸다 — 꺼내는 즉시 비운다.
     *
     * ⚠️ 비우지 않으면 대국 기록 화면에 다시 들어올 때마다 그 판이 또 열린다(목록을 못 본다).
     */
    fun take(): Int? = pendingMoveCount.also { pendingMoveCount = null }

    /**
     * 이미 닫은 계가 팝업의 열쇠(`FinalScoreJudgement.dialogKey`). `null`이면 아직 안 닫았다.
     *
     * ⚠️ **여기 있는 이유는 「복기 하기」가 대국 화면을 컴포지션에서 빼기 때문이다**(2026-09-22
     * 실기에서 잡혔다). 닫았다는 기억이 `GoCoachContent`의 `remember`에만 있으면, 복기를 보고
     * 돌아오는 순간 그 상태가 새로 만들어져 **판정 결과가 다시 뜬다** — 사용자는 방금 읽고
     * 스스로 닫은 팝업을 또 닫아야 한다.
     * ⚠️ 새 대국이 시작되면 [clearDismissedJudgement]로 비운다 — 안 비우면 수순·결과가 우연히
     * 같은 다음 판의 결과가 **조용히 삼켜진다**(열쇠가 그 둘로 만들어진다).
     */
    var dismissedJudgementKey by mutableStateOf<String?>(null)
        private set

    fun markJudgementDismissed(key: String?) {
        dismissedJudgementKey = key
    }

    fun clearDismissedJudgement() {
        dismissedJudgementKey = null
    }
}
