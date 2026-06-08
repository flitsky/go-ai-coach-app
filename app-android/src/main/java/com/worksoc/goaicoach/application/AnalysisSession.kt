package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.DifficultyProfile
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.LegalMoveGenerator
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.analysisFingerprint
import java.util.LinkedHashMap

internal const val MinScoredTopMovesForDisplay = 5

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

internal class AnalysisResultCache(
    private val maxEntries: Int,
) {
    private val entries = object : LinkedHashMap<AnalysisCacheKey, CachedAnalysisResult>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<AnalysisCacheKey, CachedAnalysisResult>?,
        ): Boolean = size > maxEntries
    }

    private var hits: Int = 0
    private var misses: Int = 0

    fun get(key: AnalysisCacheKey): CachedAnalysisResult? {
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
        entries[key] = result
    }

    fun statsText(): String =
        "entries=${entries.size}, hits=$hits, misses=$misses"
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
): Int =
    LegalMoveGenerator
        .legalPlayCount(state)
        .coerceAtLeast(1)
        .coerceAtMost(preset.candidateCap)

internal fun topMovesAnalysisLimitFor(
    profile: EngineProfile,
    preset: AnalysisPreset,
    candidateCount: Int,
): AnalysisLimit {
    val promoted = if (preset.promoteTopMovesDifficulty) {
        profile.difficulty.next().defaultAnalysisLimit()
    } else {
        profile.analysisLimit
    }
    val promotedTimeMillis = promoted.timeMillis ?: profile.analysisLimit.timeMillis
    return profile.analysisLimit.copy(
        visits = maxOf(profile.analysisLimit.visits, promoted.visits),
        timeMillis = promotedTimeMillis?.let {
            strongerTopMovesTimeMillis(profile.analysisLimit.timeMillis, it)
        } ?: profile.analysisLimit.timeMillis,
        candidateCount = candidateCount,
        includePolicy = preset.includePolicy,
        refinePolicyMoves = preset.refinePolicyMoves,
        minVisitsPerCandidate = preset.minVisitsPerCandidate,
        minTimeMillis = preset.minTimeMillis,
    )
}

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
    } else if (!preset.promoteTopMovesDifficulty) {
        profile.difficulty.label
    } else {
        profile.difficulty.next().label
    }
    val suffix = if (deep) {
        "manual deep analysis"
    } else if (profile.difficulty.next() == profile.difficulty) {
        "same as max profile"
    } else if (!preset.promoteTopMovesDifficulty) {
        "same as ${profile.difficulty.label}"
    } else {
        "one grade above ${profile.difficulty.label}"
    }
    return "Top Moves request: ${preset.label}, up to $candidateCount candidate(s), $label ($suffix), base ${limit.visits} visits / ${limit.timeMillis ?: 0}ms, refine ${limit.refinePolicyMoves}\n$this"
}

internal fun String.withAnalysisCoverage(snapshot: MoveAnalysisSnapshot): String =
    "${snapshot.coverageSummary()}\n$this"
