package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork

/**
 * 형세 보기를 **켜 둔 채** 두는 동안, 화면의 형세가 사람 모델의 임시 값이면 주 모델로 다시 잴지(백로그 #215 보강 ②).
 *
 * 급수 캐릭터와 두는 동안 AI가 둔 뒤의 형세는 사람 모델이 본 임시 값이다(`ScoreEstimate.network == Human`). 형세 보기를 **누른**
 * 순간은 주 모델이 답하지만, 켜 둔 토글은 그 뒤로 수마다 그 임시 값을 그대로 보여 준다 — 켜 둔 사람은 다시 누를 수도 없다
 * (누르면 꺼진다). 그래서 켜져 있는 동안에는 **사람 차례가 올 때마다** 한 번 다시 잰다.
 *
 * - **형세 보기가 꺼져 있으면 재지 않는다** — 안 보는 값을 위해 엔진을 갈아 올리지 않는다(갈아 올리기 한 번이 S23에서 약 1.6초).
 *   1회권으로 켠 표시는 다음 수에서 스스로 꺼지므로 여기 걸리지 않는다.
 * - **AI 차례에는 재지 않는다** — 그 차례가 곧 엔진을 쓴다. AI끼리 두는 판은 그래서 끝까지 임시 값이고, 끝난 뒤에 다시 잰다(보강 ①).
 * - **한 국면에 한 번만 시도한다**([ProvisionalScoreRefineAttempt]) — 실패한 재측정을 엔진이 한가해질 때마다 되풀이하지 않는다.
 * - 재측정은 `ScoreEstimate` 오퍼레이션이라 **착수를 막지 않는다** — 사용자가 먼저 두면 취소되고, 그 수의 동기화가 판을 다시 맞춘다.
 */
data class ProvisionalScoreRefineInput(
    val isScoreViewOn: Boolean,
    /** 지금 화면에 걸린 형세를 낸 망. 형세가 없으면 `null`. */
    val shownEstimateNetwork: EngineNetwork?,
    val isGameEnded: Boolean,
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    /** 무르기 뒤의 재동기화가 아직 남았는가 — 그 동기화가 형세를 새로 낸다. */
    val isPendingUndoSync: Boolean,
    val isHumanTurn: Boolean,
    val attempt: ProvisionalScoreRefineAttempt,
)

/** 재측정을 시도하는 국면 하나 — 세션 세대(무르기·새 대국마다 바뀐다)와 수순 번호. */
data class ProvisionalScoreRefineAttempt(
    val sessionGeneration: Long,
    val moveNumber: Int,
)

fun shouldRefineProvisionalScore(
    input: ProvisionalScoreRefineInput,
    lastAttempt: ProvisionalScoreRefineAttempt?,
): Boolean =
    input.isScoreViewOn &&
        input.shownEstimateNetwork == EngineNetwork.Human &&
        !input.isGameEnded &&
        input.isEngineReady &&
        !input.isEngineBusy &&
        !input.isPendingUndoSync &&
        input.isHumanTurn &&
        input.attempt != lastAttempt
