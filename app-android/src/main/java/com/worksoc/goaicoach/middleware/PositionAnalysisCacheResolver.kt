package com.worksoc.goaicoach.middleware

import com.worksoc.goaicoach.application.NoopPositionAnalysisCacheStore
import com.worksoc.goaicoach.application.PositionAnalysisCacheEntry
import com.worksoc.goaicoach.application.PositionAnalysisCacheKey
import com.worksoc.goaicoach.application.PositionAnalysisCacheQuality
import com.worksoc.goaicoach.application.PositionAnalysisCacheStore
import com.worksoc.goaicoach.application.TrustedPositionAnalysisCacheProvider
import com.worksoc.goaicoach.application.bestPositionAnalysisCacheEntry

internal class PositionAnalysisCacheResolver(
    private val localStore: PositionAnalysisCacheStore = NoopPositionAnalysisCacheStore,
    private val trustedProviders: List<TrustedPositionAnalysisCacheProvider> = emptyList(),
) {
    fun statsText(nowMillis: Long): String {
        val localStats = localStore.statsText(nowMillis)
        if (trustedProviders.isEmpty()) {
            return localStats
        }
        val trustedStats = trustedProviders
            .joinToString(";") { provider -> provider.statsText(nowMillis) }
        return "$localStats, trusted={$trustedStats}"
    }

    fun qualityFor(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheQuality? =
        localStore.peek(key, nowMillis)?.quality
            ?: trustedEntryFor(key = key, nowMillis = nowMillis, reusableOnly = false)?.quality

    fun reusableEntryFor(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry? =
        localStore.get(key, nowMillis)
            ?: trustedEntryFor(key = key, nowMillis = nowMillis, reusableOnly = true)

    fun putLocal(
        entry: PositionAnalysisCacheEntry,
        nowMillis: Long,
    ) {
        localStore.put(entry, nowMillis)
    }

    private fun trustedEntryFor(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
        reusableOnly: Boolean,
    ): PositionAnalysisCacheEntry? =
        bestPositionAnalysisCacheEntry(
            trustedProviders
                .mapNotNull { provider ->
                    if (reusableOnly) {
                        provider.get(key, nowMillis)
                    } else {
                        provider.peek(key, nowMillis)
                    }
                }
                .filterNot { entry -> entry.isExpired(nowMillis) },
        )
}
