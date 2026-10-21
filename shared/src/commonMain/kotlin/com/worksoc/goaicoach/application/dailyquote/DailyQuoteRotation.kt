package com.worksoc.goaicoach.application.dailyquote

import kotlin.random.Random

/**
 * 「오늘의 한 줄 명언」의 진행도(백로그 #273, 사용자 2026-10-20 · 10-21).
 *
 * 요구(사용자): **처음 한 번 무작위로 섞은 차례**를 간직하고 그 차례대로 하루에 하나씩 보인다. 섞은 차례와 **이미 보인 명언**은 따로 둔다 —
 * 지웠다 다시 깔아도 기기 백업 복원으로 이어지고, 복원이 없으면 새로 섞는다(설치마다 다른 차례 — 고정 씨앗을 쓰지 않는다).
 *
 * @property order 섞은 차례(명언 id). 설치마다 한 번 섞고, 한 바퀴를 다 돌면 다시 섞는다.
 * @property shown 이번 바퀴에 이미 보인 명언 id. 차례와 따로 둔다 — 앱을 고치며 명언을 더하거나 빼도 「본 것」은 그대로 남는다.
 * @property todayDay 오늘의 명언을 고른 날(출석과 같은 UTC 하루 번호 — `application.attendance.utcDayIndex`).
 * @property todayId 그날의 명언 id.
 * @property dismissedDay ✕로 닫은 날 — 그날은 다시 띄우지 않는다(다음 날 다음 명언, 사용자 2026-10-21).
 */
data class DailyQuoteProgress(
    val order: List<String> = emptyList(),
    val shown: Set<String> = emptySet(),
    val todayDay: Long? = null,
    val todayId: String? = null,
    val dismissedDay: Long? = null,
) {
    fun isDismissedOn(day: Long): Boolean = dismissedDay == day
}

/** [pickDailyQuote]의 답 — [quoteId]가 `null`이면 실을 명언이 없다. [changed]면 [progress]를 저장한다. */
data class DailyQuotePick(
    val quoteId: String?,
    val progress: DailyQuoteProgress,
    val changed: Boolean,
)

/**
 * 오늘([today]) 보일 명언을 고른다.
 *
 * - 오늘 이미 골랐으면 그것을 그대로 돌려준다. 폰 시계가 뒤로 갔을 때(오늘 < 고른 날)도 그대로다 — 시계를 돌려 명언을 넘기지 못하게.
 * - 날이 바뀌었으면 섞은 차례에서 **아직 안 보인 첫 명언**이 오늘의 것이 되고, 그 자리에서 「보인 것」에 적는다.
 * - 앱에 실린 명언([catalog])이 바뀌었으면 차례를 맞춘다 — 빠진 것은 지우고, 새로 든 것은 아직 안 보인 자리들 사이에 무작위로 끼운다.
 * - 한 바퀴를 다 돌면 다시 섞는다. 이때 **직전 명언이 곧바로 다시 나오지 않게** 한다(스레드가 정한 기본값, 2026-10-21).
 *
 * @param random 섞기에 쓴다. 앱은 고정 씨앗 없는 `Random.Default`를 넘긴다 — 설치마다 차례가 달라야 한다.
 */
fun pickDailyQuote(
    catalog: List<String>,
    progress: DailyQuoteProgress,
    today: Long,
    random: Random,
): DailyQuotePick {
    val ids = catalog.distinct()
    if (ids.isEmpty()) return DailyQuotePick(quoteId = null, progress = progress, changed = false)
    val current = progress.todayId
    val pickedDay = progress.todayDay
    if (current != null && pickedDay != null && today <= pickedDay && current in ids) {
        return DailyQuotePick(quoteId = current, progress = progress, changed = false)
    }
    val available = ids.toSet()
    var shown = progress.shown.intersect(available)
    var order = reconcileOrder(progress.order, ids, shown, random)
    if (order.all { it in shown }) {
        order = reshuffleAvoiding(ids, last = current, random = random)
        shown = emptySet()
    }
    val next = order.first { it !in shown }
    val updated = progress.copy(order = order, shown = shown + next, todayDay = today, todayId = next)
    return DailyQuotePick(quoteId = next, progress = updated, changed = true)
}

/** 섞은 차례를 지금 실린 명언에 맞춘다 — 처음이면 통째로 섞고, 빠진 것은 지우고, 새 것은 아직 안 보인 자리들 사이에 무작위로 끼운다. */
private fun reconcileOrder(order: List<String>, ids: List<String>, shown: Set<String>, random: Random): List<String> {
    val available = ids.toSet()
    val kept = order.distinct().filter { it in available }.toMutableList()
    if (kept.isEmpty()) return ids.shuffled(random)
    val keptSet = kept.toSet()
    // 보인 것은 차례의 앞쪽에 모여 있다 — 새 명언을 그 뒤로만 끼워야 이번 바퀴에 빠짐없이 한 번씩 나온다.
    val firstUnshown = kept.indexOfFirst { it !in shown }.let { if (it < 0) kept.size else it }
    ids.filter { it !in keptSet }.shuffled(random).forEach { newcomer ->
        kept.add(random.nextInt(firstUnshown, kept.size + 1), newcomer)
    }
    return kept
}

private fun reshuffleAvoiding(ids: List<String>, last: String?, random: Random): List<String> {
    val shuffled = ids.shuffled(random).toMutableList()
    if (shuffled.size > 1 && shuffled.first() == last) {
        val swapWith = random.nextInt(1, shuffled.size)
        shuffled[0] = shuffled[swapWith]
        shuffled[swapWith] = last
    }
    return shuffled
}
