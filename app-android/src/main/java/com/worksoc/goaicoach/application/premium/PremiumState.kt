package com.worksoc.goaicoach.application.premium

/**
 * 프리미엄 활성화 소스. [Purchase]는 영구, [AdGrant]는 부여된 대국(세션)에 한해 최대
 * [PremiumState.AdGrantDurationMillis] 동안만 유효하다. 실제 광고/결제 연동(Step 3, 4)
 * 이전까지는 [AdGrant]가 즉시 활성화되는 스텁으로 동작한다.
 */
internal enum class PremiumSource {
    None,
    AdGrant,
    Purchase,
}

/**
 * 프리미엄 모드 상태. 플랫폼(Google Play 결제/광고 SDK)에 의존하지 않는 순수 로직으로
 * 설계해, 추후 iOS 쪽 활성화 소스를 추가할 때 이 타입 자체는 재사용할 수 있게 한다.
 */
internal data class PremiumState(
    val source: PremiumSource = PremiumSource.None,
    val adGrantSessionGeneration: Long? = null,
    val adGrantStartedAtMillis: Long? = null,
) {
    /**
     * 현재 대국(세션)과 시각 기준으로 프리미엄이 유효한지 판정한다.
     * - [PremiumSource.Purchase]는 항상 유효.
     * - [PremiumSource.AdGrant]는 부여된 세션과 현재 세션이 같고, 부여 후 1시간이
     *   지나지 않았을 때만 유효 (대국 1판 한정 + 1시간 한도, 둘 중 먼저 도달하는 조건으로 만료).
     * - [PremiumSource.None]은 항상 무효.
     */
    fun isActive(currentSessionGeneration: Long, nowMillis: Long): Boolean =
        when (source) {
            PremiumSource.Purchase -> true
            PremiumSource.AdGrant ->
                adGrantSessionGeneration == currentSessionGeneration &&
                    adGrantStartedAtMillis != null &&
                    nowMillis - adGrantStartedAtMillis < AdGrantDurationMillis
            PremiumSource.None -> false
        }

    companion object {
        const val AdGrantDurationMillis: Long = 60L * 60L * 1000L

        fun adGranted(sessionGeneration: Long, nowMillis: Long): PremiumState =
            PremiumState(
                source = PremiumSource.AdGrant,
                adGrantSessionGeneration = sessionGeneration,
                adGrantStartedAtMillis = nowMillis,
            )

        fun purchased(): PremiumState = PremiumState(source = PremiumSource.Purchase)
    }
}
