package com.worksoc.goaicoach.application.dailyfortune

import kotlin.random.Random

/*
 * 실험실 「오늘의 운세」(백로그 #276, 사용자 2026-10-23) — 규칙은 이 파일 한 곳이다.
 * 운세 글은 화면 쪽 `ui/l10n/DailyFortuneCatalog.kt`에, 저장은 4계층 어댑터 `persistence/DailyFortuneStore.kt`에 있다.
 *
 * 사용자가 정한 것:
 * - **랜덤 운세**: 설정 없이, 사용자가 **누른 순간의 타임스탬프**로 뽑는다(*"띠, 별자리 운세는 선택의 허들이 있으니, 우선 랜덤 운세 먼저"*). 운세 글은 최소 365개.
 * - 하루의 첫 운세는 무료, **갱신은 광고 1편**(구독자는 광고 없이) · **하루 3번**([DailyFortuneMaxRefreshes]). 점수 · 등급은 없다.
 * - 운을 바꿔도 **앞의 운세가 남는다**(#299, 2026-10-29) — 그날 본 운세가 쌓이고(최대 4개) 화면에서 옆으로 넘겨 본다([DailyFortuneProgress.todaysIndices]).
 * - 띠별 · 별자리별 운세는 근거 데이터가 없어 「(준비 중)」이다(아이디어 I-7) — 여기에는 규칙이 없다.
 *
 * 스레드가 정한 것:
 * - 뽑은 운세는 **그날 내내 남는다** — 앱을 다시 열어도 같은 운세다(다시 열 때마다 새로 뽑으면 광고 갱신이 뜻을 잃는다).
 * - 갱신하면 **그날 본 적 없는 운세**가 나온다 — 타임스탬프로 고른 것이 오늘 본 것이면 그다음 글로 넘긴다(쌓아 보여 주니 같은 글이 두 번 있으면 안 된다, #299).
 * - 하루는 출석의 UTC 하루(`application.attendance.utcDayIndex` — 명언과 같다)라 한국에서는 오전 9시에 바뀐다.
 *   저장된 날과 오늘이 다르면 앞이든 뒤든 새 날로 본다 — 시계를 되감은 사람을 막지 않는다(1일 1회 뽑기와 같은 결, 함정 12 · `FEATURE_ACCESS_PRINCIPLES.md` §8.8).
 */

/** 하루에 광고(구독자는 광고 없이)로 갱신할 수 있는 횟수(사용자 2026-10-23). */
const val DailyFortuneMaxRefreshes: Int = 3

/**
 * 운세의 진행도 — 그날 뽑은 운세와 갱신 횟수.
 *
 * @property day 뽑은 날(UTC 하루 번호). 한 번도 뽑지 않았으면 `null`.
 * @property fortuneIndex [day]에 보이는 운세 — 카탈로그의 몇 번째 글인가.
 * @property refreshesUsed [day]에 쓴 갱신 횟수.
 * @property earlierIndices [day]에 [fortuneIndex] 앞서 본 운세들(본 차례대로) — 운을 바꿀 때마다 그전 운세가 끝에 붙는다(#299).
 */
data class DailyFortuneProgress(
    val day: Long? = null,
    val fortuneIndex: Int? = null,
    val refreshesUsed: Int = 0,
    val earlierIndices: List<Int> = emptyList(),
) {
    /** 오늘([today]) 보일 운세 — 오늘 아직 뽑지 않았거나, 앱을 고치며 글이 줄어 그 번호가 없어졌으면 `null`(다시 뽑는다). */
    fun todaysIndex(today: Long, poolSize: Int): Int? = fortuneIndex?.takeIf { day == today && it in 0 until poolSize }

    /** 오늘 본 운세 전부(본 차례대로, 지금 것이 맨 끝) — 넘겨 보는 쪽들. 오늘 아직 뽑지 않았으면 빈 목록, 없어진 번호는 건너뛴다. */
    fun todaysIndices(today: Long, poolSize: Int): List<Int> {
        val current = todaysIndex(today, poolSize) ?: return emptyList()
        return earlierIndices.filter { it in 0 until poolSize } + current
    }

    /** 오늘 쓴 갱신 횟수 — 저장된 날이 오늘이 아니면 0. */
    fun refreshesUsedOn(today: Long): Int =
        if (day == today) refreshesUsed.coerceIn(0, DailyFortuneMaxRefreshes) else 0

    /** 오늘 남은 갱신 횟수. */
    fun refreshesLeftOn(today: Long): Int = DailyFortuneMaxRefreshes - refreshesUsedOn(today)
}

/**
 * 오늘의 첫 운세를 뽑는다(무료) — [timestampMillis]는 사용자가 누른 순간. 오늘 이미 뽑았으면 `null`(아무것도 바꾸지 않는다).
 * 갱신 횟수와 앞서 본 운세는 새 날이면 비우고, 같은 날 글이 줄어 다시 뽑는 것이면 그대로다.
 */
fun drawDailyFortune(progress: DailyFortuneProgress, today: Long, timestampMillis: Long, poolSize: Int): DailyFortuneProgress? {
    if (progress.todaysIndex(today, poolSize) != null) return null
    val earlier = if (progress.day == today) progress.earlierIndices.filter { it in 0 until poolSize } else emptyList()
    return DailyFortuneProgress(
        day = today,
        fortuneIndex = fortuneIndexAt(timestampMillis, poolSize, excluding = earlier.toSet()),
        refreshesUsed = progress.refreshesUsedOn(today),
        earlierIndices = earlier,
    )
}

/**
 * 한 번 갱신한다 — 오늘 아직 뽑지 않았거나 남은 횟수가 없으면 `null`. 지금 운세는 앞서 본 운세의 끝으로 가고, 새 운세는 오늘 본 어느 것과도 다르다.
 * 광고는 호출부가 먼저 보인다(광고를 본 뒤에 부른다 — 광고가 실패하면 부르지 않는다).
 */
fun refreshDailyFortune(progress: DailyFortuneProgress, today: Long, timestampMillis: Long, poolSize: Int): DailyFortuneProgress? {
    val seen = progress.todaysIndices(today, poolSize).ifEmpty { return null }
    if (progress.refreshesLeftOn(today) <= 0) return null
    return progress.copy(
        fortuneIndex = fortuneIndexAt(timestampMillis, poolSize, excluding = seen.toSet()),
        refreshesUsed = progress.refreshesUsedOn(today) + 1,
        earlierIndices = seen,
    )
}

/**
 * 누른 순간([timestampMillis])으로 고른 글 번호 — 같은 순간이면 어느 기기에서나 같은 답이다(`Random(seed)`는 공통 구현).
 * [excluding]에 든 글이면 그다음 글로 넘긴다(글이 모자라 다 들었으면 그대로).
 */
internal fun fortuneIndexAt(timestampMillis: Long, poolSize: Int, excluding: Set<Int> = emptySet()): Int {
    require(poolSize > 0) { "운세 글이 하나도 없다" }
    val picked = Random(timestampMillis).nextInt(poolSize)
    return (0 until poolSize).asSequence().map { (picked + it) % poolSize }.firstOrNull { it !in excluding } ?: picked
}
