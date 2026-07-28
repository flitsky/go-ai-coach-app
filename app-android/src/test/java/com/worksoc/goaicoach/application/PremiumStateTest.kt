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
}
