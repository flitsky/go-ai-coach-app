package com.worksoc.goaicoach.persistence

import android.content.Context

/**
 * 설정 화면의 버전 텍스트를 10번 두드리면 활성화되는 "개발자 테스트" 섹션 노출 여부를
 * 저장한다. [UserPreferencesStore]와 별도의 SharedPreferences 파일을 쓴다 — 그쪽은
 * 전체 [com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot]을 통째로
 * 다시 만들어 저장하는 autosave 패턴이라, 그 흐름에 배선되지 않은 필드는 저장 시점에
 * 조용히 초기값으로 되돌아간다. 이 플래그는 그 위험을 피하기 위해 독립된 저장소를 쓴다.
 */
internal class DeveloperModeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(EnabledKey, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(EnabledKey, enabled).apply()
    }

    private companion object {
        const val PrefsName = "go_ai_coach_developer_mode"
        const val EnabledKey = "enabled"
    }
}
