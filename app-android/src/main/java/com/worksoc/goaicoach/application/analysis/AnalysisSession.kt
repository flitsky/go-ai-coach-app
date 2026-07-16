package com.worksoc.goaicoach.application.analysis

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.DifficultyProfile
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.SearchTimeProfile
import com.worksoc.goaicoach.shared.TurnAnalysisPurpose
import com.worksoc.goaicoach.shared.analysisFingerprint
import com.worksoc.goaicoach.shared.fastCandidateAnalysis
import com.worksoc.goaicoach.shared.turnAnalysisLimitFor

internal const val LightweightTopMoveCandidateCount = 5

internal data class AnalysisCacheKey(
    val positionFingerprint: String,
    val preset: AnalysisPreset,
    val limit: AnalysisLimit,
    val deep: Boolean,
    // Analysis results from the JSON process and the stateful GTP process are
    // not interchangeable. Keep their short-lived UI cache entries separate.
    val searchMode: EngineSearchMode = EngineSearchMode.GtpStatefulFast,
)

internal data class CachedAnalysisResult(
    val snapshot: MoveAnalysisSnapshot,
    val candidateText: String,
    val quality: PositionAnalysisCacheQuality? = null,
) {
    val canRestoreAfterUndo: Boolean
        get() = snapshot.hasEngineCandidates && quality?.isComplete == true
}

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
    private val entries = AccessOrderedCache<AnalysisCacheKey, CachedAnalysisResult>(maxEntries)

    private var hits: Int = 0
    private var misses: Int = 0

    val isEnabled: Boolean
        get() = mode == AnalysisCacheMode.Enabled

    fun get(key: AnalysisCacheKey): CachedAnalysisResult? {
        if (!isEnabled) {
            return null
        }
        val value = entries.get(key)
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
        entries.put(key, result)
    }

    fun statsText(): String =
        if (isEnabled) {
            "enabled, entries=${entries.size}, hits=$hits, misses=$misses"
        } else {
            "disabled, entries=0, hits=0, misses=0"
        }
}

/**
 * Short-lived in-session cache for undo navigation.
 *
 * General Top Moves caching remains disabled by default because it can hide
 * fresh engine variation during normal play. Undo is narrower: returning to a
 * just-seen position should reuse the exact same completed analysis snapshot
 * instead of asking the engine again. This cache only accepts snapshots whose
 * root visits filled the requested analysis budget, and it is cleared when a
 * game/session boundary changes.
 */
internal class UndoAnalysisRestoreCache(
    private val maxEntries: Int,
) {
    private val entries = AccessOrderedCache<AnalysisCacheKey, CachedAnalysisResult>(maxEntries)

    fun get(key: AnalysisCacheKey): CachedAnalysisResult? =
        entries.get(key)

    fun put(
        key: AnalysisCacheKey,
        result: CachedAnalysisResult,
    ) {
        if (result.canRestoreAfterUndo) {
            entries.put(key, result)
        }
    }

    fun clear() {
        entries.clear()
    }

    fun statsText(): String =
        "undoRestoreEntries=${entries.size}"
}

private class AccessOrderedCache<K, V>(
    maxEntries: Int,
) {
    private val maxEntries = maxEntries.coerceAtLeast(0)
    private val values = mutableMapOf<K, V>()
    private val accessOrder = mutableListOf<K>()

    val size: Int
        get() = values.size

    fun get(key: K): V? {
        val value = values[key] ?: return null
        touch(key)
        return value
    }

    fun put(
        key: K,
        value: V,
    ) {
        if (maxEntries == 0) {
            return
        }
        values[key] = value
        touch(key)
        trimToSize()
    }

    fun clear() {
        values.clear()
        accessOrder.clear()
    }

    private fun touch(key: K) {
        accessOrder.remove(key)
        accessOrder += key
    }

    private fun trimToSize() {
        while (values.size > maxEntries) {
            val eldest = accessOrder.removeAt(0)
            values.remove(eldest)
        }
    }
}

internal fun analysisKeyFor(
    state: GameState,
    preset: AnalysisPreset,
    limit: AnalysisLimit,
    deep: Boolean,
    searchMode: EngineSearchMode = EngineSearchMode.GtpStatefulFast,
): AnalysisCacheKey =
    AnalysisCacheKey(
        positionFingerprint = state.analysisFingerprint(),
        preset = preset,
        limit = limit,
        deep = deep,
        searchMode = searchMode,
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
    ).copy(visits = SearchTimeProfile.B16.visits)

internal fun deepTopMovesAnalysisLimitFor(
    profile: EngineProfile,
    candidateCount: Int,
): AnalysisLimit {
    val full = DifficultyProfile.FullAnalysis.defaultAnalysisLimit()
    return profile.analysisLimit.copy(
        visits = maxOf(profile.analysisLimit.visits, full.visits),
        // The user-selected limit is a maximum for every Top Moves request,
        // including the manual deep variant. Never promote it to a longer cap.
        timeMillis = profile.analysisLimit.timeMillis,
        candidateCount = candidateCount,
    ).fastCandidateAnalysis(candidateCount)
}

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
