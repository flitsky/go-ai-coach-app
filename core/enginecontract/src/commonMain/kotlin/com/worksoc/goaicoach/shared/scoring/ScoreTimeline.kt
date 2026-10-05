package com.worksoc.goaicoach.shared.scoring

import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate

data class ScoreSnapshot(
    val moveNumber: Int,
    val whiteScoreLead: Double? = null,
    val whiteWinRate: Double? = null,
    val source: ScoreSnapshotSource,
) {
    init {
        require(moveNumber >= 0) { "moveNumber must be non-negative" }
        require(whiteWinRate == null || whiteWinRate in 0.0..1.0) {
            "whiteWinRate must be between 0 and 1 when set"
        }
    }

    val hasScoreData: Boolean
        get() = whiteScoreLead != null || whiteWinRate != null
}

enum class ScoreSnapshotSource {
    /** 주 모델(가장 센 망)의 신경망 평가. */
    EngineEstimate,

    /**
     * **사람 모델**이 가장 센 프로필로 본 신경망 평가 — 급수 캐릭터와 두는 동안 수마다 남는 **임시 값**이다(백로그 #215).
     * 주 모델보다 덜 정확하다(형세 오차 0.5 → 2.5집, 실험실 E6). 대국이 끝나면 주 모델로 다시 재어 [EngineEstimate]로 바꾼다.
     */
    HumanNetworkEstimate,
    LocalAreaEstimate,
    FinalScore,
    ;

    /** 신경망이 본 값인가(주 모델이든 사람 모델이든) — 국소 계가·종국 계가와 가른다. */
    val isNetworkEstimate: Boolean
        get() = this == EngineEstimate || this == HumanNetworkEstimate
}

object ScoreTimeline {
    fun fromEstimate(
        moveNumber: Int,
        estimate: ScoreEstimate,
    ): ScoreSnapshot =
        ScoreSnapshot(
            moveNumber = moveNumber,
            whiteScoreLead = estimate.whiteScoreLead,
            whiteWinRate = estimate.whiteWinRate,
            source = when (estimate.network) {
                EngineNetwork.Main -> ScoreSnapshotSource.EngineEstimate
                EngineNetwork.Human -> ScoreSnapshotSource.HumanNetworkEstimate
            },
        )

    fun fromFinalScore(
        moveNumber: Int,
        finalScore: FinalScoreResult,
        source: ScoreSnapshotSource = ScoreSnapshotSource.FinalScore,
    ): ScoreSnapshot =
        ScoreSnapshot(
            moveNumber = moveNumber,
            whiteScoreLead = finalScore.whiteScoreLead(),
            whiteWinRate = null,
            source = source,
        )

    fun record(
        snapshots: List<ScoreSnapshot>,
        snapshot: ScoreSnapshot,
    ): List<ScoreSnapshot> =
        (snapshots.filterNot { it.moveNumber == snapshot.moveNumber } + snapshot)
            .sortedBy { it.moveNumber }

    fun trimAfter(
        snapshots: List<ScoreSnapshot>,
        moveNumber: Int,
    ): List<ScoreSnapshot> =
        snapshots.filter { it.moveNumber <= moveNumber }

    private fun FinalScoreResult.whiteScoreLead(): Double? =
        when {
            whiteAreaWithKomi != null && blackArea != null -> whiteAreaWithKomi - blackArea
            margin != null && winner == StoneColor.White -> margin
            margin != null && winner == StoneColor.Black -> -margin
            margin != null -> 0.0
            else -> null
        }
}
