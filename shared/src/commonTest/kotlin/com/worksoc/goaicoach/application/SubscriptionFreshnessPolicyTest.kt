package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.premium.state.SubscriptionFreshness
import com.worksoc.goaicoach.application.premium.state.SubscriptionFreshnessPolicy
import com.worksoc.goaicoach.application.premium.state.SubscriptionFreshnessPolicy.ExpireAfterMillis
import com.worksoc.goaicoach.application.premium.state.SubscriptionFreshnessPolicy.RecheckAfterMillis
import kotlin.test.Test
import kotlin.test.assertEquals

/** 구독 확인 신선도 판정 계약(백로그 #174). */
class SubscriptionFreshnessPolicyTest {

    private val now = 1_700_000_000_000L

    private fun at(elapsed: Long) =
        SubscriptionFreshnessPolicy.evaluate(lastVerifiedAtMillis = now - elapsed, nowMillis = now)

    @Test
    fun aRecentCheckIsTrustedWithoutAskingAgain() {
        assertEquals(SubscriptionFreshness.Fresh, at(0))
        assertEquals(SubscriptionFreshness.Fresh, at(RecheckAfterMillis - 1))
    }

    @Test
    fun afterTwentyFourHoursWeAskAgainButDoNotTakeAnythingAway() {
        assertEquals(SubscriptionFreshness.NeedsRecheck, at(RecheckAfterMillis))
        assertEquals(SubscriptionFreshness.NeedsRecheck, at(ExpireAfterMillis - 1))
    }

    @Test
    fun afterSeventyTwoHoursWithoutASuccessfulCheckTheAnswerIsNoLongerTrusted() {
        assertEquals(SubscriptionFreshness.Expired, at(ExpireAfterMillis))
        assertEquals(SubscriptionFreshness.Expired, at(ExpireAfterMillis * 10))
    }

    /**
     * ⚠️ **이 줄이 없으면 업데이트를 받은 유료 구독자가 앱을 켜자마자 전부 강등된다.**
     * 이 필드는 #174에서 새로 생겨서, 기존 저장값에는 없고 디코더가 `null`을 준다.
     */
    @Test
    fun aUserWhoHasNeverBeenCheckedIsAskedAgainRatherThanDowngraded() {
        assertEquals(
            SubscriptionFreshness.NeedsRecheck,
            SubscriptionFreshnessPolicy.evaluate(lastVerifiedAtMillis = null, nowMillis = now),
        )
    }

    /**
     * ⚠️ **함정 12** — 이 앱은 `AppClock`을 쓰지 않고 `System.currentTimeMillis`를 직접 읽는다.
     * 시계를 앞당겼다 되돌리면 "확인 시각이 미래"가 되어 경과가 음수가 되는데, 그대로 두면
     * **그 조작만으로 72시간을 영영 피한다.**
     */
    @Test
    fun aClockWoundBackwardsCountsAsStaleInsteadOfForeverFresh() {
        assertEquals(
            SubscriptionFreshness.NeedsRecheck,
            SubscriptionFreshnessPolicy.evaluate(
                lastVerifiedAtMillis = now + ExpireAfterMillis,
                nowMillis = now,
            ),
        )
    }
}
