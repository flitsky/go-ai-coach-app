package com.worksoc.goaicoach.application.premium.app


import com.worksoc.goaicoach.application.premium.port.AdRewardOutcome
import com.worksoc.goaicoach.application.premium.port.PurchaseFailureReason
import com.worksoc.goaicoach.application.premium.port.PurchaseOutcome
import com.worksoc.goaicoach.application.premium.state.FeatureId
import com.worksoc.goaicoach.application.premium.state.PremiumSource
import com.worksoc.goaicoach.application.premium.state.PremiumState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 구독 전환 코어(백로그 #158) — **되잠기되, 함부로 잠그지 않는다.**
 *
 * ## ⚠️ 왜 이 방향이 뒤집혔나
 * 영구 구매에서는 강등이 아예 없는 것이 옳았다 — 한 번 사면 끝이라 "미소유"는 환불뿐이었고,
 * 일시적 네트워크 오류로 접근권을 뺏는 위험이 더 컸다. **구독은 정반대다**: 해지·만료·계정
 * 보류가 정상 흐름이라, 되잠그지 않으면 **한 번 결제한 사람이 영원히 프리미엄**이다.
 *
 * 그래서 이 파일이 지키는 것은 **두 방향**이다. 한쪽만 지키면 다른 쪽이 사고가 된다.
 */
class SubscriptionDowngradeTest {

    private val subscribed = PremiumState(
        source = PremiumSource.Purchase,
        claimedFeatures = setOf(FeatureId.Undo),
    )

    private fun restore(outcome: PurchaseOutcome, current: PremiumState) =
        runPremiumPurchaseApplication(
            PremiumPurchaseRunRequest(
                outcome = outcome,
                trigger = PurchaseTrigger.Restore,
                nowMillis = 1_000L,
                currentState = current,
            ),
        )

    // ── 되잠근다 ────────────────────────────────────────────────────────────────

    @Test
    fun anAuthoritativeNotOwnedDowngradesALiveSubscription() {
        val result = restore(PurchaseOutcome.NotPurchased(PurchaseFailureReason.NotFound), subscribed)

        assertEquals(
            PremiumSource.None,
            result.nextState?.source,
            "Play가 미소유라고 답했는데 구독이 살아 있다 — 해지해도 영원히 프리미엄이 된다(#158).",
        )
        assertEquals("premium_subscription_downgraded", result.diagnosticEvent.code)
    }

    /**
     * ⚠️ **출석으로 받은 영구 클레임은 구독과 다른 축이다.** 강등이 그것까지 쓸어 가면
     * 구독을 한 번 샀다가 해지한 사용자가 **원래 무료로 받은 무르기까지 잃는다.**
     */
    @Test
    fun theDowngradeKeepsPermanentClaimsThatHaveNothingToDoWithTheSubscription() {
        val result = restore(PurchaseOutcome.NotPurchased(PurchaseFailureReason.NotFound), subscribed)

        assertEquals(
            setOf(FeatureId.Undo),
            result.nextState?.claimedFeatures,
            "강등이 출석으로 받은 영구 클레임까지 지웠다(#158).",
        )
    }

    // ── 함부로 잠그지 않는다 ────────────────────────────────────────────────────

    /**
     * ⚠️ **이 테스트가 이 파일에서 가장 중요하다.** `OwnershipUnknown`은 *"확인하지 못했다"* 이지
     * 미소유가 아니다. 여기에 강등을 걸면 **네트워크가 한 번 끊긴 것이 유료 구독자의 접근권을
     * 박탈한다** — 백로그가 #26 착수 순서 1번으로 지목했던 바로 그 결함이다.
     */
    @Test
    fun aFailedQueryNeverDowngradesAnyone() {
        for (reason in listOf(
            PurchaseFailureReason.OwnershipUnknown,
            PurchaseFailureReason.BillingUnavailable,
            PurchaseFailureReason.Unavailable,
        )) {
            val result = restore(PurchaseOutcome.NotPurchased(reason), subscribed)
            assertNull(
                result.nextState,
                "'$reason'(확인 실패)로 강등했다 — 네트워크 한 번 끊긴 것이 구독자를 쫓아낸다(#158).",
            )
        }
    }

    /** 구매 **시도** 실패는 소유 조회가 아니다 — 여기서 강등하면 결제 취소가 해지가 된다. */
    @Test
    fun aCancelledPurchaseAttemptIsNotAnOwnershipAnswer() {
        val result = runPremiumPurchaseApplication(
            PremiumPurchaseRunRequest(
                outcome = PurchaseOutcome.NotPurchased(PurchaseFailureReason.UserCancelled),
                trigger = PurchaseTrigger.Explicit,
                nowMillis = 1_000L,
                currentState = subscribed,
            ),
        )

        assertNull(result.nextState, "구매를 취소했다고 기존 구독을 내렸다(#158).")
    }

    /** 내릴 것이 없는 사람은 건드리지 않는다 — 광고 1시간은 스스로 만료된다. */
    @Test
    fun thereIsNothingToDowngradeForSomeoneWhoNeverSubscribed() {
        val adUser = PremiumState.adGranted(nowMillis = 1_000L)

        val result = restore(PurchaseOutcome.NotPurchased(PurchaseFailureReason.NotFound), adUser)

        assertNull(result.nextState, "구독한 적 없는 사용자의 광고 1시간을 구독 조회가 끊었다(#158).")
    }

    // ── 광고가 구독을 덮지 않는다 ───────────────────────────────────────────────

    /**
     * ⚠️ [PremiumState.adGranted]는 **새 상태를 만든다.** 그대로 저장하면 `source`가
     * `Purchase` → `AdGrant`로 바뀌어 **구독자가 광고를 본 순간 구독이 1시간짜리로 강등된다.**
     * `saveMergingClaimedFeatures`는 `claimedFeatures`만 되살리므로 이것을 막지 못한다.
     */
    @Test
    fun watchingAnAdNeverDemotesALiveSubscriptionToAnHour() {
        val result = runPremiumAdGrantApplication(
            PremiumAdGrantRunRequest(
                outcome = AdRewardOutcome.RewardEarned(type = "premium", amount = 1),
                nowMillis = 2_000L,
                currentState = subscribed,
            ),
        )

        assertNull(
            result.nextState,
            "구독자가 광고를 보자 상태가 바뀌었다 — Purchase가 AdGrant로 덮이면 강등이다(#158).",
        )
        assertEquals("premium_ad_grant_ignored_active_subscription", result.diagnosticEvent.code)
    }

    /** 구독이 없으면 광고는 예전 그대로 동작한다 — 그 경로를 망가뜨리지 않았다. */
    @Test
    fun withoutASubscriptionTheAdGrantStillWorksAndKeepsClaims() {
        val claimed = PremiumState(claimedFeatures = setOf(FeatureId.Undo))

        val result = runPremiumAdGrantApplication(
            PremiumAdGrantRunRequest(
                outcome = AdRewardOutcome.RewardEarned(type = "premium", amount = 1),
                nowMillis = 2_000L,
                currentState = claimed,
            ),
        )

        assertEquals(PremiumSource.AdGrant, result.nextState?.source)
        assertTrue(result.nextState!!.isActive(nowMillis = 2_000L))
        assertEquals(
            setOf(FeatureId.Undo),
            result.nextState!!.claimedFeatures,
            "광고 부여가 출석으로 받은 영구 클레임을 지웠다.",
        )
    }
}
