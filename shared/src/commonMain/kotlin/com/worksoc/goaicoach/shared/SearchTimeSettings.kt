package com.worksoc.goaicoach.shared

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

        fun fromStoredName(name: String?): SearchTimeLimit =
            entries.firstOrNull { it.name == name } ?: WithinThreeSeconds
    }
}

/**
 * Keeps search-time policy separate from AI strength: level determines visits,
 * while this value only caps elapsed search time.
 */
data class SearchTimeSettings(
    val limit: SearchTimeLimit = SearchTimeLimit.WithinThreeSeconds,
) {
    fun applyTo(limit: AnalysisLimit): AnalysisLimit =
        limit.copy(timeMillis = this.limit.maximumMillis)

    fun withLimit(limit: SearchTimeLimit): SearchTimeSettings =
        copy(limit = limit)

    fun normalized(): SearchTimeSettings = this

    fun summaryText(): String =
        limit.maximumMillis?.let { millis -> "Time limit ${millis}ms" } ?: "Time limit OFF"
}
