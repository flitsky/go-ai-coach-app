package com.worksoc.goaicoach.application.premium

/**
 * **구독 확인이 얼마나 낡았는가**를 판정한다(백로그 #174, 2026-09-18 사용자 설계).
 *
 * ## 무엇을 재는가 — ⚠️ **만료일이 아니다**
 * 앱은 구독이 *언제 끝나는지* 모른다. Play Billing 클라이언트는 `expiryTimeMillis`를 주지 않고,
 * 그것은 **서버 API에만** 있다(`PREMIUM_MODE.md`). 그래서 이 정책은 만료일을 주장하지 않고
 * ***"마지막으로 확인한 「소유」 답을 언제까지 믿을 것인가"*** 하나만 정한다.
 *
 * ⭐ **그래서 「두 개의 진실」이 생기지 않는다** — `PREMIUM_MODE.md`가 자체 만료 시계를 접으며
 * 경계한 것이 *"Play와 앱이라는 두 진실이 어긋나는 날 어느 쪽을 믿을지"* 였는데, 앱의 시계가
 * **신뢰 한도만** 말하면 주장이 겹치지 않는다. Play가 답하는 순간 언제나 Play가 이긴다.
 *
 * ## 두 문턱
 * - [RecheckAfterMillis] (**24시간**) — 이만큼 지났으면 답이 낡았다고 보고 **다시 묻는다.**
 *   묻는 것일 뿐 권한은 그대로다.
 * - [ExpireAfterMillis] (**72시간**) — 이만큼 **확인에 성공하지 못했으면** 더는 못 믿는다.
 *   사용자에게 알리고 **일반 사용자로 내린다.**
 *
 * ## ⚠️ 이것은 #158의 관문을 대체하지 않는다 — **두 번째 경로**다
 * 즉시 강등은 여전히 `isAuthoritativeNotOwned`(Play가 **권위 있게 "미소유"** 라고 답함) 하나로만
 * 일어난다. 이 정책은 *"아무 답도 못 얻은 채 사흘이 지났다"* 는 **다른 사유**로 내린다.
 * ⚠️ 조회 실패 한 번이 유료 구독자를 내리는 일은 여전히 없어야 한다 — 그것이 #158이 막은 결함이다.
 */
object SubscriptionFreshnessPolicy {

    /** 확인을 다시 시도할 간격 — 24시간. */
    const val RecheckAfterMillis: Long = 24L * 60L * 60L * 1000L

    /** 확인에 성공하지 못한 채 이만큼 지나면 더는 믿지 않는다 — 72시간. */
    const val ExpireAfterMillis: Long = 72L * 60L * 60L * 1000L

    /**
     * @param lastVerifiedAtMillis 마지막으로 **확인에 성공한** 시각. 한 번도 없었으면 `null`.
     * @param nowMillis 지금.
     */
    fun evaluate(lastVerifiedAtMillis: Long?, nowMillis: Long): SubscriptionFreshness {
        // ⚠️ **한 번도 확인한 적이 없으면 곧바로 내리지 않는다.** 이 필드는 #174에서 새로 생겼고,
        // 이미 구독 중인 사용자의 저장값에는 **없다**(디코더가 `null`을 준다). 여기서 Expired를
        // 돌려주면 업데이트를 받은 유료 구독자가 앱을 켜자마자 전부 강등된다.
        // 대신 다시 묻게만 하고, 그 확인이 성공하면 그때 시각이 찍힌다.
        if (lastVerifiedAtMillis == null) return SubscriptionFreshness.NeedsRecheck

        val elapsed = nowMillis - lastVerifiedAtMillis

        // ⚠️ **시계를 되감으면 「확인 시각이 미래」가 된다**(함정 12 — 이 앱은 `AppClock`을 쓰지 않고
        // `System.currentTimeMillis`를 직접 읽는다). 그대로 두면 `elapsed`가 음수라 영원히 Fresh고,
        // **시계를 앞당겼다 되돌리는 것만으로 72시간을 영영 피한다.** 낡은 것으로 취급한다.
        if (elapsed < 0) return SubscriptionFreshness.NeedsRecheck

        return when {
            elapsed >= ExpireAfterMillis -> SubscriptionFreshness.Expired
            elapsed >= RecheckAfterMillis -> SubscriptionFreshness.NeedsRecheck
            else -> SubscriptionFreshness.Fresh
        }
    }
}

/** [SubscriptionFreshnessPolicy.evaluate]의 세 갈래. */
enum class SubscriptionFreshness {
    /** 충분히 최근에 확인했다 — 아무것도 하지 않는다. */
    Fresh,

    /** 다시 물어볼 때가 됐다. ⚠️ **권한은 그대로 둔다** — 묻는 것과 내리는 것은 다르다. */
    NeedsRecheck,

    /** 사흘 넘게 확인하지 못했다 — 안내하고 내린다. */
    Expired,
}

/**
 * **사흘 넘게 확인하지 못한 구독을 내린다**(백로그 #174) — 내릴 것이 없으면 `null`.
 *
 * ## ⚠️ 이것은 #158의 즉시 강등과 **다른 사유**다
 * #158은 *"Play가 미소유라고 답했다"* 로 내린다. 이쪽은 *"아무 답도 못 얻은 채 72시간이 지났다"* 로
 * 내린다. 두 경로를 합치지 말 것 — 합치는 순간 **조회 실패 한 번이 유료 구독자를 내리는** 결함이
 * 되살아난다(#158이 막은 바로 그것).
 *
 * ## ⚠️ 구독([PremiumSource.Purchase])에만 적용된다
 * 광고 1시간은 스스로 시간으로 만료되고, [PremiumSource.None]은 내릴 것이 없다.
 *
 * ## ⚠️ `claimedFeatures`는 남긴다
 * 출석 3일차로 받은 무르기 같은 영구 클레임은 구독과 다른 축이다(#173 실기에서 해지 뒤에도
 * 1회권 30개가 남은 것과 같은 이유). `copy`가 그것을 지킨다.
 */
fun decideStaleSubscriptionDowngrade(state: PremiumState, nowMillis: Long): PremiumState? {
    if (state.source != PremiumSource.Purchase) return null
    if (SubscriptionFreshnessPolicy.evaluate(state.lastSubscriptionVerifiedAtMillis, nowMillis)
        != SubscriptionFreshness.Expired
    ) {
        return null
    }
    // ⚠️ **확인 시각도 함께 비운다.** 안 비우면 다음 포그라운드 복귀마다 또 Expired로 판정돼
    // **팝업이 매번 다시 뜬다** — 이미 일반 사용자로 내려 더 내릴 것이 없는데도.
    return state.copy(source = PremiumSource.None, lastSubscriptionVerifiedAtMillis = null)
}
