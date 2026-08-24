package com.worksoc.goaicoach.shared

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

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
        maxLevel = 5,
        difficulty = DifficultyProfile.Beginner,
        visits = 16,
        timeMillis = SearchTimeProfile.B16.defaultMillis,
        candidateCount = 8,
        analysisPreset = AnalysisPreset.Lite,
    ),
    Beginner(
        label = "초급",
        shortLabel = "LB",
        maxLevel = 7,
        difficulty = DifficultyProfile.Beginner,
        visits = 32,
        timeMillis = SearchTimeProfile.B32.defaultMillis,
        candidateCount = 16,
        analysisPreset = AnalysisPreset.Learning,
    ),
    Intermediate(
        label = "중급",
        shortLabel = "IM",
        maxLevel = 5,
        difficulty = DifficultyProfile.Casual,
        visits = 64,
        timeMillis = SearchTimeProfile.B64.defaultMillis,
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
                1 -> MoveSelectionPolicy.BucketedTierSelection(
                    worstPercent = 100,
                    midPercent = 0,
                    bestPercent = 0,
                    tierName = "초보",
                    description = "초보 · 최하수만 착수",
                )
                2 -> MoveSelectionPolicy.BucketedTierSelection(
                    worstPercent = 60,
                    midPercent = 40,
                    bestPercent = 0,
                    tierName = "하수",
                    description = "하수 · 최하수 60% / 중급수 40%",
                )
                3 -> MoveSelectionPolicy.BucketedTierSelection(
                    worstPercent = 20,
                    midPercent = 60,
                    bestPercent = 20,
                    tierName = "중수",
                    description = "중수 · 최하수 20% / 중급수 60% / 최적수 20%",
                )
                4 -> MoveSelectionPolicy.BucketedTierSelection(
                    worstPercent = 10,
                    midPercent = 30,
                    bestPercent = 60,
                    tierName = "고수",
                    description = "고수 · 최하수 10% / 중급수 30% / 최적수 60%",
                )
                // 초고수(5단계): 후보 버킷을 나누지 않는다 — 엔진에 후보 1개만
                // 요청해 그 최적수를 그대로 둔다(과거 "빠른 초급 3단계"와 동일).
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
    /**
     * 그룹 이름을 뺀 **단계 이름만**("초보"/"하수"/... , 다른 그룹은 "4단계").
     * 봇 캐릭터 픽커가 캐릭터 이름 옆에 이 값을 병기해 어느 쪽이 센 상대인지 드러낸다(백로그 #9 확정).
     */
    val tierLabel: String =
        (selectionPolicy as? MoveSelectionPolicy.BucketedTierSelection)?.tierName
            // 5단계(초고수)는 BestOnly라 위 분기를 안 타지만, 이름은 여전히 필요하다.
            ?: if (group == PlayLevelGroup.FastBeginner) "초고수" else "${safeLevel}단계"

    val displayLabel: String =
        if (group == PlayLevelGroup.FastBeginner) {
            "${group.label} · $tierLabel"
        } else {
            "${group.label} $tierLabel"
        }

    fun withGroup(nextGroup: PlayLevelGroup): PlayLevelSetting =
        copy(
            group = nextGroup,
            level = safeLevel.coerceAtMost(nextGroup.maxLevel),
        ).normalized()

    fun withLevel(nextLevel: Int): PlayLevelSetting =
        copy(level = nextLevel).normalized()

    fun normalized(): PlayLevelSetting =
        if (level == safeLevel) this else copy(level = safeLevel)

    fun analysisLimitWith(searchTimeSettings: SearchTimeSettings): AnalysisLimit =
        searchTimeSettings.applyTo(analysisLimit)

    fun toEngineProfile(
        base: EngineProfile,
        searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    ): EngineProfile =
        base.copy(
            difficulty = group.difficulty,
            analysisLimit = analysisLimitWith(searchTimeSettings),
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

    /**
     * 후보를 순위(order)로 최적수(1개)/중급수(가운데)/최하수(1개, N≥3부터 존재)
     * 3개 버킷으로 나눈 뒤, 대국 진행에 따라 최하수를 우선 채우는 방식으로 버킷을
     * 골라 착수하는 정책. [candidateIndexRange]는 이 정책에 대해 답할 수 없다 —
     * 어느 버킷을 쓸지는 그 턴의 후보 개수만이 아니라 "이 진영이 이번 판에서
     * 지금까지 몇 수를 뒀는가"에도 좌우되기 때문이다. 실제 버킷 선택은
     * [targetBucket]/[resolveIndexRange]가 담당한다.
     */
    data class BucketedTierSelection(
        val worstPercent: Int,
        val midPercent: Int,
        val bestPercent: Int,
        val tierName: String,
        override val description: String,
    ) : MoveSelectionPolicy() {
        init {
            require(worstPercent in 0..100) { "worstPercent must be 0..100" }
            require(midPercent in 0..100) { "midPercent must be 0..100" }
            require(bestPercent in 0..100) { "bestPercent must be 0..100" }
            require(worstPercent + midPercent + bestPercent == 100) {
                "worstPercent + midPercent + bestPercent must sum to 100"
            }
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
            // 이 정책은 턴 진행 상태가 필요해 여기서 답할 수 없다 — 클래스 문서 참고.
            is BucketedTierSelection -> return null
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

/** [MoveSelectionPolicy.BucketedTierSelection]이 후보를 나누는 3개 버킷. */
enum class CandidateBucket {
    Best,
    Mid,
    Worst,
}

/**
 * `candidateCount`개의 scored 후보(순위 0=최상위)를 최적수/중급수/최하수로 나눴을 때,
 * 요청한 [bucket]에 해당하는 인덱스 구간. 그 버킷이 이 후보 개수에서 존재하지 않으면
 * `null`(예: 최하수는 후보가 3개 이상이어야 존재, 중급수는 2개 이상).
 */
fun candidateBucketRange(candidateCount: Int, bucket: CandidateBucket): IntRange? {
    if (candidateCount <= 0) return null
    return when (bucket) {
        CandidateBucket.Best -> 0..0
        CandidateBucket.Mid -> when {
            candidateCount <= 1 -> null
            candidateCount == 2 -> 1..1
            else -> 1..(candidateCount - 2)
        }
        CandidateBucket.Worst -> when {
            candidateCount <= 2 -> null
            else -> (candidateCount - 1)..(candidateCount - 1)
        }
    }
}

/**
 * 이번 턴에 어느 버킷을 목표로 할지 결정한다. 상태를 별도로 저장하지 않고
 * [ownMoveIndex](이 진영이 이번 판에서 지금까지 둔 수, 0부터 시작)만으로 계산한다 —
 * "지금까지 뒀어야 할 최하수 누적 목표"를 매 턴 다시 계산해서, 목표가 새로 하나
 * 늘어난 턴에만 최하수를 목표로 삼는다(ceil 경계 통과 방식). 이 방식은 매 수마다
 * 독립적으로 뽑는 것과 달리 (a) worstPercent가 0보다 크면 이 진영의 첫 수는 항상
 * 최하수를 목표로 하고 (b) 게임이 길어져도 장기 비율이 원안 퍼센트에 수렴하며
 * (c) 판/진영별로 별도 저장 상태가 필요 없다(그때그때 [ownMoveIndex]만 알면 된다).
 * 목표 버킷이 이번 턴엔 비어 있을 수 있으므로 실제 선택은 [resolveIndexRange]가
 * 최하수→중급수→최적수 순으로 폴백한다.
 */
fun MoveSelectionPolicy.BucketedTierSelection.targetBucket(
    ownMoveIndex: Int,
    random: Random,
): CandidateBucket {
    if (worstPercent > 0) {
        val previousTarget = ceil(worstPercent * ownMoveIndex / 100.0).toInt()
        val currentTarget = ceil(worstPercent * (ownMoveIndex + 1) / 100.0).toInt()
        if (currentTarget > previousTarget) return CandidateBucket.Worst
    }
    if (midPercent <= 0) return CandidateBucket.Best
    if (bestPercent <= 0) return CandidateBucket.Mid
    return if (random.nextInt(midPercent + bestPercent) < midPercent) {
        CandidateBucket.Mid
    } else {
        CandidateBucket.Best
    }
}

/**
 * [targetBucket]으로 이번 턴 목표 버킷을 정한 뒤, 그 버킷이 이번 턴 후보 개수에서
 * 비어 있으면 최하수→중급수→최적수 순으로 폴백해 실제로 선택 가능한 인덱스 구간을
 * 돌려준다. `candidateCount ≥ 1`이면 최적수는 항상 존재하므로 이 함수는 그 경우
 * 절대 `null`을 반환하지 않는다.
 */
fun MoveSelectionPolicy.BucketedTierSelection.resolveIndexRange(
    candidateCount: Int,
    ownMoveIndex: Int,
    random: Random,
): IntRange? {
    if (candidateCount <= 0) return null
    val target = targetBucket(ownMoveIndex, random)
    val fallbackOrder = when (target) {
        CandidateBucket.Worst -> listOf(CandidateBucket.Worst, CandidateBucket.Mid, CandidateBucket.Best)
        CandidateBucket.Mid -> listOf(CandidateBucket.Mid, CandidateBucket.Best)
        CandidateBucket.Best -> listOf(CandidateBucket.Best)
    }
    for (bucket in fallbackOrder) {
        candidateBucketRange(candidateCount, bucket)?.let { return it }
    }
    return null
}
