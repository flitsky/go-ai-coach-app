package com.worksoc.goaicoach.persistence

import android.content.Context
import android.content.SharedPreferences
import com.worksoc.goaicoach.application.profile.UserNicknamePolicy

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
 *
 * ## ⚠️ 닉네임 규칙은 여기 없다
 * 몇 글자까지 받고 무엇을 남기는지는 `shared`의 [UserNicknamePolicy]가 정한다 — 이 저장소는 그것을
 * **부르기만** 한다(refactor backlog #85, 원칙 문서 4계층 "포트가 아는 것" ⓑ). 규칙을 여기 다시 적으면
 * 입력 팝업이 자르는 길이와 저장이 자르는 길이가 한쪽만 바뀌는 날 조용히 갈라진다.
 */
internal class UserProfileStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE),
    )

    /** 저장된 닉네임. 한 번도 지은 적이 없으면 `null`(빈 문자열도 `null`로 접는다). */
    fun nickname(): String? = prefs.getString(NicknameKey, null)?.takeIf { it.isNotBlank() }

    /**
     * 닉네임을 저장한다. 무엇을 남길지는 [UserNicknamePolicy.sanitize]가 정한다(앞뒤 공백을 걷고
     * [UserNicknamePolicy.MaxLength]까지 자른다).
     *
     * ⚠️ **그 결과가 `null`이면 지운다** — 사용자가 다 지우고 확인하면 「이름 없음」으로
     * 되돌아가야 한다. 빈 문자열을 그대로 저장하면 원 안에 아무것도 없는 아바타가 남는다.
     */
    fun saveNickname(raw: String) {
        val sanitized = UserNicknamePolicy.sanitize(raw)
        prefs.edit().apply {
            if (sanitized == null) remove(NicknameKey) else putString(NicknameKey, sanitized)
        }.apply()
    }

    private companion object {
        const val PrefsName = "go_ai_coach_user_profile"
        const val NicknameKey = "nickname"
    }
}
