package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.safety.engineTurnWatchdogTimeoutMillisFor
import com.worksoc.goaicoach.application.safety.isEngineTurnWatchdogTriggered
import com.worksoc.goaicoach.shared.SearchTimeLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTurnWatchdogTest {
    @Test
    fun timeoutScalesConfiguredLimitByOnePointTwoPlusThreeSeconds() {
        assertEquals(15_000L, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinTenSeconds))
        assertEquals(6_600L, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinThreeSeconds))
        assertEquals(4_200L, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinOneSecond))
    }

    @Test
    fun timeoutIsFixedSixtySecondsWhenLimitIsOff() {
        assertEquals(60_000L, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.Off))
    }

    @Test
    fun notTriggeredWhenNotAiTurnEvenIfElapsedIsLong() {
        assertFalse(
            isEngineTurnWatchdogTriggered(
                isAiTurn = false,
                elapsedSinceTurnStartMillis = 999_999L,
                searchTimeLimit = SearchTimeLimit.WithinThreeSeconds,
            ),
        )
    }

    @Test
    fun notTriggeredBeforeThresholdIsReached() {
        assertFalse(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 6_599L,
                searchTimeLimit = SearchTimeLimit.WithinThreeSeconds,
            ),
        )
    }

    @Test
    fun triggeredOnceThresholdIsReached() {
        assertTrue(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 6_600L,
                searchTimeLimit = SearchTimeLimit.WithinThreeSeconds,
            ),
        )
    }

    @Test
    fun triggeredAtSixtySecondsWhenLimitIsOff() {
        assertFalse(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 59_999L,
                searchTimeLimit = SearchTimeLimit.Off,
            ),
        )
        assertTrue(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 60_000L,
                searchTimeLimit = SearchTimeLimit.Off,
            ),
        )
    }
}
