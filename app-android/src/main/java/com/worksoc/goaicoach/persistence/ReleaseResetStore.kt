package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.lifecycle.ReleaseResetPolicy

/**
 * 4계층 — **정식 릴리즈 초기화**가 어느 세대까지 적용됐는지만 담는다(백로그 #63).
 *
 * ⚠️ **다른 저장소에 얹지 않고 따로 둔 이유**: 이 값은 초기화의 **대상이 아니라 기준**이다.
 * 만약 권한 저장소 중 하나에 얹었다면 초기화가 자기 자신을 지워, 다음 실행에서 또 초기화되는
 * 무한 루프가 된다.
 *
 * ⚠️ 저장에 [android.content.SharedPreferences.Editor.commit]을 쓴다(비동기 `apply`가 아니라).
 * 초기화 직후 프로세스가 죽으면 마커가 유실돼 **다음 실행에서 한 번 더 밀기** 때문이다 —
 * 초기화 자체는 멱등이라 치명적이진 않지만, 그 사이 사용자가 얻은 것이 있으면 잃는다.
 */
internal class ReleaseResetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    /** 지금까지 적용된 초기화 세대. 마커가 없으면 [ReleaseResetPolicy.NoResetApplied]. */
    fun lastAppliedGeneration(): Int =
        prefs.getInt(GenerationKey, ReleaseResetPolicy.NoResetApplied)

    fun markApplied(generation: Int) {
        prefs.edit()
            .putInt(GenerationKey, generation)
            .commit()
    }

    /**
     * 초기화 안내를 아직 못 보여줬는가(백로그 #63).
     *
     * ⚠️ **"초기화가 돌았는가"가 아니라 "실제로 지운 것이 있었는가"다.** 초기화는 신규 설치에서도
     * 도는데(지울 것이 없어 무해하다), 그걸 기준으로 삼으면 **처음 설치한 사람에게 "테스트 기록이
     * 초기화됐다"고 알리는** 꼴이 된다.
     *
     * ⚠️ 메모리가 아니라 저장소에 두는 이유: 안내를 보기 전에 앱이 죽으면 **영영 못 본다.**
     */
    fun isNoticePending(): Boolean = prefs.getBoolean(NoticePendingKey, false)

    fun markNoticePending() {
        prefs.edit().putBoolean(NoticePendingKey, true).commit()
    }

    fun clearNoticePending() {
        prefs.edit().putBoolean(NoticePendingKey, false).apply()
    }

    private companion object {
        const val PrefsName = "go_ai_coach_release_reset"
        const val GenerationKey = "applied_generation"
        const val NoticePendingKey = "notice_pending"
    }
}
