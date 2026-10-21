package com.worksoc.goaicoach.shared.policy

/**
 * 화면의 밝기 — **밤 시간**의 유일한 정의와, 설정의 네 갈래가 지금 어두운 화면인지(백로그 #272, 사용자 2026-10-20).
 *
 * ## 밤 시간 — 21:00부터 다음 날 04:59까지
 * 기동 스플래시(#271 — 밤이면 스포트라이트 그림)와 야간 모드의 「밤 시간 자동」이 **같은 시간**을 쓴다. 한 곳에서만 정한다.
 * 5시 정각을 낮으로 둔 것은 스레드가 정한 것이다(사용자 문장은 *"밤 9시부터 다음날 새벽 5시까지"*). 시각은 폰의 지역 시각이다.
 */
fun isNightHour(hour: Int): Boolean = hour >= NightStartHour || hour < NightEndHour

const val NightStartHour: Int = 21
const val NightEndHour: Int = 5

/**
 * 설정 › 표시의 「화면 모드」(사용자 2026-10-20: 설정에서 고르기, 기본은 시스템 따라가기).
 *
 * ⚠️ [id]는 **저장 포맷**이다 — 상수 이름이 아니라 이 문자열을 바꾸면 고른 값이 기본으로 돌아간다.
 */
enum class ThemeMode(val id: String) {
    /** 폰의 다크 모드를 따른다(기본). */
    System("system"),

    /** 늘 밝게. */
    Light("light"),

    /** 늘 어둡게. */
    Dark("dark"),

    /** 밤 시간([isNightHour])에만 어둡게. */
    NightHours("night_hours"),
    ;

    /** 지금 어두운 화면인가 — [systemDark]는 폰의 다크 모드, [hour]는 폰의 지역 시각(0~23). */
    fun isDark(systemDark: Boolean, hour: Int): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
        NightHours -> isNightHour(hour)
    }

    /**
     * 맨 윗줄 빠른 버튼(백로그 #275)을 누르면 다음 갈래 — 밝게 → 어둡게 → 시스템 따라가기 → 밤 시간 자동 → 밝게.
     * 차례는 사용자가 정했다(2026-10-21: *"해 > 달 > 컴퓨터모양? > 시계"*). 설정의 선택지 차례([entries])와는 따로다.
     */
    val nextOnQuickToggle: ThemeMode
        get() = QuickToggleOrder[(QuickToggleOrder.indexOf(this) + 1) % QuickToggleOrder.size]

    companion object {
        val Default: ThemeMode = System

        /** 빠른 버튼의 차례 — 갈래를 더하면 여기에도 넣는다(빠지면 `DisplayThemeTest`가 빨갛다). */
        private val QuickToggleOrder: List<ThemeMode> = listOf(Light, Dark, System, NightHours)

        /** 저장된 문자열 → 갈래. 모르는 값(지운 갈래 · 깨진 값)은 기본으로. */
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: Default
    }
}
