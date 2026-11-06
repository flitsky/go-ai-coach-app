package com.worksoc.goaicoach.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.worksoc.goaicoach.application.attendance.MillisPerUtcDay
import com.worksoc.goaicoach.application.attendance.utcDayIndex
import com.worksoc.goaicoach.application.dailyfortune.DailyFortuneProgress
import com.worksoc.goaicoach.application.dailyfortune.DailyFortuneStorePort
import com.worksoc.goaicoach.application.dailyfortune.drawDailyFortune
import com.worksoc.goaicoach.application.dailyfortune.refreshDailyFortune
import com.worksoc.goaicoach.application.premium.port.AdRewardFailureReason
import com.worksoc.goaicoach.application.premium.port.AdRewardOutcome
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.l10n.DailyFortuneCatalog
import com.worksoc.goaicoach.ui.l10n.FortuneText
import kotlinx.coroutines.delay

/**
 * 「오늘의 운세」의 화면 상태 **하나**(백로그 #303) — 대국 페이지 맨 위의 운세 줄과 실험실의 운세 카드(#276)가 이것을 나눠 쓴다.
 * 한쪽에서 뽑거나 바꾸면 다른 쪽도 그대로 따라온다(전에는 카드가 저장소를 혼자 읽어, 두 자리가 생기면 서로 어긋났다).
 * 홈(`GoCoachHomeScreen`)이 [rememberDailyFortuneState]로 한 번 만들어 두 페이지에 넘긴다.
 *
 * 규칙(누른 순간으로 뽑기 · 하루 3번 갱신 · UTC 하루)은 shared `application/dailyfortune/DailyFortune.kt` 한 곳이고, 여기는 저장 · 광고 · 날 바뀜만 잇는다.
 * ⚠️ 시계는 `currentEpochMillis`를 직접 읽는다 — 출석과 같다(함정 12).
 */
@Stable
internal class DailyFortuneState(private val store: DailyFortuneStorePort) {
    var progress by mutableStateOf(store.load())
        private set

    /** 지금의 UTC 하루. 화면을 열어 둔 채 날이 바뀌면 [rememberDailyFortuneState]가 다시 읽는다 — 그때 운세 자리는 「뽑기」로 돌아간다. */
    var today by mutableLongStateOf(utcDayIndex(currentEpochMillis()))
        private set

    /** 광고를 보는 중 — 두 자리 모두 단추 대신 도는 표시를 그린다(한쪽에서 누른 사이 다른 쪽에서 또 누르지 않게). */
    var busy by mutableStateOf(false)
        private set

    private val poolSize: Int get() = DailyFortuneCatalog.fortunes.size

    /** 오늘 본 운세들의 순번(#299) — 처음 뽑은 것부터, 새로 바꾼 운세가 맨 끝이다. 비어 있으면 아직 안 뽑았다. */
    val todays: List<Int> get() = progress.todaysIndices(today, poolSize)

    /** 지금의 운세 — 오늘 가장 나중에 뽑거나 바꾼 것. 아직 안 뽑았으면 `null`. */
    val current: FortuneText? get() = todays.lastOrNull()?.let { index -> DailyFortuneCatalog.fortunes.getOrNull(index) }

    /** 오늘 남은 갱신 횟수(0 ~ 3). */
    val refreshesLeft: Int get() = progress.refreshesLeftOn(today)

    /** 오늘의 첫 운세를 뽑는다 — **누른 순간**의 타임스탬프가 운세를 정한다(사용자 2026-10-23). 무료다. 이미 뽑았으면 아무 일도 없다. */
    fun draw() {
        val now = currentEpochMillis()
        val day = utcDayIndex(now)
        drawDailyFortune(store.load(), day, now, poolSize)?.let { drawn -> commit(drawn) }
        today = day
    }

    /**
     * 운을 바꾼다 — 구독자가 아니면([withAd]) 보상형 광고를 끝까지 본 뒤에만. 광고가 없거나 끝까지 보지 않았으면 운은 그대로이고 [onAdFailed]로 알린다.
     * 광고를 본 뒤의 순간으로 다시 뽑는다 — 광고를 보는 사이 날이 바뀌었으면 갱신이 아니라 새 날의 뽑기가 남는다(shared 규칙).
     */
    suspend fun refresh(
        withAd: Boolean,
        showAd: suspend () -> AdRewardOutcome,
        onAdFailed: (AdRewardFailureReason) -> Unit,
    ) {
        if (busy) return
        busy = true
        try {
            val outcome = if (withAd) showAd() else null
            if (outcome is AdRewardOutcome.NotRewarded) {
                onAdFailed(outcome.reason)
                return
            }
            val now = currentEpochMillis()
            val day = utcDayIndex(now)
            refreshDailyFortune(store.load(), day, now, poolSize)?.let { refreshed -> commit(refreshed) }
            today = day
        } finally {
            busy = false
        }
    }

    /** 날이 바뀌었는지 다시 읽는다. */
    fun rereadToday() {
        today = utcDayIndex(currentEpochMillis())
    }

    private fun commit(next: DailyFortuneProgress) {
        store.save(next)
        progress = next
    }
}

/** 홈에서 한 번 — 다음 UTC 자정까지 기다렸다가 오늘을 다시 읽는다(화면을 열어 둔 채 날이 바뀌어도 「뽑기」로 돌아간다). */
@Composable
internal fun rememberDailyFortuneState(): DailyFortuneState {
    val platform = LocalAppPlatform.current
    val state = remember(platform) { DailyFortuneState(platform.stores.dailyFortune) }
    LaunchedEffect(state, state.today) {
        val untilNextDay = (state.today + 1) * MillisPerUtcDay - currentEpochMillis()
        delay(untilNextDay.coerceAtLeast(1_000))
        state.rereadToday()
    }
    return state
}
