package com.worksoc.goaicoach

import android.content.Context
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.persistence.AttendanceStore
import com.worksoc.goaicoach.persistence.BackgroundMusicStore
import com.worksoc.goaicoach.persistence.BotCollectionStore
import com.worksoc.goaicoach.persistence.ConsumableInventoryStore
import com.worksoc.goaicoach.persistence.DailyDrawStore
import com.worksoc.goaicoach.persistence.DailyFortuneStore
import com.worksoc.goaicoach.persistence.DailyQuoteStore
import com.worksoc.goaicoach.persistence.DebugReportMirrorStore
import com.worksoc.goaicoach.persistence.DeveloperModeStore
import com.worksoc.goaicoach.persistence.DeviceIdentityStore
import com.worksoc.goaicoach.persistence.DiagnosticEventLog
import com.worksoc.goaicoach.persistence.EngineBenchmarkStore
import com.worksoc.goaicoach.persistence.GameHistoryStore
import com.worksoc.goaicoach.persistence.GameSessionStore
import com.worksoc.goaicoach.persistence.GuideProgressStore
import com.worksoc.goaicoach.persistence.LifeAndDeathProgressStore
import com.worksoc.goaicoach.persistence.PremiumStateStore
import com.worksoc.goaicoach.persistence.RankMeasureStore
import com.worksoc.goaicoach.persistence.ReleaseResetStore
import com.worksoc.goaicoach.persistence.ThemeModeStore
import com.worksoc.goaicoach.persistence.UiLanguageStore
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.persistence.UserProfileStore
import com.worksoc.goaicoach.persistence.WhatsNewStore
import com.worksoc.goaicoach.persistence.loadReferenceGameReplay
import com.worksoc.goaicoach.platform.AdsConsentManager
import com.worksoc.goaicoach.platform.AndroidClipboardPort
import com.worksoc.goaicoach.platform.AndroidUserNoticePort
import com.worksoc.goaicoach.platform.AppUpdateCheck
import com.worksoc.goaicoach.platform.BoardScanEntry
import com.worksoc.goaicoach.platform.BundledAssets
import com.worksoc.goaicoach.platform.DebugArmFiles
import com.worksoc.goaicoach.platform.DeveloperTools
import com.worksoc.goaicoach.platform.PlayHapticFeedback
import com.worksoc.goaicoach.platform.PlayHaptics
import com.worksoc.goaicoach.platform.processAppUpdateCheck
import com.worksoc.goaicoach.ui.foundation.AppPlatform
import com.worksoc.goaicoach.ui.foundation.AppStores
import com.worksoc.goaicoach.vision.SharedBoardPhotoInbox
import java.io.File

/**
 * 안드로이드의 [AppPlatform](백로그 #294 — 이식 계획 S1) — 프로세스에 하나(`GoAiCoachApplication.appPlatform`), `MainActivity`가
 * 컴포지션에 넣는다. 조립만 한다:
 * 멤버는 이미 있던 어댑터(`platform/`)와 저장소(`persistence/`)를 그대로 만든다.
 */
internal class AndroidAppPlatform(context: Context) : AppPlatform {
    private val appContext = context.applicationContext

    override val notice = AndroidUserNoticePort(appContext)
    override val clipboard = AndroidClipboardPort(appContext)
    override val stores: AppStores = AndroidAppStores(appContext)

    // 판이 오늘처럼 `remember`로 하나 만든다 — 읽을 때마다 새로(진동기 서비스를 잡는 일은 생성자에서 한 번).
    override val haptics: PlayHapticFeedback get() = PlayHaptics(appContext)

    override val appUpdate: AppUpdateCheck get() = processAppUpdateCheck(appContext)

    override val boardScan: BoardScanEntry = object : BoardScanEntry {
        override fun hasPendingSharedPhoto(): Boolean = SharedBoardPhotoInbox.hasPhoto(appContext)
    }

    override val assets = object : BundledAssets {
        override fun readBytes(path: String): ByteArray? = runCatching { appContext.assets.open(path).use { it.readBytes() } }.getOrNull()

        override fun referenceGameReplay(): GameReplayData? = loadReferenceGameReplay(appContext)

        override fun licensesJson(): String? =
            runCatching { appContext.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() } }.getOrNull()
    }

    override val developer = object : DeveloperTools {
        override fun wipeToFreshInstall() = wipeToFreshInstall(appContext)

        override fun restartToFreshInstall() = restartToFreshInstall(appContext)

        override fun runReleaseResetAgain(): Boolean = runReleaseResetAgain(appContext)

        override fun resetAdConsent() = AdsConsentManager.resetForDebug(appContext)

        override fun diagnosticLogText(): String = DiagnosticEventLog(File(appContext.filesDir, DiagnosticEventLog.FileName)).readText()
    }

    override val debugArmFiles = DebugArmFiles { name -> BuildConfig.DEBUG && File(appContext.filesDir, name).let { it.isFile && it.delete() } }
}

/**
 * 안드로이드의 [AppStores](S7). ⚠️ **읽을 때마다 새 저장소를 만든다** — 화면이 `XStore(context)`를 그 자리에서 만들던 오늘과
 * 수명이 같다(저장소는 SharedPreferences · 파일을 감쌀 뿐 상태를 들지 않는다). 하나로 묶어 두면 어느 저장소가 캐시를 들기 시작할 때
 * 수명이 몰래 바뀐다.
 */
internal class AndroidAppStores(private val appContext: Context) : AppStores {
    override val userPreferences get() = UserPreferencesStore(appContext)
    override val themeMode get() = ThemeModeStore(appContext)
    override val backgroundMusic get() = BackgroundMusicStore(appContext)
    override val uiLanguage get() = UiLanguageStore(appContext)
    override val developerMode get() = DeveloperModeStore(appContext)
    override val whatsNew get() = WhatsNewStore(appContext)
    override val releaseReset get() = ReleaseResetStore(appContext)
    override val dailyQuote get() = DailyQuoteStore(appContext)
    override val dailyFortune get() = DailyFortuneStore(appContext)
    override val guideProgress get() = GuideProgressStore(appContext)
    override val lifeAndDeathProgress get() = LifeAndDeathProgressStore(appContext)
    override val userProfile get() = UserProfileStore(appContext)
    override val gameHistory get() = GameHistoryStore(appContext)
    override val savedGame get() = GameSessionStore(appContext)
    override val botCollection get() = BotCollectionStore(appContext)
    override val premiumState get() = PremiumStateStore(appContext)
    override val consumables get() = ConsumableInventoryStore(appContext)
    override val attendance get() = AttendanceStore(appContext)
    override val dailyDraw get() = DailyDrawStore(appContext)
    override val rankMeasure get() = RankMeasureStore(appContext)
    override val engineBenchmark get() = EngineBenchmarkStore(appContext)
    override val deviceIdentity get() = DeviceIdentityStore(appContext)
    override val debugReportMirror get() = DebugReportMirrorStore(appContext)
}
