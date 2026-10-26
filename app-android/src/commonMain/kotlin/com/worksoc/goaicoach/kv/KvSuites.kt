package com.worksoc.goaicoach.kv

/**
 * iOS 저장 묶음 이름 전부(백로그 #294) — 안드로이드 `SharedPreferences` 파일 이름과 같다(이름이 곧 저장 포맷 — 함정 1 · 69).
 * 저장소는 **이 상수로만** 연다(`KvSuitesContractTest`가 다른 자리의 글자 그대로를 막고, 안드로이드 이름과 대조한다).
 * 안드로이드는 개발자 초기화를 접두사 훑기로 하지만, iOS는 묶음 파일 배치가 비공개라 이 목록을 돈다([wipedByFreshInstall]).
 */
internal object KvSuites {
    const val UserPreferences = "go_ai_coach_user_preferences"
    const val ThemeMode = "go_ai_coach_theme_mode"
    const val BackgroundMusic = "go_ai_coach_background_music"
    const val UiLanguage = "go_ai_coach_ui_language"
    const val DeveloperMode = "go_ai_coach_developer_mode"
    const val WhatsNew = "go_ai_coach_whats_new"
    const val ReleaseReset = "go_ai_coach_release_reset"
    const val DailyQuote = "go_ai_coach_daily_quote"
    const val DailyFortune = "go_ai_coach_daily_fortune"
    const val Guide = "go_ai_coach_guide"
    const val LifeAndDeath = "go_ai_coach_life_and_death"
    const val UserProfile = "go_ai_coach_user_profile"
    const val Session = "go_ai_coach_session"
    const val BotCollection = "go_ai_coach_bot_collection"
    const val PremiumState = "go_ai_coach_premium_state"
    const val Consumables = "go_ai_coach_consumables"
    const val Attendance = "go_ai_coach_attendance"
    const val DailyDraw = "go_ai_coach_daily_draw"
    const val RankMeasure = "go_ai_coach_rank_measure"
    const val DeviceIdentity = "go_ai_coach_device_identity"

    val all: List<String> = listOf(
        UserPreferences,
        ThemeMode,
        BackgroundMusic,
        UiLanguage,
        DeveloperMode,
        WhatsNew,
        ReleaseReset,
        DailyQuote,
        DailyFortune,
        Guide,
        LifeAndDeath,
        UserProfile,
        Session,
        BotCollection,
        PremiumState,
        Consumables,
        Attendance,
        DailyDraw,
        RankMeasure,
        DeviceIdentity,
    )

    /** 개발자 「새로 설치한 것처럼」이 지우는 묶음 — 기기 id만 남긴다(안드로이드 `wipeToFreshInstall`과 같은 범위). */
    val wipedByFreshInstall: List<String> = all - DeviceIdentity
}
