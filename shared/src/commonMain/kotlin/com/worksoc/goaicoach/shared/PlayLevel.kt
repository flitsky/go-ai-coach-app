package com.worksoc.goaicoach.shared

import kotlin.math.ceil
import kotlin.math.floor

enum class PlayLevelGroup(
    val label: String,
    val shortLabel: String,
    val maxLevel: Int,
    val difficulty: DifficultyProfile,
    val visits: Int,
    val timeMillis: Long,
    val candidateCount: Int,
    val analysisPreset: AnalysisPreset,
) {
    FastBeginner(
        label = "빠른 초급",
        shortLabel = "FB",
        maxLevel = 3,
        difficulty = DifficultyProfile.Beginner,
        visits = 16,
        timeMillis = 250,
        candidateCount = 8,
        analysisPreset = AnalysisPreset.Lite,
    ),
    Beginner(
        label = "초급",
        shortLabel = "LB",
        maxLevel = 7,
        difficulty = DifficultyProfile.Beginner,
        visits = 32,
        timeMillis = 500,
        candidateCount = 16,
        analysisPreset = AnalysisPreset.Learning,
    ),
    Intermediate(
        label = "중급",
        shortLabel = "IM",
        maxLevel = 5,
        difficulty = DifficultyProfile.Casual,
        visits = 64,
        timeMillis = 500,
        candidateCount = 20,
        analysisPreset = AnalysisPreset.Balanced,
    ),
    Advanced(
        label = "고급",
        shortLabel = "AD",
        maxLevel = 5,
        difficulty = DifficultyProfile.Intermediate,
        visits = 160,
        timeMillis = 1_000,
        candidateCount = 24,
        analysisPreset = AnalysisPreset.Balanced,
    ),
    ;

    fun defaultAnalysisLimit(): AnalysisLimit =
        AnalysisLimit(
            visits = visits,
            timeMillis = timeMillis,
            candidateCount = candidateCount,
            includePolicy = analysisPreset.includePolicy,
            refinePolicyMoves = analysisPreset.refinePolicyMoves,
            minVisitsPerCandidate = analysisPreset.minVisitsPerCandidate,
            minTimeMillis = analysisPreset.minTimeMillis,
        )

    fun selectionPolicy(level: Int): MoveSelectionPolicy {
        val safeLevel = level.coerceIn(1, maxLevel)
        return when (this) {
            FastBeginner -> when (safeLevel) {
                1 -> MoveSelectionPolicy.PercentileRange(50, 100, "탐색 후보 하위 50%")
                2 -> MoveSelectionPolicy.ExcludeBestPercentileRange(0, 60, "최적수 제외 상위 후보")
                else -> MoveSelectionPolicy.BestOnly
            }
            Beginner -> when (safeLevel) {
                1 -> MoveSelectionPolicy.PercentileRange(70, 100, "탐색 후보 최하위 30%")
                2 -> MoveSelectionPolicy.PercentileRange(50, 100, "탐색 후보 하위 50%")
                3 -> MoveSelectionPolicy.PercentileRange(40, 70, "탐색 후보 중위 40~70%")
                4 -> MoveSelectionPolicy.PercentileRange(30, 60, "탐색 후보 상위 30~60%")
                5 -> MoveSelectionPolicy.PercentileRange(10, 50, "탐색 후보 상위 10~50%")
                6 -> MoveSelectionPolicy.PercentileRange(0, 30, "탐색 후보 상위 30%")
                else -> MoveSelectionPolicy.BestOnly
            }
            Intermediate -> when (safeLevel) {
                1 -> MoveSelectionPolicy.PercentileRange(50, 100, "Casual 후보 하위 50%")
                2 -> MoveSelectionPolicy.PercentileRange(40, 80, "Casual 후보 하위~중위")
                3 -> MoveSelectionPolicy.PercentileRange(20, 60, "Casual 후보 중위권")
                4 -> MoveSelectionPolicy.PercentileRange(0, 40, "Casual 후보 상위 40%")
                else -> MoveSelectionPolicy.BestOnly
            }
            Advanced -> when (safeLevel) {
                1 -> MoveSelectionPolicy.PercentileRange(30, 70, "Intermediate 후보 중위권")
                2 -> MoveSelectionPolicy.PercentileRange(20, 50, "Intermediate 후보 상위 중간권")
                3 -> MoveSelectionPolicy.PercentileRange(10, 40, "Intermediate 후보 상위권")
                4 -> MoveSelectionPolicy.PercentileRange(0, 20, "Intermediate 후보 최상위권")
                else -> MoveSelectionPolicy.BestOnly
            }
        }
    }
}

