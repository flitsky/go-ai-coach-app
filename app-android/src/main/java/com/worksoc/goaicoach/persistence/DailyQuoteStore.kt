package com.worksoc.goaicoach.persistence

import android.content.Context
import androidx.core.content.edit
import com.worksoc.goaicoach.application.dailyquote.DailyQuoteProgress
import com.worksoc.goaicoach.application.dailyquote.DailyQuoteStorePort

/**
 * 「오늘의 한 줄 명언」의 진행도(백로그 #273) — 섞은 차례와 이미 보인 명언을 **따로** 둔다(사용자 2026-10-20). 판정은 shared의 `pickDailyQuote` 한 곳이다.
 *
 * 지웠다 다시 깔아도 이어지는 길은 안드로이드 자동 백업이다 — `shared_prefs`는 제외 규칙(`backup_rules.xml` · `data_extraction_rules.xml`)에
 * 걸리지 않아 따로 적지 않아도 실린다. 자동 백업은 하루 한 번쯤 돌므로 복원본에는 마지막 며칠의 「본 것」이 빠져 있을 수 있다 — 그 며칠의
 * 명언이 한 번 더 나올 뿐이다. 복원이 없으면 비어 있는 채로 시작해 새로 섞는다(설치마다 다른 차례).
 *
 * ⚠️ `UserPreferencesSnapshot`에 얹지 않는다(함정 2). 이름이 앱 접두사(`go_ai_coach_`)로 시작해 「앱 최초 실행 상태로 되돌리기」가 함께 지운다
 * (함정 55 — 에뮬레이터에서 처음 설치한 상태를 다시 볼 때 쓴다). 권한이 아니라 정식 릴리즈 초기화(함정 6)는 건드리지 않는다
 * (`LayeringContractTest.releaseResetClearsEntitlementsOnlyAndSparesUserContent`).
 * ⚠️ 명언 id는 저장값이다 — `DailyQuoteCatalog`의 id를 바꾸면 그 명언을 본 기록이 끊긴다(함정 1과 같은 결).
 */
internal class DailyQuoteStore(context: Context) : DailyQuoteStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun load(): DailyQuoteProgress = DailyQuoteProgress(
        order = prefs.getString(OrderKey, null).toIds(),
        shown = prefs.getString(ShownKey, null).toIds().toSet(),
        todayDay = prefs.getLong(TodayDayKey, NoDay).takeIf { it != NoDay },
        todayId = prefs.getString(TodayIdKey, null),
        dismissedDay = prefs.getLong(DismissedDayKey, NoDay).takeIf { it != NoDay },
    )

    override fun save(progress: DailyQuoteProgress) {
        prefs.edit {
            putString(OrderKey, progress.order.joinToString(Separator))
            putString(ShownKey, progress.shown.joinToString(Separator))
            putLong(TodayDayKey, progress.todayDay ?: NoDay)
            putString(TodayIdKey, progress.todayId)
            putLong(DismissedDayKey, progress.dismissedDay ?: NoDay)
        }
    }

    private fun String?.toIds(): List<String> = this?.split(Separator)?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val PrefsName = "go_ai_coach_daily_quote"
        const val OrderKey = "order"
        const val ShownKey = "shown"
        const val TodayDayKey = "today_day"
        const val TodayIdKey = "today_id"
        const val DismissedDayKey = "dismissed_day"
        const val NoDay = -1L
        const val Separator = ","
    }
}
