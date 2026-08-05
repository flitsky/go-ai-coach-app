package com.worksoc.goaicoach.application.premium

import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity

/**
 * 이 구매 결과가 사용자의 명시적 구매 시도([Explicit])에서 왔는지, 앱 시작 시 자동 복원
 * 조회([Restore])에서 왔는지 — 상태 전이 로직 자체는 동일하지만, 진단 로그의 code/severity를
 * 의미 있게 구분하기 위해서만 쓰인다(예: 복원 조회에서 "소유한 구매 없음"은 대부분의 사용자에게
 * 정상적인 기본 상태라 Warning이 아니라 Info로 남긴다).
 */
internal enum class PurchaseTrigger {
    Explicit,
    Restore,
}

internal data class PremiumPurchaseRunRequest(
    val outcome: PurchaseOutcome,
    val trigger: PurchaseTrigger,
    val nowMillis: Long,
)

/**
 * [nextState]가 `null`이면 상태를 바꾸지 말라는 뜻이다(호출부가 premiumState/저장소 쓰기를
 * 건너뛰도록). 특히 [PurchaseTrigger.Restore]에서 Play가 "소유한 구매 없음"을 반환했다고 해서
 * 로컬에 이미 있던 [PremiumSource.Purchase] 상태를 되돌리지는 않는다 — 일시적인 네트워크
 * 응답 하나로 결제한 사용자의 접근권을 조용히 빼앗는 위험이, 실제로는 드문 환불 케이스가
 * 잠시 더 유지되는 위험보다 크다고 판단한 보수적 선택이다(premium-mode/README.md Step 4 참고).
 */
internal data class PremiumPurchaseRunResult(
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
internal fun runPremiumPurchaseApplication(request: PremiumPurchaseRunRequest): PremiumPurchaseRunResult =
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
            val isRestoreNotFound = request.trigger == PurchaseTrigger.Restore && outcome.reason == PurchaseFailureReason.NotFound
            PremiumPurchaseRunResult(
                nextState = null,
                diagnosticEvent = DiagnosticEvent(
                    severity = if (isRestoreNotFound) DiagnosticSeverity.Info else DiagnosticSeverity.Warning,
                    code = if (request.trigger == PurchaseTrigger.Restore) {
                        "premium_purchase_restore_not_found"
                    } else {
                        "premium_purchase_not_completed"
                    },
                    message = if (request.trigger == PurchaseTrigger.Restore) {
                        "No active purchase found on restore check; premium state unchanged."
                    } else {
                        "Purchase did not complete; premium not activated."
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
