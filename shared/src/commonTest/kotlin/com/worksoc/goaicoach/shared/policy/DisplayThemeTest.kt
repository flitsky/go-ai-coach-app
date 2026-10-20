package com.worksoc.goaicoach.shared.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 밤 시간과 화면 모드(백로그 #272). */
class DisplayThemeTest {

    @Test
    fun nightRunsFromNineInTheEveningUntilFiveInTheMorning() {
        assertFalse(isNightHour(20), "20시는 낮이다")
        assertTrue(isNightHour(21), "21시는 밤이다")
        assertTrue(isNightHour(0), "0시는 밤이다")
        assertTrue(isNightHour(4), "4시는 밤이다")
        assertFalse(isNightHour(5), "5시 정각부터 낮이다")
        assertEquals(8, (0 until 24).count(::isNightHour), "밤은 여덟 시간이다(21~4시)")
    }

    @Test
    fun eachModeDecidesDarknessFromItsOwnInput() {
        (0 until 24).forEach { hour ->
            listOf(false, true).forEach { systemDark ->
                assertEquals(systemDark, ThemeMode.System.isDark(systemDark, hour), "시스템 따라가기는 폰의 다크 모드만 본다")
                assertFalse(ThemeMode.Light.isDark(systemDark, hour), "밝게는 늘 밝다")
                assertTrue(ThemeMode.Dark.isDark(systemDark, hour), "어둡게는 늘 어둡다")
                assertEquals(isNightHour(hour), ThemeMode.NightHours.isDark(systemDark, hour), "밤 시간 자동은 시각만 본다")
            }
        }
    }

    /** ⚠️ [ThemeMode.id]는 저장 포맷이다 — 무심한 개명을 잡는다. */
    @Test
    fun storedIdsAreFrozenAndUnknownFallsBackToSystem() {
        assertEquals(listOf("system", "light", "dark", "night_hours"), ThemeMode.entries.map { it.id })
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromId(it.id)) }
        assertEquals(ThemeMode.System, ThemeMode.fromId(null))
        assertEquals(ThemeMode.System, ThemeMode.fromId("sepia"))
    }
}
