package com.worksoc.goaicoach.shared

import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit

/**
 * Internal search profiles remain useful for engine defaults and benchmarks.
 * They are deliberately not exposed as user-facing time-limit choices.
 */
enum class SearchTimeProfile(
    val visits: Int,
    val defaultMillis: Long,
) {
    B16(
        visits = 16,
        defaultMillis = 1_000L,
    ),
    B32(
        visits = 32,
        defaultMillis = 2_000L,
    ),
    B64(
        visits = 64,
        defaultMillis = 3_000L,
    ),
    ;
}

/** A single user choice that caps every normal AI and Top Moves search. */
enum class SearchTimeLimit(
    val maximumMillis: Long?,
) {
    Off(maximumMillis = null),
    WithinOneSecond(maximumMillis = 1_000L),
    WithinThreeSeconds(maximumMillis = 3_000L),
    WithinFiveSeconds(maximumMillis = 5_000L),
    WithinTenSeconds(maximumMillis = 10_000L),
    ;

    fun nextLonger(): SearchTimeLimit =
        entries.getOrElse(ordinal + 1) { this }

    companion object {
        /** Rounds a historical or measured duration up to a supported user choice. */
        fun ceilingFor(millis: Long): SearchTimeLimit =
            entries
                .filter { it.maximumMillis != null }
                .firstOrNull { limit -> millis.coerceAtLeast(1L) <= requireNotNull(limit.maximumMillis) }
                ?: WithinTenSeconds

        /**
         * ⚠️ **읽지 못한 값의 기본도 [DefaultSearchTimeLimit]이다** — 여기만 옛 기본값으로
         * 남겨 두면, 저장이 깨진 사용자만 조용히 3초로 돌아간다.
         */
        fun fromStoredName(name: String?): SearchTimeLimit =
            entries.firstOrNull { it.name == name } ?: DefaultSearchTimeLimit
    }
}

/**
 * Keeps search-time policy separate from AI strength: level determines visits,
 * while this value only caps elapsed search time.
 */
/**
 * AI 한 수에 허용하는 **최대 탐색 시간**의 기본값 — 2026-09-22에 3초에서 **10초**로 올렸다.
 *
 * ⚠️ **이미 고른 사용자는 그대로다** — 저장된 값이 있으면 그것이 이긴다. 바뀌는 것은 새로
 * 설치한 사용자와, 아직 이 설정을 만져 본 적이 없는 사용자다.
 * ⚠️ **와치독 한도가 이 값에서 계산된다**(`engineTurnWatchdogTimeoutMillisFor`) — 여기를
 * 내리면 「엔진 응답 지연」 팝업이 그만큼 빨리 뜬다. 둘은 같이 움직인다.
 */
val DefaultSearchTimeLimit: SearchTimeLimit = SearchTimeLimit.WithinTenSeconds

data class SearchTimeSettings(
    val limit: SearchTimeLimit = DefaultSearchTimeLimit,
) {
    fun applyTo(limit: AnalysisLimit): AnalysisLimit =
        limit.copy(timeMillis = this.limit.maximumMillis)

    fun withLimit(limit: SearchTimeLimit): SearchTimeSettings =
        copy(limit = limit)

    fun normalized(): SearchTimeSettings = this

    fun summaryText(): String =
        limit.maximumMillis?.let { millis -> "Time limit ${millis}ms" } ?: "Time limit OFF"
}
