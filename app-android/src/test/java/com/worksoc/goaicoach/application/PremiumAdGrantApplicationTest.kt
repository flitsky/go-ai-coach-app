package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.premium.AdRewardFailureReason
import com.worksoc.goaicoach.application.premium.AdRewardOutcome
import com.worksoc.goaicoach.application.premium.PremiumAdGrantRunRequest
import com.worksoc.goaicoach.application.premium.PremiumSource
import com.worksoc.goaicoach.application.premium.runPremiumAdGrantApplication
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PremiumAdGrantApplicationTest {
    @Test
    fun rewardEarnedActivatesAdGrantAtRequestTime() {
        val nowMillis = 1_000_000L

        val result = runPremiumAdGrantApplication(
            PremiumAdGrantRunRequest(outcome = AdRewardOutcome.RewardEarned, nowMillis = nowMillis),
        )

        val nextState = result.nextState
        checkNotNull(nextState)
        assertEquals(PremiumSource.AdGrant, nextState.source)
        assertEquals(nowMillis, nextState.adGrantStartedAtMillis)
        assertEquals(DiagnosticSeverity.Info, result.diagnosticEvent.severity)
        assertEquals("premium_ad_grant_activated", result.diagnosticEvent.code)
        assertEquals(nowMillis.toString(), result.diagnosticEvent.context["adGrantStartedAtMillis"])
    }

    @Test
    fun dismissedWithoutRewardKeepsStateUnchangedAndLogsReason() {
        val result = runPremiumAdGrantApplication(
            PremiumAdGrantRunRequest(
                outcome = AdRewardOutcome.NotRewarded(AdRewardFailureReason.DismissedWithoutReward),
                nowMillis = 1_000_000L,
            ),
        )

        assertNull(result.nextState)
        assertEquals(DiagnosticSeverity.Warning, result.diagnosticEvent.severity)
        assertEquals("premium_ad_grant_not_rewarded", result.diagnosticEvent.code)
        assertEquals("DismissedWithoutReward", result.diagnosticEvent.context["reason"])
    }

    @Test
    fun loadFailedKeepsStateUnchangedAndCarriesDetail() {
        val result = runPremiumAdGrantApplication(
            PremiumAdGrantRunRequest(
                outcome = AdRewardOutcome.NotRewarded(AdRewardFailureReason.LoadFailed, detail = "no fill"),
                nowMillis = 1_000_000L,
            ),
        )

        assertNull(result.nextState)
        assertEquals("LoadFailed", result.diagnosticEvent.context["reason"])
        assertEquals("no fill", result.diagnosticEvent.context["detail"])
    }

    @Test
    fun notRewardedWithoutDetailLogsEmptyDetailInsteadOfNull() {
        val result = runPremiumAdGrantApplication(
            PremiumAdGrantRunRequest(
                outcome = AdRewardOutcome.NotRewarded(AdRewardFailureReason.Unavailable),
                nowMillis = 1_000_000L,
            ),
        )

        assertEquals("", result.diagnosticEvent.context["detail"])
    }
}
