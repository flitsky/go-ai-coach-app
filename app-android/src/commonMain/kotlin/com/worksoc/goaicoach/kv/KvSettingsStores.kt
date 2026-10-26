package com.worksoc.goaicoach.kv

import com.worksoc.goaicoach.application.lifecycle.ReleaseResetPolicy
import com.worksoc.goaicoach.application.preferences.BackgroundMusicSettingsPort
import com.worksoc.goaicoach.application.preferences.DeveloperModeStorePort
import com.worksoc.goaicoach.application.preferences.ReleaseResetMarkerPort
import com.worksoc.goaicoach.application.preferences.ThemeModeStorePort
import com.worksoc.goaicoach.application.preferences.UiLanguageStorePort
import com.worksoc.goaicoach.application.preferences.WhatsNewStorePort
import com.worksoc.goaicoach.shared.policy.ThemeMode
import kotlinx.coroutines.flow.Flow

// 설정 저장소의 공용 구현(백로그 #294 — 이식 계획 P1). 안드로이드 `persistence/`의 같은 이름 저장소와 **묶음 이름 · 키 · 기본값이
// 같다** — 고칠 때는 둘을 함께 고친다. 묶음 이름은 `KvSuitesContractTest`가 안드로이드와 대조한다. ⚠️ 키 · 기본값을 두 쪽에서 대조하는
// 시험은 없다(`KvStoreParityTest`는 코덱만 본다) — 그래서 이 파일의 키 · 기본값은 안드로이드 저장소를 열어 놓고 고친다.

internal class KvThemeModeStore(factory: KeyValueStoreFactory) : ThemeModeStorePort {
    private val kv = factory.open(KvSuites.ThemeMode)

    override fun load(): ThemeMode = ThemeMode.fromId(kv.getString("mode"))

    override fun save(mode: ThemeMode) = kv.putString("mode", mode.id)

    override fun modeChanges(): Flow<ThemeMode> = kv.watch("mode") { load() }
}

internal class KvBackgroundMusicStore(factory: KeyValueStoreFactory) : BackgroundMusicSettingsPort {
    private val kv = factory.open(KvSuites.BackgroundMusic)

    override fun isEnabled(): Boolean = kv.getBoolean("enabled", true)

    override fun setEnabled(enabled: Boolean) = kv.putBoolean("enabled", enabled)

    override fun enabledChanges(): Flow<Boolean> = kv.watch("enabled") { isEnabled() }
}

internal class KvUiLanguageStore(factory: KeyValueStoreFactory) : UiLanguageStorePort {
    private val kv = factory.open(KvSuites.UiLanguage)

    override fun loadName(): String? = kv.getString("language")

    override fun save(languageName: String) = kv.putString("language", languageName)
}

internal class KvDeveloperModeStore(factory: KeyValueStoreFactory) : DeveloperModeStorePort {
    private val kv = factory.open(KvSuites.DeveloperMode)

    override fun isEnabled(): Boolean = kv.getBoolean("enabled", false)

    override fun setEnabled(enabled: Boolean) = kv.putBoolean("enabled", enabled)

    override fun lastResetUtcMillis(): Long? = kv.getLong("last_reset_utc_millis", -1L).takeIf { it != -1L }

    override fun markResetBaseline(utcMillis: Long) = kv.putLong("last_reset_utc_millis", utcMillis)
}

/** [isFreshInstall]은 플랫폼이 답한다 — 안드로이드는 설치 시각 = 갱신 시각, iOS는 이 묶음이 처음 열렸는가. */
internal class KvWhatsNewStore(factory: KeyValueStoreFactory, private val freshInstall: () -> Boolean) : WhatsNewStorePort {
    private val kv = factory.open(KvSuites.WhatsNew)

    override fun lastSeenVersionCode(): Int? = kv.getInt("last_seen_version_code", -1).takeIf { it != -1 }

    override fun markSeen(versionCode: Int) = kv.putInt("last_seen_version_code", versionCode)

    override fun isFreshInstall(): Boolean = runCatching { freshInstall() }.getOrDefault(true)
}

internal class KvReleaseResetStore(factory: KeyValueStoreFactory) : ReleaseResetMarkerPort {
    private val kv = factory.open(KvSuites.ReleaseReset)

    override fun lastAppliedGeneration(): Int = kv.getInt("applied_generation", ReleaseResetPolicy.NoResetApplied)

    override fun markApplied(generation: Int) = kv.putInt("applied_generation", generation)

    override fun isNoticePending(): Boolean = kv.getBoolean("notice_pending", false)

    override fun markNoticePending() = kv.putBoolean("notice_pending", true)

    override fun clearNoticePending() = kv.putBoolean("notice_pending", false)
}
