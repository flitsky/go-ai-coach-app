package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.safety.EngineEndgameWatchdogTimeoutMillis
import com.worksoc.goaicoach.application.safety.engineTurnWatchdogTimeoutMillisFor
import com.worksoc.goaicoach.application.safety.isEngineTurnWatchdogTriggered
import com.worksoc.goaicoach.shared.SearchTimeLimit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun endgameTimeoutIgnoresSearchTimeLimitAndUsesFixedBudget() {
        assertEquals(
            EngineEndgameWatchdogTimeoutMillis,
            engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinOneSecond, isResolvingEndgame = true),
        )
        assertEquals(
            EngineEndgameWatchdogTimeoutMillis,
            engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.Off, isResolvingEndgame = true),
        )
    }

    @Test
    fun notTriggeredDuringEndgameResolutionUnderTheEndgameBudgetEvenPastNormalThreshold() {
        // 일반 착수 기준(1초 제한 -> 4_200ms)은 이미 넘었지만 계가 처리 중이므로 트리거되지 않는다.
        assertFalse(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 8_700L,
                searchTimeLimit = SearchTimeLimit.WithinOneSecond,
                isResolvingEndgame = true,
            ),
        )
    }

    @Test
    fun triggeredDuringEndgameResolutionOnceEndgameBudgetIsReached() {
        assertTrue(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = EngineEndgameWatchdogTimeoutMillis,
                searchTimeLimit = SearchTimeLimit.WithinOneSecond,
                isResolvingEndgame = true,
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
