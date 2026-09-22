package com.worksoc.goaicoach.persistence

import android.content.Context

/**
 * 사용자가 자기 자신에게 붙인 이름(백로그 #165). 앱 어디에도 **사용자 자신을 가리키는
 * 이름·그림이 0건**이던 것을 여기서 처음 만든다.
 *
 * ## ⚠️ 왜 [UserPreferencesStore]가 아닌가
 * 그쪽은 저장할 때마다 `UserPreferencesSnapshot`을 **통째로 다시 만든다.** 실을 꿰야 할 자리가
 * 열 군데가 넘고 **하나만 빠져도 그 필드가 조용히 초기값으로 되돌아간다**(함정 2). 이 저장소가
 * 그 사고를 실제로 낸 자리라, 같은 판단으로 독립 저장소를 세운 선례가 이미 셋 있다
 * (`DeveloperModeStore`·`AttendanceStore`·`BotCollectionStore`).
 *
 * ## ⚠️ 접두사는 반드시 `go_ai_coach_`
 * `DeveloperModeResetCoordinator`가 **그 접두사로 시작하는 prefs만** 지운다 — 접두사가 없으면
 * 개발자 모드 초기화가 이 값을 **건너뛰어**, 초기화했는데 남의 닉네임이 남는다.
 *
 * ## ⚠️ 정식 릴리즈 초기화(`ReleaseResetCoordinator`)에는 **넣지 않는다**
 * 그것이 지우는 것은 **권한 저장소 넷**이다(함정 6). 닉네임은 권한이 아니라 취향이라, 지우면
 * 사용자가 지은 이름이 업데이트 한 번에 사라진다. **판단해서 뺀 것이지 잊은 것이 아니다.**
 */
internal class UserProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    /** 저장된 닉네임. 한 번도 지은 적이 없으면 `null`(빈 문자열도 `null`로 접는다). */
    fun nickname(): String? = prefs.getString(NicknameKey, null)?.takeIf { it.isNotBlank() }

    /**
     * 닉네임을 저장한다. 앞뒤 공백을 걷고 [NicknameMaxLength]까지 자른다.
     *
     * ⚠️ **빈 문자열은 지우는 것으로 친다** — 사용자가 다 지우고 확인하면 「이름 없음」으로
     * 되돌아가야 한다. 빈 문자열을 그대로 저장하면 원 안에 아무것도 없는 아바타가 남는다.
     */
    fun saveNickname(raw: String) {
        val trimmed = sanitizeNickname(raw)
        prefs.edit().apply {
            if (trimmed == null) remove(NicknameKey) else putString(NicknameKey, trimmed)
        }.apply()
    }

    internal companion object {
        /**
         * 닉네임 길이 상한. **글자 수이지 바이트가 아니다** — 한글·한자 한 글자도 1로 센다.
         * ⚠️ 상한이 있는 이유는 저장 용량이 아니라 **한 줄에 들어가야 하기 때문**이다(함정 21:
         * CJK는 폭이 두 배다). 늘릴 때는 마이 페이지를 네 언어로 다시 볼 것.
         */
        const val NicknameMaxLength = 12

        private const val PrefsName = "go_ai_coach_user_profile"
        private const val NicknameKey = "nickname"

        /** 저장 전·입력 중에 **같은 규칙**을 쓴다 — 화면이 자르는 길이와 저장이 자르는 길이가 다르면 안 된다. */
        fun sanitizeNickname(raw: String): String? =
            raw.trim().take(NicknameMaxLength).takeIf { it.isNotBlank() }
    }
}
