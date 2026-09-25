package com.worksoc.goaicoach.application.premium.state

/**
 * 프리미엄 활성화 소스. [Purchase]는 영구, [AdGrant]는 부여 시점부터 최대
 * [PremiumState.AdGrantDurationMillis] 동안만 유효하다 — 그 사이에 대국을 몇 판을 하든
 * 시간이 남아 있으면 계속 유효하다(대국 단위로 끊기지 않는다). 실제 광고/결제 연동
 * (Step 3, 4) 이전까지는 [AdGrant]가 즉시 활성화되는 스텁으로 동작한다.
 */
enum class PremiumSource {
    None,
    AdGrant,
    Purchase,
}

/** 프리미엄 게이팅 대상 기능. [FeatureAccessPolicy]가 이 각각에 대해 접근 가능 여부를 판정한다. */
enum class FeatureId {
    Undo,
    Eval,
    TopMoves,
    MoveReview,
    BoardScan,
}

/**
 * 6계층(Session & Continuity) — 프리미엄 모드 상태. 플랫폼(Google Play 결제/광고 SDK)에
 * 의존하지 않는 순수 로직으로 설계해, 추후 iOS 쪽 활성화 소스를 추가할 때 이 타입 자체는
 * 재사용할 수 있게 한다. [PremiumStateStorePort]와는 계층이 다르고 패키지도 다르다(`premium.port`) —
 * 이 타입은 "지금 유효한 프리미엄 상태가 무엇인가"를 나타내는 상태(6계층)이고,
 * [PremiumStateStorePort]는 그 상태를 저장/복원하는 외부 저장소 포트(4계층)다.
 */
data class PremiumState(
    val source: PremiumSource = PremiumSource.None,
    val adGrantStartedAtMillis: Long? = null,
    // 앱 내 활동/프로모션으로 얻은 영구 클레임 원장(GOOGLE_PLAY_LAUNCH_PLAN.md 3장의 "무르기 무료
    // 클레임"이 첫 사례) — [source]와 별개 축이다. 한 번 담긴 [FeatureId]는 그 기능의 기본
    // 정책이 나중에 바뀌어도 재평가하지 않고 계속 남는다("지금 기본으로 무료인가"가 아니라
    // "이 유저가 예전에 클레임을 받았는가"만 저장). [purchased]/[adGranted]로 전이할 때 이
    // 필드까지 같이 초기화되지 않도록 호출부(`GoCoachApp.kt`)가 `.copy(claimedFeatures = ...)`로
    // 이어붙인다.
    val claimedFeatures: Set<FeatureId> = emptySet(),
    /**
     * **마지막으로 Play에 구독 소유를 확인하는 데 성공한 시각**(백로그 #174).
     *
     * ⚠️ **만료 시각이 아니다.** 앱은 구독이 언제 끝나는지 모른다 — 이 값은 오직
     * [SubscriptionFreshnessPolicy]가 *"이 답을 언제까지 믿을지"* 를 재는 데만 쓴다.
     * **여기에 `purchaseTime + 한 달` 같은 값을 넣지 말 것**(그 판단이 #158이 자체 시계를 접은 이유다).
     *
     * ⚠️ **`null`은 "확인에 실패했다"가 아니라 "아직 한 번도 확인한 적이 없다"이다** — #174 이전
     * 저장값에는 이 키가 없어 디코더가 `null`을 준다. 정책이 그 경우를 **강등이 아니라 재조회**로
     * 받는 이유가 그것이다(업데이트를 받은 유료 구독자를 켜자마자 내리지 않기 위해).
     *
     * ⚠️ 광고 1시간([PremiumSource.AdGrant])과는 무관하다 — 그쪽 수명은 [adGrantStartedAtMillis]가 잰다.
     */
    val lastSubscriptionVerifiedAtMillis: Long? = null,
) {
    /**
     * 현재 시각 기준으로 프리미엄이 유효한지 판정한다.
     * - [PremiumSource.Purchase]는 항상 유효.
     * - [PremiumSource.AdGrant]는 부여 후 1시간이 지나지 않았을 때 유효하다 — 그 시간 안에
     *   대국을 새로 몇 판 시작하든(무르기, 새 대국 등과 무관하게) 계속 유효하다. 특정
     *   대국(매치)에 묶이지 않는다.
     * - [PremiumSource.None]은 항상 무효.
     */
    fun isActive(nowMillis: Long): Boolean =
        when (source) {
            PremiumSource.Purchase -> true
            PremiumSource.AdGrant ->
                adGrantStartedAtMillis != null && nowMillis - adGrantStartedAtMillis < AdGrantDurationMillis
            PremiumSource.None -> false
        }

    /**
     * **구독(또는 그 자리를 잇는 영속 권한)이 살아 있는가** — 로스터 전체를 여는 축이다(백로그 #157).
     *
     * ⚠️ **[isActive]와 갈라진다. 광고 1시간([PremiumSource.AdGrant])은 여기 포함되지 않는다.**
     * 인게임 기능은 광고로도 1시간 열리지만, **캐릭터 로스터까지 열리면 안 된다** — 광고 몇 번이
     * 월 구독과 같은 것을 주면 구독을 살 이유가 사라진다(`FEATURE_ACCESS_PRINCIPLES.md` 8.2가
     * 중간안 *"보유한 모두에게 무제한"* 을 폐기한 것과 같은 이유다).
     *
     * ⚠️ **이 권한은 저장소에 쓰지 않는다** — 구독은 **살아 있는 상태**이지 획득 이력이 아니다.
     * `claimedBots`에 넣으면 해지 뒤에도 캐릭터가 남는다(#157).
     *
     * 지금 [PremiumSource.Purchase]가 그 자리를 들고 있고, **#158이 그것을 월 구독으로 바꾼다** —
     * 그때 이 함수는 고칠 것이 없다.
     */
    fun isSubscriptionActive(): Boolean = source == PremiumSource.Purchase

    /**
     * 저장소에서 막 읽어온 상태를 신뢰해도 되는지 판정한다. [PremiumSource.AdGrant]의 시작
     * 시각이 현재보다 미래라면(기기 시계 되돌림, 디스크 손상 등) 신뢰할 수 없다는 신호다 —
     * 그런 값을 그대로 믿으면 [isActive]의 경과시간 계산(`nowMillis - adGrantStartedAtMillis`)이
     * 음수가 되어 영영 만료되지 않는 프리미엄으로 오판된다. 저장소 어댑터(4계층,
     * `persistence/PremiumStateStore.kt`)가 [load] 시점에 이 판정을 거쳐 신뢰할 수 없는 값은
     * 기본 상태로 폴백해야 한다.
     */
    fun isClockPlausibleAt(nowMillis: Long): Boolean =
        when (source) {
            PremiumSource.AdGrant -> adGrantStartedAtMillis?.let { startedAt -> startedAt <= nowMillis } ?: false
            PremiumSource.Purchase, PremiumSource.None -> true
        }

    companion object {
        const val AdGrantDurationMillis: Long = 60L * 60L * 1000L

        fun adGranted(nowMillis: Long): PremiumState =
            PremiumState(source = PremiumSource.AdGrant, adGrantStartedAtMillis = nowMillis)

        fun purchased(): PremiumState = PremiumState(source = PremiumSource.Purchase)
    }
}
