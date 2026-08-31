package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.premium.PremiumPurchaseRunRequest
import com.worksoc.goaicoach.application.premium.PremiumSource
import com.worksoc.goaicoach.application.premium.PurchaseFailureReason
import com.worksoc.goaicoach.application.premium.PurchaseOutcome
import com.worksoc.goaicoach.application.premium.isAuthoritativeNotOwned
import com.worksoc.goaicoach.application.premium.PurchaseTrigger
import com.worksoc.goaicoach.application.premium.runPremiumPurchaseApplication
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class PremiumPurchaseApplicationTest {
    @Test
    fun purchasedActivatesPurchaseSourceOnExplicitTrigger() {
        val result = runPremiumPurchaseApplication(
            PremiumPurchaseRunRequest(
                outcome = PurchaseOutcome.Purchased,
                trigger = PurchaseTrigger.Explicit,
                nowMillis = 1_000_000L,
            ),
        )

        val nextState = result.nextState
        checkNotNull(nextState)
        assertEquals(PremiumSource.Purchase, nextState.source)
        assertEquals(DiagnosticSeverity.Info, result.diagnosticEvent.severity)
        assertEquals("premium_purchase_activated", result.diagnosticEvent.code)
        assertEquals("Explicit", result.diagnosticEvent.context["trigger"])
    }

    @Test
    fun purchasedOnRestoreUsesRestoreDiagnosticCode() {
        val result = runPremiumPurchaseApplication(
            PremiumPurchaseRunRequest(
                outcome = PurchaseOutcome.Purchased,
                trigger = PurchaseTrigger.Restore,
                nowMillis = 1_000_000L,
            ),
        )

        assertEquals(PremiumSource.Purchase, result.nextState?.source)
        assertEquals("premium_purchase_restored", result.diagnosticEvent.code)
    }

    @Test
    fun userCancelledKeepsStateUnchangedAndLogsWarning() {
        val result = runPremiumPurchaseApplication(
            PremiumPurchaseRunRequest(
                outcome = PurchaseOutcome.NotPurchased(PurchaseFailureReason.UserCancelled),
                trigger = PurchaseTrigger.Explicit,
                nowMillis = 1_000_000L,
            ),
        )

        assertNull(result.nextState)
        assertEquals(DiagnosticSeverity.Warning, result.diagnosticEvent.severity)
        assertEquals("premium_purchase_not_completed", result.diagnosticEvent.code)
        assertEquals("UserCancelled", result.diagnosticEvent.context["reason"])
    }

    @Test
    fun restoreNotFoundKeepsStateUnchangedAndLogsInfoInsteadOfWarning() {
        val result = runPremiumPurchaseApplication(
            PremiumPurchaseRunRequest(
                outcome = PurchaseOutcome.NotPurchased(PurchaseFailureReason.NotFound),
                trigger = PurchaseTrigger.Restore,
                nowMillis = 1_000_000L,
            ),
        )

        assertNull(result.nextState)
        assertEquals(DiagnosticSeverity.Info, result.diagnosticEvent.severity)
        assertEquals("premium_purchase_restore_not_found", result.diagnosticEvent.code)
    }

    /**
     * ⚠️ **#26 착수 순서 1번의 본체다.** 조회가 오류로 끝난 것을 *"소유한 구매 없음"* 으로 적으면
     * 로그가 거짓을 말하고, **강등을 켜는 순간 그 거짓이 유료 구독자의 접근권 박탈이 된다.**
     * 예전 어댑터가 두 경우를 똑같이 `null`로 뭉갠 탓에 이 구분이 아예 표현되지 않았다.
     */
    @Test
    fun aRestoreThatCouldNotVerifyIsNeverReportedAsNotOwned() {
        val result = runPremiumPurchaseApplication(
            PremiumPurchaseRunRequest(
                outcome = PurchaseOutcome.NotPurchased(PurchaseFailureReason.OwnershipUnknown, "network down"),
                trigger = PurchaseTrigger.Restore,
                nowMillis = 1_000_000L,
            ),
        )

        assertNull(result.nextState)
        assertEquals(DiagnosticSeverity.Warning, result.diagnosticEvent.severity)
        assertEquals("premium_purchase_restore_unverified", result.diagnosticEvent.code)
        // "없음"이라고 말하지 않는다 — 이 문장이 로그의 거짓을 막는다.
        assertEquals("network down", result.diagnosticEvent.context["detail"])
    }

    /**
     * ⚠️ **강등의 유일한 관문**이 정확히 한 조합에서만 열리는지 본다 —
     * `Restore` × `NotFound`. 여기가 넓어지면 확인 실패에도 강등이 걸린다.
     */
    @Test
    fun theDowngradeGateOpensOnlyForAnAuthoritativeNotOwned() {
        PurchaseFailureReason.entries.forEach { reason ->
            val expected = reason == PurchaseFailureReason.NotFound
            assertEquals(
                PurchaseOutcome.NotPurchased(reason).isAuthoritativeNotOwned(PurchaseTrigger.Restore),
                expected,
                "Restore x $reason",
            )
            // 구매 시도의 결과는 "소유 여부 조회"가 아니다 — 어떤 사유에서도 열리지 않는다.
            assertEquals(
                PurchaseOutcome.NotPurchased(reason).isAuthoritativeNotOwned(PurchaseTrigger.Explicit),
                false,
                "Explicit x $reason",
            )
        }
        // 성공한 복원도 "미소유"가 아니다.
        assertEquals(PurchaseOutcome.Purchased.isAuthoritativeNotOwned(PurchaseTrigger.Restore), false)
    }

    @Test
    fun restoreDoesNotDowngradeOnNonNotFoundFailureEither() {
        val result = runPremiumPurchaseApplication(
            PremiumPurchaseRunRequest(
                outcome = PurchaseOutcome.NotPurchased(PurchaseFailureReason.BillingUnavailable),
                trigger = PurchaseTrigger.Restore,
                nowMillis = 1_000_000L,
            ),
        )

        assertNull(result.nextState)
        assertEquals(DiagnosticSeverity.Warning, result.diagnosticEvent.severity)
    }

    @Test
    fun notPurchasedWithoutDetailLogsEmptyDetailInsteadOfNull() {
        val result = runPremiumPurchaseApplication(
            PremiumPurchaseRunRequest(
                outcome = PurchaseOutcome.NotPurchased(PurchaseFailureReason.BillingUnavailable),
                trigger = PurchaseTrigger.Explicit,
                nowMillis = 1_000_000L,
            ),
        )

        assertEquals("", result.diagnosticEvent.context["detail"])
    }
}
