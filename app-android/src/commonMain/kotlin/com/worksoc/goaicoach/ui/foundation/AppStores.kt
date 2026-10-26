package com.worksoc.goaicoach.ui.foundation

import com.worksoc.goaicoach.application.attendance.AttendanceStorePort
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.application.consumable.ConsumableStorePort
import com.worksoc.goaicoach.application.dailydraw.DailyDrawStorePort
import com.worksoc.goaicoach.application.dailyfortune.DailyFortuneStorePort
import com.worksoc.goaicoach.application.dailyquote.DailyQuoteStorePort
import com.worksoc.goaicoach.application.debugreport.DebugReportMirrorPort
import com.worksoc.goaicoach.application.device.DeviceIdentityStorePort
import com.worksoc.goaicoach.application.engine.EngineBenchmarkStorePort
import com.worksoc.goaicoach.application.gamehistory.GameHistoryStorePort
import com.worksoc.goaicoach.application.guide.GuideProgressStorePort
import com.worksoc.goaicoach.application.guide.LifeAndDeathProgressPort
import com.worksoc.goaicoach.application.preferences.BackgroundMusicSettingsPort
import com.worksoc.goaicoach.application.preferences.DeveloperModeStorePort
import com.worksoc.goaicoach.application.preferences.ReleaseResetMarkerPort
import com.worksoc.goaicoach.application.preferences.ThemeModeStorePort
import com.worksoc.goaicoach.application.preferences.UiLanguageStorePort
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.application.preferences.WhatsNewStorePort
import com.worksoc.goaicoach.application.premium.port.PremiumStateStorePort
import com.worksoc.goaicoach.application.profile.UserProfileStorePort
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureStorePort
import com.worksoc.goaicoach.application.savedgame.SavedGameStorePort

/**
 * 화면이 쓰는 저장소 전부(백로그 #294 — 이식 계획 S7). 화면은 `XStore(context)`를 만들지 않고 이 묶음에서 포트로 꺼낸다 —
 * 안드로이드는 `AndroidAppStores`(읽을 때마다 새 저장소 — 오늘과 같은 수명), iOS는 같은 키 · 같은 값의 iOS 저장소다.
 */
interface AppStores {
    val userPreferences: UserPreferencesStorePort
    val themeMode: ThemeModeStorePort
    val backgroundMusic: BackgroundMusicSettingsPort
    val uiLanguage: UiLanguageStorePort
    val developerMode: DeveloperModeStorePort
    val whatsNew: WhatsNewStorePort
    val releaseReset: ReleaseResetMarkerPort
    val dailyQuote: DailyQuoteStorePort
    val dailyFortune: DailyFortuneStorePort
    val guideProgress: GuideProgressStorePort
    val lifeAndDeathProgress: LifeAndDeathProgressPort
    val userProfile: UserProfileStorePort
    val gameHistory: GameHistoryStorePort
    val savedGame: SavedGameStorePort
    val botCollection: BotCollectionStorePort
    val premiumState: PremiumStateStorePort
    val consumables: ConsumableStorePort
    val attendance: AttendanceStorePort
    val dailyDraw: DailyDrawStorePort
    val rankMeasure: RankMeasureStorePort
    val engineBenchmark: EngineBenchmarkStorePort
    val deviceIdentity: DeviceIdentityStorePort
    val debugReportMirror: DebugReportMirrorPort
}
