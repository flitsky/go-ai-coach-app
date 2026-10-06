package com.worksoc.goaicoach.shared.playstyle

import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.customRank

/**
 * 5계층 — **급수 캐릭터가 사람처럼 두는 기풍**(백로그 #215). 캐릭터의 성향을 담는 도메인(#212)의 첫 칸이다.
 *
 * 급수 캐릭터(초보·하수·중수)는 탐색으로 좋은 수를 찾은 뒤 일부러 못한 수를 고르지 않는다 — **그 급수의 사람이 둘 법한 수**를
 * 사람 모델(KataGo Human SL)의 정책에서 뽑는다. 무엇을 뽑을지는 프로필 하나([profile])가 정한다.
 *
 * ⚠️ 이 타입은 **무엇을 흉내 낼지**만 안다. 사람 모델이 기기에 있는지, 지금 올라가 있는지는 엔진의 일이다
 * (`EngineCoreApi.supportsHumanNetwork`) — 없으면 그 캐릭터는 지금 방식(`PlayLevelSetting.selectionPolicy`)으로 둔다.
 *
 * @property profile 사람 모델의 프로필 이름(`rank_15k`). 공식 KGS 급수를 **믿고 쓴다**(사용자 2026-10-04) — 캐릭터의 세기는
 *   이 값을 범위 안에서 고쳐 맞춘다(밸런스 패치).
 * @property weakestRank·[strongestRank] 이 캐릭터가 맡는 구간(U-59). 표시·밸런스 패치의 울타리다 — 착수에는 [profile]만 쓴다.
 */
data class HumanPlayStyle(
    val profile: String,
    val weakestRank: String,
    val strongestRank: String,
)

/**
 * 이 단계의 캐릭터가 사람 모델로 둔다면 그 기풍, 아니면 `null`.
 *
 * 사람 모델로 두는 것은 **빠른 초급의 급 구간 셋**(초보 12~18급 · 하수 6~12급 · 중수 1~6급)뿐이다. 고수·초고수는 주 모델 탐색으로
 * 단 구간을 내고(사용자 2026-10-05), 숨겨 둔 다른 그룹(초급·중급·고급)은 지금 방식 그대로다.
 *
 * ⚠️ 프로필은 구간의 **가운데**에서 시작한다(스레드 제안 — 사용자 승인 전). 실험실 E4에서 17~11급은 13줄·9줄에서 거의 안 갈렸다 —
 * 초보와 하수의 차이가 작게 느껴지면 여기 값을 벌린다.
 */
fun PlayLevelSetting.humanPlayStyle(): HumanPlayStyle? {
    // 급수를 직접 정한 상대(기력 측정 대국, 백로그 #219) — 그 급수의 공식 프로필 그대로 둔다. 구간이 한 칸이다.
    customRank()?.let { rank ->
        val label = "${rank.number}${if (rank.isDan) "d" else "k"}"
        return HumanPlayStyle(profile = rank.profile, weakestRank = label, strongestRank = label)
    }
    if (group != PlayLevelGroup.FastBeginner) return null
    return when (safeLevel) {
        1 -> HumanPlayStyle(profile = "rank_15k", weakestRank = "18k", strongestRank = "12k")
        2 -> HumanPlayStyle(profile = "rank_9k", weakestRank = "12k", strongestRank = "6k")
        3 -> HumanPlayStyle(profile = "rank_3k", weakestRank = "6k", strongestRank = "1k")
        else -> null
    }
}
