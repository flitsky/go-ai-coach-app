package com.worksoc.goaicoach.application.premium

/**
 * **결제가 끝나지 않았을 때 사용자에게 무엇을 말할 것인가**(백로그 #178).
 *
 * ## 왜 갈랐는가
 * 2026-09-19까지 실패는 **한 문장**이었다 — *"구매가 완료되지 않아 프리미엄이 활성화되지
 * 않았습니다."* 그런데 그 한 문장이 성격이 전혀 다른 셋을 덮고 있었다:
 *
 * - **사용자가 스스로 취소했다** — ⚠️ **의도한 행동인데 오류처럼 안내한다.** 결제 시트를 열었다가
 *   닫은 사람에게 빨간 글씨가 뜬다. 가장 흔한 경우이면서 가장 잘못된 안내다.
 * - **결제를 이용할 수 없다** — 사용자가 한 일이 없다. *"구매가 완료되지 않았다"* 는 마치 시도가
 *   실패한 것처럼 들리지만, 실제로는 **시작조차 못 했다.**
 * - **결제가 처리 중이다**(계좌이체 등) — ⚠️ **실패가 아니다.** 나중에 확정되는데 실패라고
 *   말하면 사용자가 **한 번 더 결제한다.**
 *
 * ⚠️ **이 판정을 화면에서 하지 말 것** — 사유는 플랫폼이 주는 값이고 문구는 언어마다 다르다.
 * 둘 사이의 규칙만 여기 순수 함수로 둔다.
 */
enum class PurchaseFailureNotice {
    /**
     * **아무 말도 하지 않는다.** 사용자가 스스로 취소한 경우 — 의도한 결과에 안내를 붙이면 소음이다.
     * ⚠️ 여기에 "다시 시도" 같은 권유를 넣지 말 것. 방금 스스로 그만둔 사람에게 다시 권하는 꼴이다.
     */
    Silent,

    /** 결제 수단 처리 중 — **실패가 아니다.** 중복 결제를 막으려면 이 말이 반드시 달라야 한다. */
    Pending,

    /** 지금은 결제를 이용할 수 없다 — 사용자가 한 일이 없고, 사용자가 고칠 것도 없다. */
    Unavailable,

    /** 그 밖의 실패 — 다시 시도할 만하다. */
    Failed,
}

/**
 * [PurchaseOutcome]이 사용자에게 무엇을 말해야 하는지 정한다. 성공([PurchaseOutcome.Purchased])이면
 * 말할 것이 없으므로 `null`.
 */
fun PurchaseOutcome.failureNotice(): PurchaseFailureNotice? = when (this) {
    PurchaseOutcome.Purchased -> null
    is PurchaseOutcome.NotPurchased -> when (reason) {
        PurchaseFailureReason.UserCancelled -> PurchaseFailureNotice.Silent
        PurchaseFailureReason.Pending -> PurchaseFailureNotice.Pending
        // ⚠️ 셋 다 **사용자가 한 일이 없는** 경우다 — 결제 자체를 시작할 수 없었다.
        //   `ProductUnavailable`은 우리 콘솔 설정 문제이지 사용자 문제가 아니다.
        PurchaseFailureReason.BillingUnavailable,
        PurchaseFailureReason.ProductUnavailable,
        PurchaseFailureReason.Unavailable,
        -> PurchaseFailureNotice.Unavailable
        // ⚠️ 아래 둘은 **복원 조회**의 답이라 구매 경로에서는 오지 않는다. 그래도 분기를 비워 두지
        //   않는 이유는, 언젠가 오게 됐을 때 조용히 사라지는 것보다 "실패"라고 말하는 편이 낫기 때문이다.
        PurchaseFailureReason.NotFound,
        PurchaseFailureReason.OwnershipUnknown,
        PurchaseFailureReason.PurchaseError,
        -> PurchaseFailureNotice.Failed
    }
}
