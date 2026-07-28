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

        assertFalse(state.isActive(currentSessionGeneration = 1L, nowMillis = 0L))
        assertTrue(state.source == PremiumSource.None)
    }

    @Test
    fun purchasedStateIsAlwaysActiveRegardlessOfSessionOrTime() {
        val state = PremiumState.purchased()

        assertTrue(state.isActive(currentSessionGeneration = 1L, nowMillis = 0L))
        assertTrue(state.isActive(currentSessionGeneration = 99L, nowMillis = Long.MAX_VALUE))
    }

    @Test
    fun adGrantedStateIsActiveWithinOneHourForSameSession() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(sessionGeneration = 5L, nowMillis = grantedAt)

        assertTrue(state.isActive(currentSessionGeneration = 5L, nowMillis = grantedAt))
        assertTrue(
            state.isActive(
                currentSessionGeneration = 5L,
                nowMillis = grantedAt + PremiumState.AdGrantDurationMillis - 1,
            ),
        )
    }

    @Test
    fun adGrantedStateExpiresAfterOneHour() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(sessionGeneration = 5L, nowMillis = grantedAt)

        assertFalse(
            state.isActive(
                currentSessionGeneration = 5L,
                nowMillis = grantedAt + PremiumState.AdGrantDurationMillis,
            ),
        )
        assertFalse(
            state.isActive(
                currentSessionGeneration = 5L,
                nowMillis = grantedAt + PremiumState.AdGrantDurationMillis + 60_000L,
            ),
        )
    }

    @Test
    fun adGrantedStateDoesNotCarryOverToNewMatch() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(sessionGeneration = 5L, nowMillis = grantedAt)

        // 새 대국이 시작되어 sessionGeneration이 바뀌면(예: 6L), 시간이 남아 있어도 무효.
        assertFalse(state.isActive(currentSessionGeneration = 6L, nowMillis = grantedAt))
    }

    @Test
    fun adGrantedStateWithNullSessionIsNotActiveUntilBound() {
        // 홈 화면 등 대국 시작 전에 활성화된 경우, 아직 어느 세션에도 묶이지 않은 상태.
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(sessionGeneration = null, nowMillis = grantedAt)

        assertFalse(state.isActive(currentSessionGeneration = 1L, nowMillis = grantedAt))
        assertFalse(state.isActive(currentSessionGeneration = 999L, nowMillis = grantedAt))
    }

    @Test
    fun bindToSessionIfPendingBindsUnboundGrantToStartedMatch() {
        val grantedAt = 1_000_000L
        val pending = PremiumState.adGranted(sessionGeneration = null, nowMillis = grantedAt)

        val bound = pending.bindToSessionIfPending(sessionGeneration = 7L)

        assertTrue(bound.isActive(currentSessionGeneration = 7L, nowMillis = grantedAt))
        assertFalse(bound.isActive(currentSessionGeneration = 8L, nowMillis = grantedAt))
    }

    @Test
    fun bindToSessionIfPendingDoesNotOverwriteAlreadyBoundGrant() {
        val grantedAt = 1_000_000L
        val bound = PremiumState.adGranted(sessionGeneration = 5L, nowMillis = grantedAt)

        // 이미 5L에 묶여 있는데 새 대국(6L)이 또 시작돼도, 다른 세션으로 재바인딩하지 않는다
        // (그 결과 5L 전용으로 남아 6L에서는 자연히 무효 판정된다).
        val result = bound.bindToSessionIfPending(sessionGeneration = 6L)

        assertFalse(result.isActive(currentSessionGeneration = 6L, nowMillis = grantedAt))
        assertTrue(result.isActive(currentSessionGeneration = 5L, nowMillis = grantedAt))
    }
}
