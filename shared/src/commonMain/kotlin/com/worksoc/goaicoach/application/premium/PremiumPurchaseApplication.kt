package com.worksoc.goaicoach.application.premium

import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity

/**
 * 이 구매 결과가 사용자의 명시적 구매 시도([Explicit])에서 왔는지, 앱 시작 시 자동 복원
 * 조회([Restore])에서 왔는지 — 상태 전이 로직 자체는 동일하지만, 진단 로그의 code/severity를
 * 의미 있게 구분하기 위해서만 쓰인다(예: 복원 조회에서 "소유한 구매 없음"은 대부분의 사용자에게
 * 정상적인 기본 상태라 Warning이 아니라 Info로 남긴다).
 */
enum class PurchaseTrigger {
    Explicit,
    Restore,
}

data class PremiumPurchaseRunRequest(
    val outcome: PurchaseOutcome,
    val trigger: PurchaseTrigger,
    val nowMillis: Long,
    /**
     * 지금 로컬에 있는 상태 — **강등 판정에 필요하다**(백로그 #158).
     *
     * 강등은 [PremiumSource.Purchase]일 때만 뜻이 있다. 광고 부여는 스스로 시간으로 만료되고,
     * [PremiumSource.None]은 내릴 것이 없다. 그리고 [PremiumState.claimedFeatures]는
     * **강등해도 남아야 한다** — 출석으로 받은 영구 클레임은 구독과 무관하다.
     */
    val currentState: PremiumState = PremiumState(),
)

/**
 * [nextState]가 `null`이면 상태를 바꾸지 말라는 뜻이다(호출부가 premiumState/저장소 쓰기를
 * 건너뛰도록).
 *
 * ⚠️ **2026-09-18에 이 계약이 한쪽만 뒤집혔다**(백로그 #158). 예전에는 *"Play가 미소유라고 해도
 * 되돌리지 않는다"* 가 전부였다 — 영구 구매에서는 그것이 옳았다. **구독에서는 아니다**: 해지·
 * 만료·계정 보류가 정상 흐름이라 되잠그지 않으면 한 번 결제한 사람이 영원히 프리미엄이 된다.
 *
 * 그래서 지금은 **권위 있는 미소유**([isAuthoritativeNotOwned])에서만 강등한다. *"확인하지
 * 못했다"*(`OwnershipUnknown`·`BillingUnavailable`)는 **여전히 되돌리지 않는다** — 일시적인
 * 네트워크 응답 하나로 결제한 사용자의 접근권을 빼앗는 위험은 그대로이기 때문이다.
 */
data class PremiumPurchaseRunResult(
    val nextState: PremiumState?,
    val diagnosticEvent: DiagnosticEvent,
)

/**
 * 6계층(Session & Continuity)/App Service 경계 — 구매 결과([PurchaseOutcome])를 프리미엄 상태
 * 전이 + 진단 로그로 변환하는 순수 함수. [PurchaseOutcome.Purchased]일 때만
 * [PremiumState.purchased]로 전이하고, 그 외에는 상태를 바꾸지 않는다 — 실패/취소/미소유 시
 * 일반 모드(또는 기존 상태) 유지는 호출부가 별도로 신경 쓸 필요 없이 이 함수의 반환값
 * (`nextState == null`)만으로 보장된다.
 */
