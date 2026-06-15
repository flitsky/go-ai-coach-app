package com.worksoc.goaicoach.application.analysis

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.analysisFingerprint
import kotlin.math.roundToInt

internal const val JsonPositionAnalysisCacheMaxEntries: Int = 20
internal const val JsonPositionAnalysisCacheTtlMillis: Long = 365L * 24L * 60L * 60L * 1_000L
internal const val JsonPositionAnalysisCacheMinReusableFillRatio: Double = 0.5

internal enum class PositionAnalysisCacheOrigin(
    val label: String,
    val trustRank: Int,
) {
    LocalUser("local-user", 20),
    PeerShared("peer-shared", 15),
    BundledTrusted("bundled-trusted", 30),
    OperatorTrusted("operator-trusted", 40),
}

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
    val origin: PositionAnalysisCacheOrigin = PositionAnalysisCacheOrigin.LocalUser,
) {
    fun isExpired(nowMillis: Long): Boolean =
        nowMillis - createdAtMillis > JsonPositionAnalysisCacheTtlMillis

    val quality: PositionAnalysisCacheQuality
        get() = PositionAnalysisCacheQuality.from(
            rootVisits = rootVisits,
            requestedRootVisits = requestedRootVisits,
        )
}

internal enum class PositionAnalysisCacheQualityTier(
    val label: String,
) {
    Complete("complete"),
    ReusablePartial("partial"),
    DiagnosticOnly("diagnostic"),
}

internal data class PositionAnalysisCacheQuality(
    val tier: PositionAnalysisCacheQualityTier,
    val rootVisits: Int,
    val requestedRootVisits: Int,
) {
    val fillRatio: Double =
        if (requestedRootVisits <= 0) 0.0 else rootVisits.toDouble() / requestedRootVisits.toDouble()

    val fillPercent: Int =
        (fillRatio.coerceIn(0.0, 1.0) * 100.0).roundToInt()

    val isComplete: Boolean =
        tier == PositionAnalysisCacheQualityTier.Complete

    val isReusable: Boolean =
        tier != PositionAnalysisCacheQualityTier.DiagnosticOnly

    val isStorable: Boolean =
        rootVisits > 0 && requestedRootVisits > 0

    fun summaryText(): String =
        "${tier.label} root=$rootVisits/$requestedRootVisits fill=$fillPercent%"

    companion object {
        fun from(
            rootVisits: Int?,
            requestedRootVisits: Int,
        ): PositionAnalysisCacheQuality {
            val safeRootVisits = rootVisits ?: 0
            val tier = when {
                requestedRootVisits <= 0 || safeRootVisits <= 0 ->
                    PositionAnalysisCacheQualityTier.DiagnosticOnly
                safeRootVisits >= requestedRootVisits ->
                    PositionAnalysisCacheQualityTier.Complete
                safeRootVisits.toDouble() / requestedRootVisits.toDouble() >=
                    JsonPositionAnalysisCacheMinReusableFillRatio ->
                    PositionAnalysisCacheQualityTier.ReusablePartial
                else ->
                    PositionAnalysisCacheQualityTier.DiagnosticOnly
            }
            return PositionAnalysisCacheQuality(
                tier = tier,
                rootVisits = safeRootVisits,
                requestedRootVisits = requestedRootVisits,
            )
        }
    }
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

    fun peek(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry?

    fun statsText(nowMillis: Long): String
}

internal interface TrustedPositionAnalysisCacheProvider {
    fun get(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry?

    fun peek(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry?

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

    override fun peek(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry? = null

    override fun statsText(nowMillis: Long): String =
        "disabled"
}

internal object NoopTrustedPositionAnalysisCacheProvider : TrustedPositionAnalysisCacheProvider {
    override fun get(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry? = null

    override fun peek(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry? = null

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

internal fun AnalysisResult.cacheQualityFor(limit: AnalysisLimit): PositionAnalysisCacheQuality =
    PositionAnalysisCacheQuality.from(
        rootVisits = rootVisits,
        requestedRootVisits = limit.visits,
    )

internal fun AnalysisResult.isStorablePositionAnalysisFor(limit: AnalysisLimit): Boolean =
    candidates.isNotEmpty() && cacheQualityFor(limit).isStorable

internal fun shouldReplacePositionAnalysisCacheEntry(
    existing: PositionAnalysisCacheEntry?,
    candidate: PositionAnalysisCacheEntry,
): Boolean {
    if (!candidate.quality.isStorable) {
        return false
    }
    if (existing == null) {
        return true
    }
    if (candidate.rootVisits != existing.rootVisits) {
        return candidate.rootVisits > existing.rootVisits
    }
    if (candidate.origin.trustRank != existing.origin.trustRank) {
        return candidate.origin.trustRank > existing.origin.trustRank
    }
    return candidate.createdAtMillis >= existing.createdAtMillis
}

internal fun bestPositionAnalysisCacheEntry(
    entries: Iterable<PositionAnalysisCacheEntry>,
): PositionAnalysisCacheEntry? =
    entries.fold(null as PositionAnalysisCacheEntry?) { best, entry ->
        if (shouldReplacePositionAnalysisCacheEntry(best, entry)) entry else best
    }

internal fun AnalysisResult.withCacheHitSummary(entry: PositionAnalysisCacheEntry): AnalysisResult =
    copy(
        status = status.copy(
            message = "JSON position analysis cache hit: ${entry.quality.summaryText()}",
        ),
        summary = buildString {
            append("JSON position analysis cache hit: origin=${entry.origin.label}, createdAtMillis=${entry.createdAtMillis}, ")
            append("${entry.quality.summaryText()}.\n")
            append(summary)
        },
    )
