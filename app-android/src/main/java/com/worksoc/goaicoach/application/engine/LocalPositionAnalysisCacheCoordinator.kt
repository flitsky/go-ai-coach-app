package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheEntry
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheKey
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheQuality
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheStore
import com.worksoc.goaicoach.application.analysis.TrustedPositionAnalysisCacheProvider
import com.worksoc.goaicoach.application.analysis.isStorablePositionAnalysisFor
import com.worksoc.goaicoach.application.analysis.positionAnalysisCacheKeyFor
import com.worksoc.goaicoach.application.analysis.withCacheHitSummary
import com.worksoc.goaicoach.middleware.PositionAnalysisCacheResolver
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.forcedJsonPositionAnalysis

internal data class LocalPositionAnalysisCacheContext(
    val state: GameState,
    val searchMode: EngineSearchMode,
    val effectiveLimit: AnalysisLimit,
    val cacheLimit: AnalysisLimit,
    val cacheKey: PositionAnalysisCacheKey,
)

internal class LocalPositionAnalysisCacheCoordinator(
    private val resolver: PositionAnalysisCacheResolver,
) {
    constructor(
        localStore: PositionAnalysisCacheStore,
        trustedProviders: List<TrustedPositionAnalysisCacheProvider>,
    ) : this(
        PositionAnalysisCacheResolver(
            localStore = localStore,
            trustedProviders = trustedProviders,
        ),
    )

    fun statsText(nowMillis: Long): String =
        resolver.statsText(nowMillis)

    fun qualityFor(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
        nowMillis: Long,
    ): PositionAnalysisCacheQuality? {
        val context = contextFor(
            state = state,
            limit = limit,
            searchMode = searchMode,
        )
        return resolver.qualityFor(key = context.cacheKey, nowMillis = nowMillis)
    }

    fun contextFor(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
        cacheLimitOverride: AnalysisLimit? = null,
    ): LocalPositionAnalysisCacheContext {
        val effectiveLimit = effectiveLimitFor(searchMode, limit)
        val cacheLimit = cacheLimitOverride
            ?.forcedJsonPositionAnalysis()
            ?: effectiveLimit
        return LocalPositionAnalysisCacheContext(
            state = state,
            searchMode = searchMode,
            effectiveLimit = effectiveLimit,
            cacheLimit = cacheLimit,
            cacheKey = positionAnalysisCacheKeyFor(
                state = state,
                searchMode = searchMode,
                limit = cacheLimit,
            ),
        )
    }

    fun reusableResultFor(
        context: LocalPositionAnalysisCacheContext,
        readCache: Boolean,
        nowMillis: Long,
    ): AnalysisResult? {
        if (context.searchMode != EngineSearchMode.JsonPositionAnalysis || !readCache) {
            return null
        }
        return resolver
            .reusableEntryFor(context.cacheKey, nowMillis)
            ?.let { entry -> entry.result.withCacheHitSummary(entry) }
    }

    fun storeIfEligible(
        context: LocalPositionAnalysisCacheContext,
        result: AnalysisResult,
        nowMillis: Long,
    ) {
        if (
            context.searchMode != EngineSearchMode.JsonPositionAnalysis ||
            !result.isStorablePositionAnalysisFor(context.cacheLimit)
        ) {
            return
        }
        val rootVisits = result.rootVisits ?: return
        resolver.putLocal(
            entry = PositionAnalysisCacheEntry(
                key = context.cacheKey,
                result = result,
                createdAtMillis = nowMillis,
                requestedRootVisits = context.cacheLimit.visits,
                rootVisits = rootVisits,
            ),
            nowMillis = nowMillis,
        )
    }
}

internal fun effectiveLimitFor(
    searchMode: EngineSearchMode,
    limit: AnalysisLimit,
): AnalysisLimit =
    when (searchMode) {
        EngineSearchMode.GtpStatefulFast -> limit
        EngineSearchMode.JsonPositionAnalysis -> limit.forcedJsonPositionAnalysis()
    }
