package com.worksoc.goaicoach.persistence

import android.content.Context
import androidx.core.content.edit
import com.worksoc.goaicoach.application.consumable.AdSkipTicketAdStorePort

/**
 * 「광고 보고 광고 스킵권 받기」를 마지막으로 받은 때(백로그 #283) — 하루 한 번 쿨타임의 기준. 판정은 shared의 `adSkipTicketAdAvailability` 한 곳이다.
 *
 * ⚠️ `UserPreferencesSnapshot`에 얹지 않는다(함정 2). 이름이 앱 접두사(`go_ai_coach_`)로 시작해 「앱 최초 실행 상태로 되돌리기」가 함께 지운다(함정 55).
 * 권한이 아니라 쿨타임 기록이라 정식 릴리즈 초기화(함정 6)는 건드리지 않는다 — 지워도 스킵권 재고는 그대로이고, 쿨타임이 풀릴 뿐이다.
 */
internal class AdSkipTicketAdStore(context: Context) : AdSkipTicketAdStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun lastEarnedAtMillis(): Long? = prefs.getLong(LastEarnedKey, NoTime).takeIf { it != NoTime }

    override fun markEarned(atMillis: Long) {
        prefs.edit { putLong(LastEarnedKey, atMillis) }
    }

    private companion object {
        const val PrefsName = "go_ai_coach_ad_skip_ticket_ad"
        const val LastEarnedKey = "last_earned_at_millis"
        const val NoTime = Long.MIN_VALUE
    }
}
