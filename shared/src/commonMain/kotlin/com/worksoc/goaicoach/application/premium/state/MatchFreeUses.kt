package com.worksoc.goaicoach.application.premium.state

/**
 * 6계층 — **대국 한 판의 무료 사용**(백로그 #228, 사용자 2026-10-08).
 *
 * 사용자가 정한 것: 캐릭터와 대국할 때 **누구나**, **매 대국 시작 시 3회씩**(형세 보기 3회 · 추천 수 3회 · 무르기 3회) 고정으로 받는다.
 * 다 쓰면 지금과 같이 연다(1회권 · 광고 1시간 · 구독 — 무르기는 출석 3일차의 영구 해금도). 분석의 질은 낮추지 않는다(가장 센 모델 그대로).
 * **다시보기에서는 주지 않는다.**
 * 계기는 사용자 피드백 「광고 없애기(처음엔 진입 쉽게)」 — 표가 없는 새 사용자가 광고부터 만나지 않고 기능을 먼저 써 보게 한다.
 * **무르기**는 2026-10-09에 더했다(백로그 #242) — 새 사용자가 출석 3일차에 무제한 무르기를 얻기 **전에도** 한 판에 세 번은 무를 수 있게.
 *
 * 「한 판」은 **대국 세대**([matchGeneration] — `GameSessionRuntimeState.matchGeneration`)다: 새 대국·이어하기에서만 오르고
 * 무르기에는 그대로다(무른다고 횟수가 돌아오지 않는다).
 *
 * ⚠️ 이 값은 **몇 번 썼는가**만 안다. 이 판이 무료 사용을 주는 판인가(캐릭터와 두는 판인가)는 부르는 쪽이 가린다
 * (`isFreeUseMatch`). 구독·광고 1시간으로 이미 열려 있으면 부르는 쪽이 이것을 쓰지 않는다 — 횟수가 억울하게 닳지 않게.
 * ⚠️ 저장하지 않는다 — 앱을 껐다 켜 이어 둔 판은 다시 3회를 받는다(단속하지 않는다는 `FEATURE_ACCESS_PRINCIPLES.md` 8.8의 기조).
 */
data class MatchFreeUses(
    val matchGeneration: Long = NoMatch,
    val used: Map<FeatureId, Int> = emptyMap(),
) {
    /** [matchGeneration]인 판에서 [featureId]를 무료로 쓸 수 있는 남은 횟수. 무료 사용이 없는 기능이면 0. */
    fun remaining(featureId: FeatureId, matchGeneration: Long): Int {
        if (featureId !in Features) return 0
        val usedInThisMatch = if (matchGeneration == this.matchGeneration) used[featureId] ?: 0 else 0
        return (PerMatch - usedInThisMatch).coerceAtLeast(0)
    }

    /** 한 번 썼다. 다른 판에서 센 것은 버린다(새 판은 처음부터 센다). */
    fun afterUsing(featureId: FeatureId, matchGeneration: Long): MatchFreeUses {
        val usedInThisMatch = if (matchGeneration == this.matchGeneration) used else emptyMap()
        return MatchFreeUses(
            matchGeneration = matchGeneration,
            used = usedInThisMatch + (featureId to (usedInThisMatch[featureId] ?: 0) + 1),
        )
    }

    companion object {
        /** 한 판에 기능마다 무료로 쓰는 횟수(사용자 2026-10-08: 3회 고정). */
        const val PerMatch: Int = 3

        /** 무료 사용이 있는 기능 — 형세 보기 · 추천 수 · 무르기 셋이다(무르기는 백로그 #242). */
        val Features: Set<FeatureId> = setOf(FeatureId.Eval, FeatureId.TopMoves, FeatureId.Undo)

        private const val NoMatch: Long = -1L
    }
}
