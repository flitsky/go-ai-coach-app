package com.worksoc.goaicoach.application.premium

/**
 * 프리미엄 활성화 소스. [Purchase]는 영구, [AdGrant]는 부여된 대국(매치)에 한해 최대
 * [PremiumState.AdGrantDurationMillis] 동안만 유효하다. 실제 광고/결제 연동(Step 3, 4)
 * 이전까지는 [AdGrant]가 즉시 활성화되는 스텁으로 동작한다.
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
    val adGrantMatchGeneration: Long? = null,
    val adGrantStartedAtMillis: Long? = null,
) {
    /**
     * 현재 대국(매치)과 시각 기준으로 프리미엄이 유효한지 판정한다.
     * - [PremiumSource.Purchase]는 항상 유효.
     * - [PremiumSource.AdGrant]는 부여된 매치와 현재 매치가 같고, 부여 후 1시간이
     *   지나지 않았을 때만 유효 (대국 1판 한정 + 1시간 한도, 둘 중 먼저 도달하는 조건으로 만료).
     *   여기서 "매치"는 무르기(undo)로는 바뀌지 않고 실제로 새 대국이 시작될 때만 바뀌는
     *   식별자다 — 엔진 오퍼레이션 무효화용 sessionGeneration과는 별개 개념.
     * - [PremiumSource.None]은 항상 무효.
     */
    fun isActive(currentMatchGeneration: Long, nowMillis: Long): Boolean =
        when (source) {
            PremiumSource.Purchase -> true
            PremiumSource.AdGrant ->
                adGrantMatchGeneration == currentMatchGeneration &&
                    adGrantStartedAtMillis != null &&
                    nowMillis - adGrantStartedAtMillis < AdGrantDurationMillis
            PremiumSource.None -> false
        }

    /**
     * 아직 매치에 묶이지 않은(홈 화면에서 활성화된) [AdGrant]를, 실제로 대국이 시작되어
     * 배정된 [matchGeneration]으로 확정한다. 이미 매치가 배정돼 있으면 그대로 둔다
     * (이 경우 새 대국이 또 시작된 것이므로, 굳이 덮어쓰지 않아도 매치 불일치로 자연히 만료된다).
     */
    fun bindToMatchIfPending(matchGeneration: Long): PremiumState =
        if (source == PremiumSource.AdGrant && adGrantMatchGeneration == null) {
            copy(adGrantMatchGeneration = matchGeneration)
        } else {
            this
        }

    companion object {
        const val AdGrantDurationMillis: Long = 60L * 60L * 1000L

        /**
         * [matchGeneration]이 `null`이면 "아직 어느 대국에도 묶이지 않음"을 뜻한다 (예: 대국이
         * 시작되기 전 홈 화면에서 활성화한 경우). 이 경우 [isActive]는 항상 false를 반환하다가,
         * 실제 대국이 시작되어 매치가 배정되는 시점에 상위 레이어(GoCoachApp)가 그 매치 번호로
         * 다시 바인딩해야 한다 — 그렇지 않으면 활성화 시점의 stale한 매치 번호에 묶여 정작
         * 시작된 대국에서는 무효 판정될 수 있다.
         */
        fun adGranted(matchGeneration: Long?, nowMillis: Long): PremiumState =
            PremiumState(
                source = PremiumSource.AdGrant,
                adGrantMatchGeneration = matchGeneration,
                adGrantStartedAtMillis = nowMillis,
            )

        fun purchased(): PremiumState = PremiumState(source = PremiumSource.Purchase)
    }
}