data class PlayLevelSetting(
    val group: PlayLevelGroup = PlayLevelGroup.FastBeginner,
    val level: Int = 1,
) {
    val safeLevel: Int = level.coerceIn(1, group.maxLevel)
    val analysisPreset: AnalysisPreset = group.analysisPreset
    val analysisLimit: AnalysisLimit = group.defaultAnalysisLimit()
    val selectionPolicy: MoveSelectionPolicy = group.selectionPolicy(safeLevel)
    val displayLabel: String = "${group.label} ${safeLevel}단계"

    fun withGroup(nextGroup: PlayLevelGroup): PlayLevelSetting =
        copy(
            group = nextGroup,
            level = safeLevel.coerceAtMost(nextGroup.maxLevel),
        ).normalized()

    fun withLevel(nextLevel: Int): PlayLevelSetting =
        copy(level = nextLevel).normalized()

    fun normalized(): PlayLevelSetting =
        if (level == safeLevel) this else copy(level = safeLevel)

    fun toEngineProfile(base: EngineProfile): EngineProfile =
        base.copy(
            difficulty = group.difficulty,
            analysisLimit = analysisLimit,
        )
}

sealed class MoveSelectionPolicy {
    abstract val description: String

    data object BestOnly : MoveSelectionPolicy() {
        override val description: String = "최적수만 착수"
    }

    data class PercentileRange(
        val startPercent: Int,
        val endPercent: Int,
        override val description: String,
    ) : MoveSelectionPolicy() {
        init {
            require(startPercent in 0..99) { "startPercent must be 0..99" }
            require(endPercent in 1..100) { "endPercent must be 1..100" }
            require(startPercent < endPercent) { "startPercent must be lower than endPercent" }
        }
    }

    data class ExcludeBestPercentileRange(
        val startPercent: Int,
        val endPercent: Int,
        override val description: String,
    ) : MoveSelectionPolicy() {
        init {
            require(startPercent in 0..99) { "startPercent must be 0..99" }
            require(endPercent in 1..100) { "endPercent must be 1..100" }
            require(startPercent < endPercent) { "startPercent must be lower than endPercent" }
        }
    }

    fun candidateIndexRange(candidateCount: Int): IntRange? {
        if (candidateCount <= 0) return null
        if (this is BestOnly) return 0..0

        if (this is ExcludeBestPercentileRange && candidateCount == 1) {
            return 0..0
        }

        val range = when (this) {
            is BestOnly -> return 0..0
            is PercentileRange -> PercentileWindow(startPercent, endPercent)
            is ExcludeBestPercentileRange -> PercentileWindow(startPercent, endPercent, minimumStartIndex = 1)
        }
        val last = candidateCount - 1
        val start = floor(candidateCount * range.startPercent / 100.0).toInt().coerceIn(0, last)
            .coerceAtLeast(range.minimumStartIndex.coerceAtMost(last))
        val exclusiveEnd = ceil(candidateCount * range.endPercent / 100.0).toInt().coerceIn(start + 1, candidateCount)
        return start until exclusiveEnd
    }

    private data class PercentileWindow(
        val startPercent: Int,
        val endPercent: Int,
        val minimumStartIndex: Int = 0,
    )
}
