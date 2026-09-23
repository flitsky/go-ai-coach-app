package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.safety.EngineEndgameWatchdogTimeoutMillis
import com.worksoc.goaicoach.application.safety.EngineResponseGraceMillis
import com.worksoc.goaicoach.application.safety.engineTurnWatchdogTimeoutMillisFor
import com.worksoc.goaicoach.application.safety.isEngineTurnWatchdogTriggered
import com.worksoc.goaicoach.shared.policy.SearchTimeLimit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineTurnWatchdogTest {
    /**
     * 설정값 × 1.2 + 부가 지연 3초 + **기본 응답 여유 5초**(2026-09-22).
     * ⚠️ 숫자를 손으로 적는다 — `EngineResponseGraceMillis`로 계산하면 그 상수를 바꿔도
     * 테스트가 함께 따라가서 **아무것도 지키지 않게 된다**(함정 24: 초록 ≠ 안전).
     */
    @Test
    fun timeoutScalesConfiguredLimitByOnePointTwoPlusOverheadPlusGrace() {
        assertEquals(20_000L, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinTenSeconds))
        assertEquals(11_600L, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinThreeSeconds))
        assertEquals(9_200L, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinOneSecond))
    }

    /** 여유가 **세 갈래 전부**에 붙는지 — 한 갈래만 붙는 사고를 막는다. */
    @Test
    fun theResponseGraceIsAddedToEveryBranch() {
        assertEquals(5_000L, EngineResponseGraceMillis)
        assertEquals(
            EngineEndgameWatchdogTimeoutMillis + EngineResponseGraceMillis,
            engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinOneSecond, isResolvingEndgame = true),
        )
        assertEquals(60_000L + EngineResponseGraceMillis, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.Off))
    }

    @Test
    fun timeoutIsFixedSixtyFiveSecondsWhenLimitIsOff() {
        assertEquals(65_000L, engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.Off))
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
                elapsedSinceTurnStartMillis = 11_599L,
                searchTimeLimit = SearchTimeLimit.WithinThreeSeconds,
            ),
        )
    }

    @Test
    fun triggeredOnceThresholdIsReached() {
        assertTrue(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 11_600L,
                searchTimeLimit = SearchTimeLimit.WithinThreeSeconds,
            ),
        )
    }

    @Test
    fun endgameTimeoutIgnoresSearchTimeLimitAndUsesFixedBudget() {
        assertEquals(
            25_000L,
            engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.WithinOneSecond, isResolvingEndgame = true),
        )
        assertEquals(
            25_000L,
            engineTurnWatchdogTimeoutMillisFor(SearchTimeLimit.Off, isResolvingEndgame = true),
        )
    }

    @Test
    fun notTriggeredDuringEndgameResolutionUnderTheEndgameBudgetEvenPastNormalThreshold() {
        // 일반 착수 기준(1초 제한 -> 9_200ms)은 이미 넘었지만 계가 처리 중이므로 트리거되지 않는다.
        assertFalse(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 9_500L,
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
                elapsedSinceTurnStartMillis = EngineEndgameWatchdogTimeoutMillis + EngineResponseGraceMillis,
                searchTimeLimit = SearchTimeLimit.WithinOneSecond,
                isResolvingEndgame = true,
            ),
        )
    }

    @Test
    fun triggeredAtSixtyFiveSecondsWhenLimitIsOff() {
        assertFalse(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 64_999L,
                searchTimeLimit = SearchTimeLimit.Off,
            ),
        )
        assertTrue(
            isEngineTurnWatchdogTriggered(
                isAiTurn = true,
                elapsedSinceTurnStartMillis = 65_000L,
                searchTimeLimit = SearchTimeLimit.Off,
            ),
        )
    }
}
