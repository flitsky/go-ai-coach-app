package com.worksoc.goaicoach.application.premium

/**
 * 4계층(External Integration) α — 실제 Google Play Billing SDK 호출을 감싸는 순수 포트.
 * 실제 어댑터는 `ui/AndroidBillingClient.kt`(플랫폼 계층)에 둔다 — `AdRewardPort`/`AuthClientPort`와
 * 같은 자리(PREMIUM_MODE.md "계층 배치 참고" 표). 구매를 진행할 Activity/상품 ID 같은
 * 플랫폼 세부사항은 이 인터페이스의 메서드 시그니처가 아니라 어댑터의 생성자가 받는다 —
 * `AndroidRewardedInterstitialAdClient`가 같은 이유로 Activity를 시그니처에 노출하지 않는 것과 동일하다.
 */
interface PurchasePort {
    /** 구매 플로우를 실제로 띄우고, 완료(또는 취소/실패) 결과를 반환한다. */
    suspend fun purchasePremium(): PurchaseOutcome

    /** 앱 시작 시 이미 소유 중인 구매가 있는지 조회한다 — 재설치 등으로 로컬 상태가 사라진 경우 복원용. */
    suspend fun restorePurchases(): PurchaseOutcome
}

/** [PurchaseOutcome.NotPurchased]가 발생한 원인 — 진단 로그에서 구분하기 위함이다. */
enum class PurchaseFailureReason {
    /** 사용자가 구매 플로우를 취소함. */
    UserCancelled,

    /** Play Billing 연결 자체에 실패함(서비스 접근 불가 등). */
    BillingUnavailable,

    /** 상품 정보를 가져오지 못함(콘솔 미등록/비활성 등). */
    ProductUnavailable,

    /** 결제 수단 처리 중이라 아직 확정되지 않음(계좌이체 등) — 다음 복원 조회 시 다시 확인된다. */
    Pending,

    /** Play Billing이 반환한 그 외 오류(응답 코드/디버그 메시지는 detail에 담김). */
    PurchaseError,

    /**
     * 복원 조회 결과 **Play가 "소유하고 있지 않다"고 답함** — 대부분의 사용자에게 정상적인 기본 상태.
     *
     * ⚠️ **이것만이 권위 있는 "미소유"다.** 조회가 실패했을 때는 [OwnershipUnknown]이고, 둘을
     * 합치면 안 된다 — 구독 강등(#26)이 정확히 이 구분에 걸려 있다([isAuthoritativeNotOwned]).
     */
    NotFound,

    /**
     * **소유 여부를 확인하지 못함** — 조회 자체가 오류로 끝났다(네트워크·Play 서비스 문제 등).
     *
     * ⚠️ **[NotFound]와 절대 합치지 말 것.** 예전에는 어댑터가 둘을 똑같이 `null`로 뭉개
     * [NotFound]로 보고했다. 영구 구매에서는 복원이 강등을 하지 않으므로 무해했지만,
     * **구독에서 강등을 켜는 순간 일시적 네트워크 오류가 유료 구독자의 접근권을 박탈한다**
     * (#26이 착수 순서 1번으로 지목한 결함). 지금은 진단 로그가 거짓을 적는 문제로만 드러난다.
     */
    OwnershipUnknown,

    /** 구매를 진행할 Activity를 확보하지 못하는 등, SDK 호출 자체를 시도할 수 없었던 경우. */
    Unavailable,
}

/** "영구 구매" 시도(또는 복원 조회)의 결과. [Purchased]일 때만 프리미엄을 영구 활성화해야 한다. */
sealed interface PurchaseOutcome {
    data object Purchased : PurchaseOutcome

    data class NotPurchased(
        val reason: PurchaseFailureReason,
        val detail: String? = null,
    ) : PurchaseOutcome
}
