package com.worksoc.goaicoach.shared.playstyle

import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.customRank

/**
 * 5계층 — **캐릭터가 사람처럼 두는 기풍**(백로그 #215). 캐릭터의 성향을 담는 도메인(#212)의 첫 칸이다.
 *
 * 캐릭터는 탐색으로 좋은 수를 찾은 뒤 일부러 못한 수를 고르지 않는다 — **그 급수의 사람이 둘 법한 수**를
 * 사람 모델(KataGo Human SL)의 정책에서 뽑는다. 무엇을 뽑을지는 프로필 하나([profile])가 정한다.
 *
 * ⚠️ 이 타입은 **무엇을 흉내 낼지**만 안다. 사람 모델이 기기에 있는지, 지금 올라가 있는지는 엔진의 일이다
 * (`EngineCoreApi.supportsHumanNetwork`) — 없으면 그 캐릭터는 예전 방식(`PlayLevelSetting.selectionPolicy`)으로 둔다.
 *
 * @property profile 사람 모델의 프로필 이름(`rank_15k`). 공식 KGS 급수를 **믿고 쓴다**(사용자 2026-10-04) — 캐릭터의 세기는
 *   이 값을 범위 안에서 고쳐 맞춘다(밸런스 패치).
 * @property weakestRank·[strongestRank] 이 캐릭터가 맡는 구간(U-59). 표시·밸런스 패치의 울타리다 — 착수에는 [profile]만 쓴다.
 */
data class HumanPlayStyle(
    val profile: String,
    val weakestRank: String,
    val strongestRank: String,
) {
    /**
     * 이 기풍이 흉내 내는 급수 — [profile]에서 읽는다(백로그 #224: 상대 고르기 화면이 캐릭터의 실력을 「15급 수준」으로 보인다).
     * 급수를 글자로 따로 적지 않는다 — 밸런스 패치로 [profile]을 고치면 화면의 표기가 함께 따라온다.
     */
    val rank: KgsRank? get() = KgsRank.all.firstOrNull { it.profile == profile }
}

/**
 * 이 단계의 캐릭터가 사람 모델로 둔다면 그 기풍, 아니면 `null`.
 *
 * **캐릭터 다섯이 모두 사람 모델로 둔다**(사용자 2026-10-06, 폰에서 둬 본 뒤 정한 값):
 * 문하생 판다 **15급** · 문하생 돌뫼 **9급** · 수제자 반상 **1급** · 사범 꼬북 **3단** · 관장 천원 **7단**.
 * 숨겨 둔 다른 그룹(초급·중급·고급)은 예전 방식 그대로다.
 *
 * ⚠️ 2026-10-05에는 고수·초고수를 **주 모델 탐색**(16·32방문)으로 두기로 했었다 — 그 탐색은 9단 프로필을 전승으로 이기는 세기라
 * (실험실 E2b) 「3단」·「7단」이라는 수준이 될 수 없다. 그 길은 이제 **사람 모델을 못 쓸 때의 폴백**으로만 남는다
 * (`PlayLevelGroup.aiMoveVisits`).
 * ⚠️ 값을 바꿀 때는 구간([HumanPlayStyle.weakestRank]~[HumanPlayStyle.strongestRank]) 안에서 — 구간은 U-59가 정했다.
 */
fun PlayLevelSetting.humanPlayStyle(): HumanPlayStyle? {
    // 급수를 직접 정한 상대(승급 대국, 백로그 #219) — 그 급수의 공식 프로필 그대로 둔다. 구간이 한 칸이다.
    customRank()?.let { rank ->
        val label = "${rank.number}${if (rank.isDan) "d" else "k"}"
        return HumanPlayStyle(profile = rank.profile, weakestRank = label, strongestRank = label)
    }
    if (group != PlayLevelGroup.FastBeginner) return null
    return when (safeLevel) {
        1 -> HumanPlayStyle(profile = "rank_15k", weakestRank = "18k", strongestRank = "12k")
        2 -> HumanPlayStyle(profile = "rank_9k", weakestRank = "12k", strongestRank = "6k")
        3 -> HumanPlayStyle(profile = "rank_1k", weakestRank = "6k", strongestRank = "1k")
        4 -> HumanPlayStyle(profile = "rank_3d", weakestRank = "1d", strongestRank = "5d")
        5 -> HumanPlayStyle(profile = "rank_7d", weakestRank = "5d", strongestRank = "9d")
        else -> null
    }
}
