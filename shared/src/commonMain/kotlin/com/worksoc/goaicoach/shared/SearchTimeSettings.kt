package com.worksoc.goaicoach.shared

enum class SearchTimeProfile(
    val visits: Int,
    val displayLabel: String,
    val defaultMillis: Long,
    val optionMillis: List<Long>,
) {
    B16(
        visits = 16,
        displayLabel = "16",
        defaultMillis = 1_000L,
        optionMillis = listOf(500L, 1_000L, 1_500L, 2_000L, 2_500L),
    ),
    B32(
        visits = 32,
        displayLabel = "32",
        defaultMillis = 2_000L,
        optionMillis = listOf(1_000L, 2_000L, 3_000L, 4_000L, 5_000L),
    ),
    B64(
        visits = 64,
        displayLabel = "64",
        defaultMillis = 3_000L,
        optionMillis = listOf(3_000L, 4_500L, 6_000L, 7_500L, 9_000L),
    ),
    ;

    fun sanitize(millis: Long): Long =
        optionMillis.firstOrNull { option -> option == millis } ?: defaultMillis

    companion object {
        fun fromVisits(visits: Int): SearchTimeProfile? =
            entries.firstOrNull { profile -> profile.visits == visits }
    }
}

data class SearchTimeSettings(
    val b16Millis: Long = SearchTimeProfile.B16.defaultMillis,
    val b32Millis: Long = SearchTimeProfile.B32.defaultMillis,
    val b64Millis: Long = SearchTimeProfile.B64.defaultMillis,
    val timeCapEnabled: Boolean = true,
) {
    fun millisFor(profile: SearchTimeProfile): Long =
        when (profile) {
            SearchTimeProfile.B16 -> profile.sanitize(b16Millis)
            SearchTimeProfile.B32 -> profile.sanitize(b32Millis)
            SearchTimeProfile.B64 -> profile.sanitize(b64Millis)
        }

    fun millisForVisits(
        visits: Int,
        fallbackMillis: Long?,
    ): Long? =
        if (!timeCapEnabled) {
            null
        } else {
            millisForVisitsWhenEnabled(visits, fallbackMillis)
        }

    fun millisForVisitsWhenEnabled(
        visits: Int,
        fallbackMillis: Long?,
    ): Long? =
        SearchTimeProfile.fromVisits(visits)
            ?.let(::millisFor)
            ?: fallbackMillis

    fun applyTo(limit: AnalysisLimit): AnalysisLimit =
        limit.copy(timeMillis = millisForVisits(limit.visits, limit.timeMillis))

    fun withMillis(
        profile: SearchTimeProfile,
        millis: Long,
    ): SearchTimeSettings =
        when (profile) {
            SearchTimeProfile.B16 -> copy(b16Millis = profile.sanitize(millis))
            SearchTimeProfile.B32 -> copy(b32Millis = profile.sanitize(millis))
            SearchTimeProfile.B64 -> copy(b64Millis = profile.sanitize(millis))
        }

    fun normalized(): SearchTimeSettings =
        copy(
            b16Millis = SearchTimeProfile.B16.sanitize(b16Millis),
            b32Millis = SearchTimeProfile.B32.sanitize(b32Millis),
            b64Millis = SearchTimeProfile.B64.sanitize(b64Millis),
            timeCapEnabled = timeCapEnabled,
        )

    fun withTimeCapEnabled(enabled: Boolean): SearchTimeSettings =
        copy(timeCapEnabled = enabled).normalized()

    fun summaryText(): String =
        if (!timeCapEnabled) {
            "Time cap OFF"
        } else {
            SearchTimeProfile.entries.joinToString(separator = " / ") { profile ->
                "B${profile.displayLabel} ${millisFor(profile)}ms"
            }
        }
}
