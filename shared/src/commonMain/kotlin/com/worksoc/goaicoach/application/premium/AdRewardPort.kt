package com.worksoc.goaicoach.application.premium

/**
 * 4계층(External Integration) α — 실제 리워드 광고 SDK(AdMob) 호출을 감싸는 순수 포트.
 * 실제 어댑터는 `ui/AndroidRewardedAdClient.kt`(플랫폼 계층)에 둔다 — `AuthClientPort`와
 * 같은 자리(premium-mode/README.md "계층 배치 참고" 표). 광고를 보여줄 Activity/광고단위 ID
 * 같은 플랫폼 세부사항은 이 인터페이스의 메서드 시그니처가 아니라 어댑터의 생성자가 받는다 —
 * `AndroidAuthClient`가 `FirebaseAuth.getInstance()`를 내부에서 직접 쓰는 것과 같은 이유로,
 * `application/premium`이 android.app.Activity 등 플랫폼 타입에 의존하지 않게 한다.
 */
interface AdRewardPort {
    /** 광고를 로드하고 곧바로 노출한다. 시청 완료(보상 획득) 여부만 [AdRewardOutcome]으로 반환한다. */
    suspend fun showRewardedAd(): AdRewardOutcome
}

/** [AdRewardOutcome.NotRewarded]가 발생한 원인 — 진단 로그에서 구분하기 위함이다. */
enum class AdRewardFailureReason {
    /** 광고 자체를 불러오지 못함(재고 없음, 네트워크 오류 등). */
    LoadFailed,

    /** 로드는 됐지만 노출 단계에서 실패함(드물게 발생, 만료된 광고 등). */
    ShowFailed,

    /** 광고는 정상 노출됐지만 사용자가 보상 지점 전에 닫거나 건너뜀. */
    DismissedWithoutReward,

    /** 광고를 노출할 Activity를 확보하지 못하는 등, SDK 호출 자체를 시도할 수 없었던 경우. */
    Unavailable,
}

/**
 * "광고 시청으로 프리미엄 활성화" 시도의 결과. [RewardEarned]일 때만 프리미엄을 활성화해야
 * 한다 — 그 외에는 전부 일반 모드를 유지하고 사용자에게 안내해야 하는 실패로 취급한다
 * ([runPremiumAdGrantApplication]이 이 값을 상태 전이로 변환한다).
 */
sealed interface AdRewardOutcome {
    data object RewardEarned : AdRewardOutcome

    data class NotRewarded(
        val reason: AdRewardFailureReason,
        val detail: String? = null,
    ) : AdRewardOutcome
}