fun runPremiumPurchaseApplication(request: PremiumPurchaseRunRequest): PremiumPurchaseRunResult =
    when (val outcome = request.outcome) {
        PurchaseOutcome.Purchased -> {
            val nextState = PremiumState.purchased()
            val isRestore = request.trigger == PurchaseTrigger.Restore
            PremiumPurchaseRunResult(
                nextState = nextState,
                diagnosticEvent = DiagnosticEvent(
                    severity = DiagnosticSeverity.Info,
                    code = if (isRestore) "premium_purchase_restored" else "premium_purchase_activated",
                    message = if (isRestore) {
                        "Existing premium purchase restored."
                    } else {
                        "Premium purchase activated."
                    },
                    context = mapOf("trigger" to request.trigger.name),
                ),
            )
        }

        is PurchaseOutcome.NotPurchased -> {
            // ⚠️ **"미소유"와 "확인 못 함"을 여기서 가른다**(#26 착수 순서 1번). 예전에는 복원
            // 실패가 무엇이든 `restore_not_found`("소유한 구매 없음")로 기록돼 **로그가 거짓을
            // 적었다** — 조회가 오류로 끝났을 뿐인데 미소유라고 단정한 셈이다.
            val isAuthoritativeNotOwned = outcome.isAuthoritativeNotOwned(request.trigger)
            val isRestore = request.trigger == PurchaseTrigger.Restore
            // ⚠️ **구독 강등은 여기, 그리고 여기서만 일어난다**(백로그 #158).
            //
            // 영구 구매 시절에는 강등이 아예 없었다 — 한 번 사면 끝이라 "미소유"는 환불뿐이었고,
            // 일시적 네트워크 오류로 접근권을 뺏는 위험이 더 컸다. **구독은 정반대다**: 해지·만료·
            // 계정 보류가 정상 흐름이라, 되잠그지 않으면 **한 번 결제한 사람이 영원히 프리미엄**이다.
            //
            // ⚠️ **관문은 [isAuthoritativeNotOwned] 하나다.** `OwnershipUnknown`(조회 실패)이나
            // `BillingUnavailable`에 강등을 걸면 **네트워크 한 번 끊긴 것이 유료 구독자의 접근권을
            // 박탈한다.** 그 구분이 이 함수보다 먼저 만들어져 있던 이유가 이것이다.
            //
            // ⚠️ **`claimedFeatures`는 남긴다** — 출석 3일차로 받은 무르기 같은 영구 클레임은
            // 구독과 다른 축이다(`PremiumState.claimedFeatures` KDoc). `copy`가 그것을 지킨다.
            // ⚠️ **광고 부여는 건드리지 않는다** — 스스로 시간으로 만료되고, 구독 조회가 광고
            //   1시간을 끊을 이유가 없다.
            val downgraded = isAuthoritativeNotOwned &&
                request.currentState.source == PremiumSource.Purchase
            PremiumPurchaseRunResult(
                nextState = if (downgraded) {
                    request.currentState.copy(source = PremiumSource.None)
                } else {
                    null
                },
                diagnosticEvent = DiagnosticEvent(
                    // 미소유는 대부분의 사용자에게 정상이라 Info, 확인 실패는 Warning이다.
                    severity = if (isAuthoritativeNotOwned) DiagnosticSeverity.Info else DiagnosticSeverity.Warning,
                    code = when {
                        downgraded -> "premium_subscription_downgraded"
                        isAuthoritativeNotOwned -> "premium_purchase_restore_not_found"
                        isRestore -> "premium_purchase_restore_unverified"
                        else -> "premium_purchase_not_completed"
                    },
                    message = when {
                        isAuthoritativeNotOwned ->
                            "No active purchase found on restore check; premium state unchanged."
                        isRestore ->
                            "Could not verify purchase ownership; premium state left as-is."
                        else -> "Purchase did not complete; premium not activated."
                    },
                    context = mapOf(
                        "trigger" to request.trigger.name,
                        "reason" to outcome.reason.name,
                        "detail" to (outcome.detail ?: ""),
                    ),
                ),
            )
        }
    }

/**
 * 이 결과가 **"Play가 미소유라고 답했다"** 는 뜻인가.
 *
 * ⚠️ **구독 강등은 오직 이것이 참일 때만 해도 된다**(#26 착수 순서 1번의 존재 이유).
 * [PurchaseFailureReason.OwnershipUnknown]·[PurchaseFailureReason.BillingUnavailable]처럼
 * **"확인하지 못했다"** 는 결과에 강등을 걸면, 일시적 네트워크 오류가 유료 구독자의 접근권을
 * 박탈한다. 지금은 진단 로그의 심각도·코드를 가르는 데만 쓰이지만, 강등 로직이 들어올 때
 * **이 함수가 유일한 관문**이 되어야 한다.
 *
 * 구매 트리거([PurchaseTrigger.Purchase])에서는 언제나 거짓이다 — 구매를 시도한 결과는
 * "소유 여부 조회"가 아니다.
 */
fun PurchaseOutcome.isAuthoritativeNotOwned(trigger: PurchaseTrigger): Boolean =
    trigger == PurchaseTrigger.Restore &&
        this is PurchaseOutcome.NotPurchased &&
        reason == PurchaseFailureReason.NotFound
