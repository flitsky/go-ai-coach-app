package com.worksoc.goaicoach.persistence

import android.content.Context

/**
 * 화면 표시 언어 선택을 저장한다(백로그 #34).
 *
 * **왜 필요한가**: 그전까지 언어는 `ProvideUiLanguage`의 `remember` 상태뿐이라 앱을 껐다 켜면
 * 항상 한국어로 돌아갔다. 글꼴 배율 변경처럼 액티비티가 재생성되는 상황에서도 초기화됐다.
 * 홈 우상단의 임시 칩일 때는 넘어갔지만, 선택 UI가 **설정 화면 안으로 들어오면서**
 * "저장되지 않는 설정"이 되어 그대로 둘 수 없었다.
 *
 * ⚠️ [UserPreferencesStore]와 **별도의** SharedPreferences 파일을 쓴다 — [DeveloperModeStore]와
 * 같은 이유다. 그쪽은 저장할 때마다 `UserPreferencesSnapshot`을 통째로 다시 조립하는데
 * (`buildUserPreferencesSnapshot` → `toUserPreferencesSnapshot`), 그 조립부에 배선되지 않은
 * 필드는 저장 시점에 조용히 기본값으로 되돌아간다. 실제로 `hasSeenOnboarding`과
 * `gameSetupUxMode`가 조립부에 없다. 언어를 거기 끼워 넣으면 같은 함정에 빠진다.
 *
 * 저장은 **enum 이름 문자열**로 한다. 값을 못 알아보면(언어가 삭제·개명된 빌드로 롤백 등)
 * `null`을 돌려주고, 호출부는 기본 언어로 시작한다 — 저장된 값 하나 때문에 앱이 죽지 않는다.
 */
internal class UiLanguageStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    /** 저장된 언어 이름. 고른 적이 없거나 알 수 없는 값이면 `null`. */
    fun loadName(): String? = prefs.getString(LanguageKey, null)

    fun save(languageName: String) {
        prefs.edit().putString(LanguageKey, languageName).apply()
    }

    private companion object {
        const val PrefsName = "go_ai_coach_ui_language"
        const val LanguageKey = "language"
    }
}
