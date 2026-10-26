package com.worksoc.goaicoach.persistence

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.worksoc.goaicoach.application.preferences.ThemeModeStorePort
import com.worksoc.goaicoach.shared.policy.ThemeMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * 설정의 「화면 모드」(백로그 #272). 설정 패널이 쓰고, 앱의 맨 바깥 테마(`ui/settings/AppTheme.kt`)가 [modeChanges]를 듣고 다시 그린다 —
 * 그 사이에 프로세스 전역 상태를 두지 않는다(`ShellStateLedgerContractTest` — 전역으로 옮기는 것은 원장을 피하는 길이 아니다).
 *
 * ⚠️ `UserPreferencesSnapshot`에 넣지 않았다 — 그쪽 자동저장은 스냅샷을 통째로 새로 만들어, 배선되지 않은 필드가 조용히 기본으로 돌아간다
 * (`GuideProgressStore` · `BackgroundMusicStore`와 같은 이유로 따로 선다).
 * 파일 이름의 `go_ai_coach_` 접두사는 개발자 초기화가 훑는 기준이다. 정식 릴리즈 초기화(#63)는 이것을 지우지 않는다 — 권한이 아니라 취향이다.
 * 기기 백업에 함께 실린다(`res/xml/backup_rules.xml`은 엔진 모델과 광고 WebView만 뺀다).
 */
internal class ThemeModeStore(context: Context) : ThemeModeStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun load(): ThemeMode = ThemeMode.fromId(prefs.getString(ModeKey, null))

    override fun save(mode: ThemeMode) {
        prefs.edit { putString(ModeKey, mode.id) }
    }

    /**
     * 지금 값으로 시작해 바뀔 때마다 새 값을 낸다. ⚠️ `SharedPreferences`는 듣는 쪽을 약하게 쥐므로 리스너를
     * 이 흐름이 살아 있는 동안 여기서 붙잡아 둔다 — 람다를 바로 넘기면 수거돼 조용히 멈춘다(`BackgroundMusicStore`와 같다).
     */
    override fun modeChanges(): Flow<ThemeMode> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ModeKey) trySend(load())
        }
        send(load())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    private companion object {
        const val PrefsName = "go_ai_coach_theme_mode"
        const val ModeKey = "mode"
    }
}
