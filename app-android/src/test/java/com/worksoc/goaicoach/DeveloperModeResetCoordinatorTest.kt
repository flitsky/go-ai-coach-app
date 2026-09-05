package com.worksoc.goaicoach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DeveloperModeResetCoordinator]의 조합 계약(백로그 #99).
 *
 * ⚠️ **이 테스트가 실기 검증을 대신한다.** 이 코디네이터는 `BuildConfig.DEBUG`가 false인 빌드에서만
 * 도는데, 그런 빌드는 `isDebuggable = false`라 `run-as`로 저장소를 볼 수 없고 Play 이미지
 * 에뮬레이터는 `adb root`도 막혀 있다(2026-09-05에 `playInternal`을 실제로 설치해 확인했다).
 * **관찰할 수 없는 경로이므로 테스트가 유일한 그물이다.**
 */
class DeveloperModeResetCoordinatorTest {

    private val hour = 60L * 60L * 1000L
    private val now = 1_788_000_000_000L
    private val previousInterval = now - 4 * hour

    private class Spy {
        var wipes = 0
    }

    private fun coordinator(enabled: Boolean, last: Long?, spy: Spy) =
        DeveloperModeResetCoordinator(
            isDeveloperModeEnabled = { enabled },
            lastResetUtcMillis = { last },
            wipe = { spy.wipes++ },
        )

    /** ⚠️ **꺼져 있으면 절대 지우지 않는다** — 이 기능은 켠 사람에게만 적용된다. */
    @Test
    fun anUntouchedDeviceIsNeverWiped() {
        val spy = Spy()
        assertFalse(coordinator(enabled = false, last = previousInterval, spy = spy).applyIfNeeded(now))
        assertEquals("개발자 모드가 꺼져 있는데 데이터를 지웠다(#99).", 0, spy.wipes)
    }

    /** 켜져 있고 구간이 넘어갔으면 지운다 — 이 기능의 본체다. */
    @Test
    fun anEnabledDeviceIsWipedOnceTheIntervalRolls() {
        val spy = Spy()
        assertTrue(coordinator(enabled = true, last = previousInterval, spy = spy).applyIfNeeded(now))
        assertEquals(1, spy.wipes)
    }

    /** 같은 구간 안에서는 몇 번을 켜도 지우지 않는다 — 앱을 여러 번 켠다고 계속 지우면 안 된다. */
    @Test
    fun relaunchingInsideTheSameIntervalDoesNotWipe() {
        val spy = Spy()
        assertFalse(coordinator(enabled = true, last = now - 60_000L, spy = spy).applyIfNeeded(now))
        assertEquals(0, spy.wipes)
    }

    /**
     * ⚠️ **기준 시각이 없으면 지우지 않는다.** 켜는 쪽이 기준을 심지 않으면 이 기능은 조용히
     * 동작하지 않는데, **그 조용함이 데이터를 지키는 쪽으로 기울어야** 한다.
     */
    @Test
    fun aMissingBaselineFailsSafe() {
        val spy = Spy()
        assertFalse(coordinator(enabled = true, last = null, spy = spy).applyIfNeeded(now))
        assertEquals("기준 시각이 없는데 지웠다 — 켠 직후 지워진다는 뜻이다(#99).", 0, spy.wipes)
    }
}
