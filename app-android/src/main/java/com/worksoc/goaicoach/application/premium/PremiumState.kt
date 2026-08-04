package com.worksoc.goaicoach.application.premium

/**
 * 프리미엄 활성화 소스. [Purchase]는 영구, [AdGrant]는 부여 시점부터 최대
 * [PremiumState.AdGrantDurationMillis] 동안만 유효하다 — 그 사이에 대국을 몇 판을 하든
 * 시간이 남아 있으면 계속 유효하다(대국 단위로 끊기지 않는다). 실제 광고/결제 연동
 * (Step 3, 4) 이전까지는 [AdGrant]가 즉시 활성화되는 스텁으로 동작한다.
 */
internal enum class PremiumSource {
    None,
    AdGrant,
    Purchase,
}

/**
 * 6계층(Session & Continuity) — 프리미엄 모드 상태. 플랫폼(Google Play 결제/광고 SDK)에
 * 의존하지 않는 순수 로직으로 설계해, 추후 iOS 쪽 활성화 소스를 추가할 때 이 타입 자체는
 * 재사용할 수 있게 한다. [PremiumStateStorePort]와 같은 패키지에 있지만 계층이 다르다 —
 * 이 타입은 "지금 유효한 프리미엄 상태가 무엇인가"를 나타내는 상태(6계층)이고,
 * [PremiumStateStorePort]는 그 상태를 저장/복원하는 외부 저장소 포트(4계층)다.
 */
internal data class PremiumState(
    val source: PremiumSource = PremiumSource.None,
    val adGrantStartedAtMillis: Long? = null,
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
