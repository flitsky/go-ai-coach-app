package com.worksoc.goaicoach.persistence

import android.content.Context
import androidx.core.content.edit
import com.worksoc.goaicoach.application.dailyfortune.DailyFortuneProgress
import com.worksoc.goaicoach.application.dailyfortune.DailyFortuneStorePort

/**
 * 실험실 「오늘의 운세」의 진행도(백로그 #276) — 그날 뽑은 랜덤 운세(카탈로그 순번)와 갱신 횟수. 판정은 shared의 `DailyFortune.kt` 한 곳이다.
 *
 * 기기에만 남는다(사용자 2026-10-23 — 로그인 없음). 받는 개인정보가 없어 개인정보처리방침 · 데이터 보안 양식에 적을 것이 없다(함정 60).
 * 지웠다 다시 깔면 안드로이드 자동 백업으로 돌아온다(`shared_prefs`는 제외 규칙에 걸리지 않는다 — `DailyQuoteStore`와 같다).
 *
 * ⚠️ `UserPreferencesSnapshot`에 얹지 않는다(함정 2). 이름이 앱 접두사(`go_ai_coach_`)로 시작해 「앱 최초 실행 상태로 되돌리기」가 함께 지운다(함정 55).
 * 권한이 아니라 정식 릴리즈 초기화(함정 6)는 건드리지 않는다 — 지워도 그날 다시 뽑을 수 있을 뿐이다.
 * ⚠️ 순번은 `DailyFortuneCatalog.fortunes`의 자리다 — 출시 뒤에는 새 글을 맨 끝에만 더한다(그 KDoc).
 */
internal class DailyFortuneStore(context: Context) : DailyFortuneStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun load(): DailyFortuneProgress = DailyFortuneProgress(
        day = prefs.getLong(DayKey, NoValue).takeIf { it != NoValue },
        fortuneIndex = prefs.getInt(FortuneIndexKey, NoIndex).takeIf { it != NoIndex },
        refreshesUsed = prefs.getInt(RefreshesUsedKey, 0),
    )

    override fun save(progress: DailyFortuneProgress) {
        prefs.edit {
            putLong(DayKey, progress.day ?: NoValue)
            putInt(FortuneIndexKey, progress.fortuneIndex ?: NoIndex)
            putInt(RefreshesUsedKey, progress.refreshesUsed)
        }
    }

    private companion object {
        const val PrefsName = "go_ai_coach_daily_fortune"
        const val DayKey = "day"
        const val FortuneIndexKey = "fortune_index"
        const val RefreshesUsedKey = "refreshes_used"
        const val NoValue = -1L
        const val NoIndex = -1
    }
}
