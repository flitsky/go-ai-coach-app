package com.worksoc.goaicoach.application.premium

/**
 * 4계층(External Integration) α — 실제 Google Play Billing SDK 호출을 감싸는 순수 포트.
 * 실제 어댑터는 `ui/AndroidBillingClient.kt`(플랫폼 계층)에 둔다 — `AdRewardPort`/`AuthClientPort`와
 * 같은 자리(premium-mode/README.md "계층 배치 참고" 표). 구매를 진행할 Activity/상품 ID 같은
 * 플랫폼 세부사항은 이 인터페이스의 메서드 시그니처가 아니라 어댑터의 생성자가 받는다 —
 * `AndroidRewardedInterstitialAdClient`가 같은 이유로 Activity를 시그니처에 노출하지 않는 것과 동일하다.
 */
internal interface PurchasePort {
    /** 구매 플로우를 실제로 띄우고, 완료(또는 취소/실패) 결과를 반환한다. */
    suspend fun purchasePremium(): PurchaseOutcome

    /** 앱 시작 시 이미 소유 중인 구매가 있는지 조회한다 — 재설치 등으로 로컬 상태가 사라진 경우 복원용. */
    suspend fun restorePurchases(): PurchaseOutcome
}

/** [PurchaseOutcome.NotPurchased]가 발생한 원인 — 진단 로그에서 구분하기 위함이다. */
internal enum class PurchaseFailureReason {
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

    /** 복원 조회 결과 소유 중인 구매가 없음 — 대부분의 사용자에게 정상적인 기본 상태. */
    NotFound,

    /** 구매를 진행할 Activity를 확보하지 못하는 등, SDK 호출 자체를 시도할 수 없었던 경우. */
    Unavailable,
}

/** "영구 구매" 시도(또는 복원 조회)의 결과. [Purchased]일 때만 프리미엄을 영구 활성화해야 한다. */
internal sealed interface PurchaseOutcome {
    data object Purchased : PurchaseOutcome

    data class NotPurchased(
        val reason: PurchaseFailureReason,
        val detail: String? = null,
    ) : PurchaseOutcome
}
