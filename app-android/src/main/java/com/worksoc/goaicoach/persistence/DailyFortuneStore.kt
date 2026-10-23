package com.worksoc.goaicoach.persistence

import android.content.Context
import androidx.core.content.edit
import com.worksoc.goaicoach.application.dailyfortune.DailyFortuneProgress

/**
 * 실험실 「오늘의 운세」의 진행도(백로그 #276) — 고른 띠 · 별자리와, 그날 쓴 갱신 횟수. 판정은 shared의 `DailyFortune.kt` 한 곳이다.
 *
 * 고른 것은 **이 기기에만** 남는다(사용자 2026-10-23 — 로그인 없음). 띠 · 별자리는 나이를 알려 주지 않아 개인정보처리방침 · 데이터 보안 양식에 적을 것이 없다(함정 60).
 * 지웠다 다시 깔면 안드로이드 자동 백업으로 돌아온다(`shared_prefs`는 제외 규칙에 걸리지 않는다 — `DailyQuoteStore`와 같다).
 *
 * ⚠️ `UserPreferencesSnapshot`에 얹지 않는다(함정 2). 이름이 앱 접두사(`go_ai_coach_`)로 시작해 「앱 최초 실행 상태로 되돌리기」가 함께 지운다(함정 55).
 * 권한이 아니라 정식 릴리즈 초기화(함정 6)는 건드리지 않는다 — 지워도 다시 고르면 될 뿐이다.
 * ⚠️ 띠 · 별자리 값은 `FortuneSign.key`(`zodiac:dragon` · `star:leo`)다 — enum 이름이 아니라 따로 적은 키라 enum을 개명해도 풀리지 않는다(함정 1).
 */
internal class DailyFortuneStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    fun load(): DailyFortuneProgress = DailyFortuneProgress(
        signKey = prefs.getString(SignKey, null),
        day = prefs.getLong(DayKey, NoDay).takeIf { it != NoDay },
        refreshesUsed = prefs.getInt(RefreshesUsedKey, 0),
    )

    fun save(progress: DailyFortuneProgress) {
        prefs.edit {
            putString(SignKey, progress.signKey)
            putLong(DayKey, progress.day ?: NoDay)
            putInt(RefreshesUsedKey, progress.refreshesUsed)
        }
    }

    private companion object {
        const val PrefsName = "go_ai_coach_daily_fortune"
        const val SignKey = "sign"
        const val DayKey = "day"
        const val RefreshesUsedKey = "refreshes_used"
        const val NoDay = -1L
    }
}
