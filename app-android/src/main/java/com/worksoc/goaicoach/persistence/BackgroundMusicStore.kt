package com.worksoc.goaicoach.persistence

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * 「앱 배경음악」 켜기/끄기(백로그 #244). 설정의 「소리」 절이 쓰고, 앱 전체의 플레이어(`BackgroundMusicCoordinator`)가 바뀌는 대로 따라간다.
 *
 * ⚠️ `UserPreferencesSnapshot`에 얹지 않는다(함정 2 — 자동저장이 스냅샷을 처음부터 다시 조립해 배선되지 않은 필드를 초기화한다).
 * 이름이 앱 접두사(`go_ai_coach_`)로 시작해 「앱 최초 실행 상태로 되돌리기」가 함께 지운다(함정 55) — 지워지면 기본값(켜짐)으로 돌아갈 뿐이다.
 * 기본값이 켜짐인 것은 사용자 결정이다(2026-10-18 — 새 설치·업데이트 모두).
 */
internal class BackgroundMusicStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(EnabledKey, DefaultEnabled)

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(EnabledKey, enabled) }
    }

    /**
     * 지금 값으로 시작해 바뀔 때마다 새 값을 낸다. ⚠️ `SharedPreferences`는 듣는 쪽을 약하게 쥐므로 리스너를
     * 이 흐름이 살아 있는 동안 여기서 붙잡아 둔다 — 람다를 바로 넘기면 수거돼 조용히 멈춘다.
     */
    fun enabledChanges(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == EnabledKey) trySend(isEnabled())
        }
        send(isEnabled())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    private companion object {
        const val PrefsName = "go_ai_coach_background_music"
        const val EnabledKey = "enabled"
        const val DefaultEnabled = true
    }
}
