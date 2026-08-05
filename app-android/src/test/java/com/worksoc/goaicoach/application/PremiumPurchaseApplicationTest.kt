package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.premium.PremiumPurchaseRunRequest
import com.worksoc.goaicoach.application.premium.PremiumSource
import com.worksoc.goaicoach.application.premium.PurchaseFailureReason
import com.worksoc.goaicoach.application.premium.PurchaseOutcome
import com.worksoc.goaicoach.application.premium.PurchaseTrigger
import com.worksoc.goaicoach.application.premium.runPremiumPurchaseApplication
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
