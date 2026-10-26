package com.worksoc.goaicoach.kv

import com.worksoc.goaicoach.ui.foundation.AppStores

/**
 * iOS의 [AppStores](백로그 #294 — P1~P3) — 키-값은 `NSUserDefaults`, 파일은 Application Support. 저장소는 프로세스에 하나씩이다
 * (안드로이드는 읽을 때마다 새로 만들지만 상태를 들지 않으므로 같다 — 여기는 바뀐 키 알림을 한 객체가 내야 해서 하나로 둔다).
 * 「새로 설치했는가」는 새 소식 묶음이 처음 열렸는가로 답한다(안드로이드는 설치 시각 = 갱신 시각).
 */
internal class IosAppStores(
    kv: KeyValueStoreFactory = UserDefaultsKeyValueStores,
    files: AppFileStore = IosAppFileStore(),
) : AppStores {
    private val firstLaunch = !kv.open("go_ai_coach_whats_new").contains("last_seen_version_code")

    override val userPreferences = KvUserPreferencesStore(kv)
    override val themeMode = KvThemeModeStore(kv)
    override val backgroundMusic = KvBackgroundMusicStore(kv)
    override val uiLanguage = KvUiLanguageStore(kv)
    override val developerMode = KvDeveloperModeStore(kv)
    override val whatsNew = KvWhatsNewStore(kv) { firstLaunch }
    override val releaseReset = KvReleaseResetStore(kv)
    override val dailyQuote = KvDailyQuoteStore(kv)
    override val dailyFortune = KvDailyFortuneStore(kv)
    override val guideProgress = KvGuideProgressStore(kv)
    override val lifeAndDeathProgress = KvLifeAndDeathProgressStore(kv)
    override val userProfile = KvUserProfileStore(kv)
    override val gameHistory = KvGameHistoryStore(files)
    override val savedGame = KvSavedGameStore(kv)
    override val botCollection = KvBotCollectionStore(kv)
    override val premiumState = KvPremiumStateStore(kv)
    override val consumables = KvConsumableInventoryStore(kv)
    override val attendance = KvAttendanceStore(kv)
    override val dailyDraw = KvDailyDrawStore(kv)
    override val rankMeasure = KvRankMeasureStore(kv)
    override val engineBenchmark = KvEngineBenchmarkStore(files)
    override val deviceIdentity = KvDeviceIdentityStore(kv)
    override val debugReportMirror = KvDebugReportMirrorStore(files)
}

/** 프로세스의 iOS 저장소 — `IosAppPlatform`과 ui 맨 아래층(`l10n`처럼 `LocalAppPlatform`을 못 읽는 곳)이 같은 묶음을 본다. */
internal val iosAppStores: IosAppStores by lazy { IosAppStores() }

/** 개발자 메뉴 「새로 설치한 것처럼」 — 안드로이드 `wipeToFreshInstall`과 같은 범위: `go_ai_coach_*` 묶음(기기 id 제외)과 대국 기록 폴더. */
internal fun iosWipeToFreshInstall() {
    listOf(
        "go_ai_coach_user_preferences", "go_ai_coach_theme_mode", "go_ai_coach_background_music", "go_ai_coach_ui_language",
        "go_ai_coach_developer_mode", "go_ai_coach_whats_new", "go_ai_coach_release_reset", "go_ai_coach_daily_quote",
        "go_ai_coach_daily_fortune", "go_ai_coach_guide", "go_ai_coach_life_and_death", "go_ai_coach_user_profile",
        "go_ai_coach_session", "go_ai_coach_bot_collection", "go_ai_coach_premium_state", "go_ai_coach_consumables",
        "go_ai_coach_attendance", "go_ai_coach_daily_draw", "go_ai_coach_rank_measure",
    ).forEach { name -> UserDefaultsKeyValueStores.open(name).clear() }
    IosAppFileStore().deleteRecursively("game_history")
}
