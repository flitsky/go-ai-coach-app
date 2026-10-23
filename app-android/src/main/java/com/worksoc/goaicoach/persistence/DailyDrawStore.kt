package com.worksoc.goaicoach.persistence

import android.content.Context
import androidx.core.content.edit
import com.worksoc.goaicoach.application.dailydraw.DailyDrawStorePort

/**
 * 실험실 「1일 1회 뽑기」를 마지막으로 뽑은 때(백로그 #283) — 쿨타임의 기준. 판정은 shared의 `dailyDrawAvailability` 한 곳이다.
 *
 * ⚠️ `UserPreferencesSnapshot`에 얹지 않는다(함정 2). 이름이 앱 접두사(`go_ai_coach_`)로 시작해 「앱 최초 실행 상태로 되돌리기」가 함께 지운다(함정 55).
 * 권한이 아니라 쿨타임 기록이라 정식 릴리즈 초기화(함정 6)는 건드리지 않는다 — 지워도 받은 상품은 그대로이고, 쿨타임이 풀릴 뿐이다.
 */
internal class DailyDrawStore(context: Context) : DailyDrawStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun lastDrawnAtMillis(): Long? = prefs.getLong(LastDrawnKey, NoTime).takeIf { it != NoTime }

    override fun markDrawn(atMillis: Long) {
        prefs.edit { putLong(LastDrawnKey, atMillis) }
    }

    private companion object {
        const val PrefsName = "go_ai_coach_daily_draw"
        const val LastDrawnKey = "last_drawn_at_millis"
        const val NoTime = Long.MIN_VALUE
    }
}
