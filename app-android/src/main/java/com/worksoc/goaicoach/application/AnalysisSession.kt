package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.DifficultyProfile
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.TurnAnalysisPurpose
import com.worksoc.goaicoach.shared.analysisFingerprint
import com.worksoc.goaicoach.shared.turnAnalysisLimitFor
import java.util.LinkedHashMap

internal const val LightweightTopMoveCandidateCount = 1

internal data class AnalysisCacheKey(
    val positionFingerprint: String,
    val preset: AnalysisPreset,
    val limit: AnalysisLimit,
    val deep: Boolean,
)

internal data class CachedAnalysisResult(
    val snapshot: MoveAnalysisSnapshot,
    val candidateText: String,
)

// Disabled by default until cache reuse has quality gates such as repeated stable engine results,
// sufficient root visits, probabilistic reuse, and loss-triggered invalidation.
internal enum class AnalysisCacheMode {
    Disabled,
    Enabled,
}

internal class AnalysisResultCache(
    private val maxEntries: Int,
    val mode: AnalysisCacheMode = AnalysisCacheMode.Disabled,
) {
    private val entries = object : LinkedHashMap<AnalysisCacheKey, CachedAnalysisResult>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<AnalysisCacheKey, CachedAnalysisResult>?,
        ): Boolean = size > maxEntries
    }

    private var hits: Int = 0
    private var misses: Int = 0

    val isEnabled: Boolean
        get() = mode == AnalysisCacheMode.Enabled

    fun get(key: AnalysisCacheKey): CachedAnalysisResult? {
        if (!isEnabled) {
            return null
        }
        val value = entries[key]
        if (value == null) {
            misses += 1
        } else {
            hits += 1
        }
        return value
    }

    fun put(
        key: AnalysisCacheKey,
        result: CachedAnalysisResult,
    ) {
        if (!isEnabled) {
            return
        }
        entries[key] = result
    }

    fun statsText(): String =
        if (isEnabled) {
            "enabled, entries=${entries.size}, hits=$hits, misses=$misses"
        } else {
            "disabled, entries=0, hits=0, misses=0"
        }
}

internal fun analysisKeyFor(
    state: GameState,
    preset: AnalysisPreset,
    limit: AnalysisLimit,
    deep: Boolean,
): AnalysisCacheKey =
    AnalysisCacheKey(
        positionFingerprint = state.analysisFingerprint(),
        preset = preset,
        limit = limit,
        deep = deep,
    )

internal fun topMoveCandidateCountFor(
    state: GameState,
    preset: AnalysisPreset,
): Int = LightweightTopMoveCandidateCount

internal fun topMovesAnalysisLimitFor(
    profile: EngineProfile,
    preset: AnalysisPreset,
    candidateCount: Int,
): AnalysisLimit =
    profile.turnAnalysisLimitFor(
        purpose = TurnAnalysisPurpose.TopMovesDisplay,
        candidateCount = candidateCount,
    )

internal fun deepTopMovesAnalysisLimitFor(
    profile: EngineProfile,
    candidateCount: Int,
): AnalysisLimit {
    val full = DifficultyProfile.FullAnalysis.defaultAnalysisLimit()
    val fullTimeMillis = full.timeMillis
        ?: profile.analysisLimit.timeMillis
        ?: 5_000L
    return profile.analysisLimit.copy(
        visits = maxOf(profile.analysisLimit.visits, full.visits),
        timeMillis = strongerTopMovesTimeMillis(profile.analysisLimit.timeMillis, fullTimeMillis),
        candidateCount = candidateCount,
        includePolicy = AnalysisPreset.Deep.includePolicy,
        refinePolicyMoves = AnalysisPreset.Deep.refinePolicyMoves,
        minVisitsPerCandidate = AnalysisPreset.Deep.minVisitsPerCandidate,
        minTimeMillis = AnalysisPreset.Deep.minTimeMillis,
    )
}

private fun strongerTopMovesTimeMillis(
    current: Long?,
    promoted: Long,
): Long = current?.coerceAtLeast(promoted) ?: promoted

internal fun String.withTopMovesStrengthHeader(
    profile: EngineProfile,
    preset: AnalysisPreset,
    limit: AnalysisLimit,
    candidateCount: Int,
    deep: Boolean,
): String {
    val label = if (deep) {
        DifficultyProfile.FullAnalysis.label
    } else {
        profile.difficulty.label
    }
    val suffix = if (deep) {
        "manual deep analysis"
    } else {
        "fast best-only"
    }
    return "Top Moves request: ${preset.label}, up to $candidateCount candidate(s), $label ($suffix), base ${limit.visits} visits / ${limit.timeMillis ?: 0}ms, refine ${limit.refinePolicyMoves}\n$this"
}

internal fun String.withAnalysisCoverage(snapshot: MoveAnalysisSnapshot): String =
    "${snapshot.coverageSummary()}\n$this"
