package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.shared.policy.ThemeMode
import kotlinx.coroutines.flow.Flow

// 앱 설정 저장소의 포트(백로그 #294 — 이식 계획 S7). 안드로이드는 `persistence/`의 SharedPreferences 저장소가 그대로 구현하고,
// iOS는 같은 키 · 같은 값으로 따로 구현한다. 화면은 이 포트만 안다 — 저장소를 직접 만들지 않는다(`AppStores`).

/** 화면 모드(#272) — 밝게 · 어둡게 · 시간에 따라. 바뀌면 [modeChanges]가 알린다(빠른 설정과 설정 화면이 같은 값을 본다). */
interface ThemeModeStorePort {
    fun load(): ThemeMode

    fun save(mode: ThemeMode)

    fun modeChanges(): Flow<ThemeMode>
}

/** 「앱 배경음악」 켬 · 끔(#244). 바뀌면 [enabledChanges]가 알린다 — 플레이어가 그것을 듣는다. */
interface BackgroundMusicSettingsPort {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)

    fun enabledChanges(): Flow<Boolean>
}

/** 화면 언어 — 고른 언어의 이름(`UiLanguage.name`). 고른 적이 없으면 `null`(기기 언어를 따른다). */
interface UiLanguageStorePort {
    fun loadName(): String?

    fun save(languageName: String)
}

/** 개발자 모드(빌드 정보 10탭) — 켬 · 끔과 「새로 설치한 것처럼」 초기화의 기준 시각. */
interface DeveloperModeStorePort {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)

    fun lastResetUtcMillis(): Long?

    fun markResetBaseline(utcMillis: Long)
}

/** 새 소식(#270) — 마지막으로 본 판 번호와 「새로 설치했는가」. */
interface WhatsNewStorePort {
    fun lastSeenVersionCode(): Int?

    fun markSeen(versionCode: Int)

    fun isFreshInstall(): Boolean
}

/** 출시 초기화(#90)의 표식 — 적용한 세대와 「초기화했다」는 안내가 남았는가. */
interface ReleaseResetMarkerPort {
    fun lastAppliedGeneration(): Int

    fun markApplied(generation: Int)

    fun isNoticePending(): Boolean

    fun markNoticePending()

    fun clearNoticePending()
}
