package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.premium.PremiumSource
import com.worksoc.goaicoach.application.premium.PremiumState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumStateTest {
    @Test
    fun defaultStateIsNotActive() {
        val state = PremiumState()

        assertFalse(state.isActive(currentMatchGeneration = 1L, nowMillis = 0L))
        assertTrue(state.source == PremiumSource.None)
    }

    @Test
    fun purchasedStateIsAlwaysActiveRegardlessOfMatchOrTime() {
        val state = PremiumState.purchased()

        assertTrue(state.isActive(currentMatchGeneration = 1L, nowMillis = 0L))
        assertTrue(state.isActive(currentMatchGeneration = 99L, nowMillis = Long.MAX_VALUE))
    }

    @Test
    fun adGrantedStateIsActiveWithinOneHourForSameMatch() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(matchGeneration = 5L, nowMillis = grantedAt)

        assertTrue(state.isActive(currentMatchGeneration = 5L, nowMillis = grantedAt))
        assertTrue(
            state.isActive(
                currentMatchGeneration = 5L,
                nowMillis = grantedAt + PremiumState.AdGrantDurationMillis - 1,
            ),
        )
    }

    @Test
    fun adGrantedStateExpiresAfterOneHour() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(matchGeneration = 5L, nowMillis = grantedAt)

        assertFalse(
            state.isActive(
                currentMatchGeneration = 5L,
                nowMillis = grantedAt + PremiumState.AdGrantDurationMillis,
            ),
        )
        assertFalse(
            state.isActive(
                currentMatchGeneration = 5L,
                nowMillis = grantedAt + PremiumState.AdGrantDurationMillis + 60_000L,
            ),
        )
    }

    @Test
    fun adGrantedStateDoesNotCarryOverToNewMatch() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(matchGeneration = 5L, nowMillis = grantedAt)

        // 새 대국이 시작되어 matchGeneration이 바뀌면(예: 6L), 시간이 남아 있어도 무효.
        assertFalse(state.isActive(currentMatchGeneration = 6L, nowMillis = grantedAt))
    }

    @Test
    fun adGrantedStateWithNullMatchIsNotActiveUntilBound() {
        // 홈 화면 등 대국 시작 전에 활성화된 경우, 아직 어느 매치에도 묶이지 않은 상태.
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(matchGeneration = null, nowMillis = grantedAt)

        assertFalse(state.isActive(currentMatchGeneration = 1L, nowMillis = grantedAt))
        assertFalse(state.isActive(currentMatchGeneration = 999L, nowMillis = grantedAt))
    }

    @Test
    fun bindToMatchIfPendingBindsUnboundGrantToStartedMatch() {
        val grantedAt = 1_000_000L
        val pending = PremiumState.adGranted(matchGeneration = null, nowMillis = grantedAt)

        val bound = pending.bindToMatchIfPending(matchGeneration = 7L)

        assertTrue(bound.isActive(currentMatchGeneration = 7L, nowMillis = grantedAt))
        assertFalse(bound.isActive(currentMatchGeneration = 8L, nowMillis = grantedAt))
    }

    @Test
    fun bindToMatchIfPendingDoesNotOverwriteAlreadyBoundGrant() {
        val grantedAt = 1_000_000L
        val bound = PremiumState.adGranted(matchGeneration = 5L, nowMillis = grantedAt)

        // 이미 5L에 묶여 있는데 새 대국(6L)이 또 시작돼도, 다른 매치로 재바인딩하지 않는다
        // (그 결과 5L 전용으로 남아 6L에서는 자연히 무효 판정된다).
        val result = bound.bindToMatchIfPending(matchGeneration = 6L)

        assertFalse(result.isActive(currentMatchGeneration = 6L, nowMillis = grantedAt))
        assertTrue(result.isActive(currentMatchGeneration = 5L, nowMillis = grantedAt))
    }

    @Test
    fun defaultAndPurchasedStatesAreAlwaysClockPlausible() {
        assertTrue(PremiumState().isClockPlausibleAt(nowMillis = 0L))
        assertTrue(PremiumState.purchased().isClockPlausibleAt(nowMillis = 0L))
    }

    @Test
    fun adGrantedStateIsClockPlausibleWhenStartedAtOrBeforeNow() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(matchGeneration = 5L, nowMillis = grantedAt)

        assertTrue(state.isClockPlausibleAt(nowMillis = grantedAt))
        assertTrue(state.isClockPlausibleAt(nowMillis = grantedAt + 1))
    }

    @Test
    fun adGrantedStateIsNotClockPlausibleWhenStartedInTheFuture() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(matchGeneration = 5L, nowMillis = grantedAt)

        // 저장소에서 읽어온 시점의 now가 부여 시각보다 과거라면(기기 시계 되돌림/손상),
        // isActive의 경과시간 계산이 음수가 되어 영영 만료되지 않는 오판을 막아야 한다.
        assertFalse(state.isClockPlausibleAt(nowMillis = grantedAt - 1))
    }
}
