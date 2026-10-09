package com.worksoc.goaicoach.shared.policy

/**
 * 5계층 — 기력의 **구간(티어)**. 급수 29칸([KgsRank])을 일곱 묶음으로 나눈다(백로그 #236, 2026-10-09 사용자).
 *
 * 브론즈 20~16급 · 실버 15~11급 · 골드 10~6급 · 플래티넘 5~1급 · 다이아 1~3단 · 마스터 4~6단 · 그랜드 마스터 7~9단.
 * 급은 다섯 칸씩, 단은 세 칸씩이다 — 경계는 이 표가 유일한 정본이고, 화면은 [KgsRank.tier]로만 묻는다.
 *
 * ⚠️ 구간은 **꾸밈의 단위**다(홈 카드의 테두리). 기력을 옮기는 규칙(승급·강급)은 여전히 한 칸씩이고 구간을 모른다.
 * ⚠️ 선언 순서가 곧 세기 순서다 — 화면이 `ordinal`로 "얼마나 화려한가"를 정한다. 사이에 끼워 넣으면 그 뒤가 전부 한 단계씩 밀린다.
 */
enum class KgsRankTier(val weakest: KgsRank, val strongest: KgsRank) {
    Bronze(KgsRank.kyu(20), KgsRank.kyu(16)),
    Silver(KgsRank.kyu(15), KgsRank.kyu(11)),
    Gold(KgsRank.kyu(10), KgsRank.kyu(6)),
    Platinum(KgsRank.kyu(5), KgsRank.kyu(1)),
    Diamond(KgsRank.dan(1), KgsRank.dan(3)),
    Master(KgsRank.dan(4), KgsRank.dan(6)),
    Grandmaster(KgsRank.dan(7), KgsRank.dan(9)),
    ;

    operator fun contains(rank: KgsRank): Boolean = rank in weakest..strongest

    companion object {
        fun of(rank: KgsRank): KgsRankTier = entries.first { rank in it }
    }
}

/** 이 급수가 속한 구간. */
val KgsRank.tier: KgsRankTier get() = KgsRankTier.of(this)
