package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.PremiumSource
import com.worksoc.goaicoach.application.premium.PremiumState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class PremiumStateTest {
    @Test
    fun defaultStateIsNotActive() {
        val state = PremiumState()

        assertFalse(state.isActive(nowMillis = 0L))
        assertTrue(state.source == PremiumSource.None)
    }

    @Test
    fun purchasedStateIsAlwaysActiveRegardlessOfTime() {
        val state = PremiumState.purchased()

        assertTrue(state.isActive(nowMillis = 0L))
        assertTrue(state.isActive(nowMillis = Long.MAX_VALUE))
    }

    @Test
    fun adGrantedStateIsActiveWithinOneHour() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(nowMillis = grantedAt)

        assertTrue(state.isActive(nowMillis = grantedAt))
        assertTrue(state.isActive(nowMillis = grantedAt + PremiumState.AdGrantDurationMillis - 1))
    }

    @Test
    fun adGrantedStateExpiresAfterOneHour() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(nowMillis = grantedAt)

        assertFalse(state.isActive(nowMillis = grantedAt + PremiumState.AdGrantDurationMillis))
        assertFalse(state.isActive(nowMillis = grantedAt + PremiumState.AdGrantDurationMillis + 60_000L))
    }

    @Test
    fun adGrantedStateCarriesOverAcrossMultipleMatchesWithinTheHour() {
        // 결정사항: 광고 시청 프리미엄은 특정 대국에 묶이지 않는다 — 부여 시점부터 1시간
        // 동안은 그 사이에 대국을 몇 판을 새로 시작하든(무르기/새 대국 등) 계속 유효하다.
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(nowMillis = grantedAt)

        assertTrue(state.isActive(nowMillis = grantedAt + 10 * 60_000L))
        assertTrue(state.isActive(nowMillis = grantedAt + 40 * 60_000L))
    }

    @Test
    fun defaultAndPurchasedStatesAreAlwaysClockPlausible() {
        assertTrue(PremiumState().isClockPlausibleAt(nowMillis = 0L))
        assertTrue(PremiumState.purchased().isClockPlausibleAt(nowMillis = 0L))
    }

    @Test
    fun adGrantedStateIsClockPlausibleWhenStartedAtOrBeforeNow() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(nowMillis = grantedAt)

        assertTrue(state.isClockPlausibleAt(nowMillis = grantedAt))
        assertTrue(state.isClockPlausibleAt(nowMillis = grantedAt + 1))
    }

    @Test
    fun adGrantedStateIsNotClockPlausibleWhenStartedInTheFuture() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(nowMillis = grantedAt)

        // 저장소에서 읽어온 시점의 now가 부여 시각보다 과거라면(기기 시계 되돌림/손상),
        // isActive의 경과시간 계산이 음수가 되어 영영 만료되지 않는 오판을 막아야 한다.
        assertFalse(state.isClockPlausibleAt(nowMillis = grantedAt - 1))
    }

    @Test
    fun claimedFeaturesIsIndependentOfActiveAndClockPlausibleJudgement() {
        // claimedFeatures는 초도 발행 그랜드파더링(GOOGLE_PLAY_LAUNCH_PLAN.md 3장) 전용 축이라,
        // source/adGrantStartedAtMillis 기반 판정(isActive/isClockPlausibleAt)에 영향을
        // 주면 안 된다 — 클레임만 하고 프리미엄은 없는 상태가 정상적으로 존재해야 한다.
        val state = PremiumState(claimedFeatures = setOf(FeatureId.Undo))

        assertFalse(state.isActive(nowMillis = 0L))
        assertTrue(state.isClockPlausibleAt(nowMillis = 0L))
    }

    @Test
    fun claimedFeaturesSurvivesSourceTransition() {
        // ui/GoCoachApp.kt의 세 전이 지점(setPurchased/purchasePremium/activateAdGrant)이
        // 모두 .copy(claimedFeatures = ...)로 이어붙이는 패턴을 그대로 검증한다 — 새 source로
        // 전이해도 클레임 원장이 조용히 사라지면 안 된다.
        val claimed = PremiumState(claimedFeatures = setOf(FeatureId.Undo))

        val afterPurchase = PremiumState.purchased().copy(claimedFeatures = claimed.claimedFeatures)

        assertEquals(setOf(FeatureId.Undo), afterPurchase.claimedFeatures)
        assertTrue(afterPurchase.isActive(nowMillis = 0L))
    }
}
