package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.analysisFingerprint

internal const val JsonPositionAnalysisCacheMaxEntries: Int = 10
internal const val JsonPositionAnalysisCacheTtlMillis: Long = 30L * 24L * 60L * 60L * 1_000L

internal data class PositionAnalysisCacheKey(
    val positionFingerprint: String,
    val searchMode: EngineSearchMode,
    val limit: AnalysisLimit,
)

internal data class PositionAnalysisCacheEntry(
    val key: PositionAnalysisCacheKey,
    val result: AnalysisResult,
    val createdAtMillis: Long,
    val requestedRootVisits: Int,
    val rootVisits: Int,
) {
    fun isExpired(nowMillis: Long): Boolean =
        nowMillis - createdAtMillis > JsonPositionAnalysisCacheTtlMillis
}

internal interface PositionAnalysisCacheStore {
    fun get(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry?

    fun put(
        entry: PositionAnalysisCacheEntry,
        nowMillis: Long,
    )

    fun statsText(nowMillis: Long): String
}

internal object NoopPositionAnalysisCacheStore : PositionAnalysisCacheStore {
    override fun get(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry? = null

    override fun put(
        entry: PositionAnalysisCacheEntry,
        nowMillis: Long,
    ) = Unit

    override fun statsText(nowMillis: Long): String =
        "disabled"
}

internal fun positionAnalysisCacheKeyFor(
    state: GameState,
    searchMode: EngineSearchMode,
    limit: AnalysisLimit,
): PositionAnalysisCacheKey =
    PositionAnalysisCacheKey(
        positionFingerprint = state.analysisFingerprint(),
        searchMode = searchMode,
        limit = limit,
    )

internal fun AnalysisResult.hasCompleteRootVisitsFor(limit: AnalysisLimit): Boolean =
    rootVisits?.let { visits -> visits >= limit.visits } == true

internal fun AnalysisResult.withCacheHitSummary(entry: PositionAnalysisCacheEntry): AnalysisResult =
    copy(
        status = status.copy(
            message = "JSON position analysis cache hit: root=${entry.rootVisits}/${entry.requestedRootVisits}",
        ),
        summary = buildString {
            append("JSON position analysis cache hit: createdAtMillis=${entry.createdAtMillis}, ")
            append("root=${entry.rootVisits}/${entry.requestedRootVisits}.\n")
            append(summary)
        },
    )
