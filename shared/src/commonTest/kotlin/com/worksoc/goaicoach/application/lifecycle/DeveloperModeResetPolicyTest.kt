package com.worksoc.goaicoach.application.lifecycle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DeveloperModeResetPolicy]의 계약(백로그 #99).
 *
 * ⚠️ **이 그물이 지키는 핵심은 "언제 지우는가"가 아니라 "엉뚱할 때 지우지 않는가"** 다.
 * 잘못 지우면 사용자 데이터가 사라지고, 되돌릴 방법이 없다.
 */
class DeveloperModeResetPolicyTest {

    private val hour = 60L * 60L * 1000L
    /** 2026-01-01 00:00 UTC 근처의 임의 시점. 값 자체에 뜻은 없다. */
    private val base = 1_767_225_600_000L

    /** ⚠️ 기준점이 없으면 절대 지우지 않는다 — 켜자마자 지우면 무슨 일인지 알 수 없다. */
    @Test
    fun withoutABaselineNothingIsEverReset() {
        assertFalse(DeveloperModeResetPolicy.shouldReset(null, base))
        assertFalse(DeveloperModeResetPolicy.shouldReset(null, base + 100L * hour))
    }

    /** 같은 구간 안에서는 몇 번을 켜도 지우지 않는다. */
    @Test
    fun theSameIntervalNeverResetsTwice() {
        val start = DeveloperModeResetPolicy.intervalIndexOf(base) * 3L * hour
        assertFalse(DeveloperModeResetPolicy.shouldReset(start, start))
        assertFalse(DeveloperModeResetPolicy.shouldReset(start, start + hour))
        assertFalse(DeveloperModeResetPolicy.shouldReset(start, start + 2 * hour + 59 * 60_000L))
    }

    /** 구간이 넘어가면 지운다 — 그것이 이 정책의 일이다. */
    @Test
    fun crossingIntoTheNextIntervalResets() {
        val start = DeveloperModeResetPolicy.intervalIndexOf(base) * 3L * hour
        assertTrue(DeveloperModeResetPolicy.shouldReset(start, start + 3 * hour))
        assertTrue(DeveloperModeResetPolicy.shouldReset(start, start + 100 * hour))
    }

    /**
     * ⚠️ **시계를 되돌려도 우회되지 않는다.** 구간이 달라지는 것은 앞으로 감든 뒤로 감든 같다.
     * 우회하려면 **같은 구간 안에 계속 머물러야** 하는데, 그것이 곧 이 항목이 노린 번거로움이다.
     */
    @Test
    fun windingTheClockBackwardsAlsoResets() {
        val start = DeveloperModeResetPolicy.intervalIndexOf(base) * 3L * hour
        assertTrue(
            DeveloperModeResetPolicy.shouldReset(start, start - 3 * hour),
            "시계를 뒤로 감아 이전 구간으로 갔는데 초기화되지 않았다 — 우회가 된다(#99).",
        )
    }

    /**
     * ⚠️ **구간 경계가 UTC 0·3·6…시와 맞아야 한다.** 발주가 *"UTC 기준 3의 배수 시간대"* 였다.
     * 에포크가 구간 경계이고 24가 3으로 나누어떨어지므로 성립한다 — 그것을 직접 확인한다.
     */
    @Test
    fun intervalBoundariesLandOnMultiplesOfThreeUtcHours() {
        // 에포크로부터 정확히 n시간 뒤가 구간 경계인지 본다.
        (0 until 24).forEach { hourOfDay ->
            val atHour = hourOfDay * hour
            val isBoundary = DeveloperModeResetPolicy.intervalIndexOf(atHour) !=
                DeveloperModeResetPolicy.intervalIndexOf(atHour - 1)
            assertEquals(
                hourOfDay % DeveloperModeResetPolicy.ResetIntervalHours == 0,
                isBoundary,
                "UTC ${hourOfDay}시가 구간 경계인지 판정이 어긋난다(#99).",
            )
        }
    }

    /** 하루 8번이라는 발주 그대로인지 — 상수를 바꾸면 여기서 걸린다. */
    @Test
    fun theDayIsDividedIntoEightIntervals()  {
        assertEquals(8, 24 / DeveloperModeResetPolicy.ResetIntervalHours)
    }
}
