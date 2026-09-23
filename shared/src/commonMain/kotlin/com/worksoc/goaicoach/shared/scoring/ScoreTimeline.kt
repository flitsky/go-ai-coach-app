package com.worksoc.goaicoach.shared

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
    EngineEstimate,
    LocalAreaEstimate,
    FinalScore,
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
            source = ScoreSnapshotSource.EngineEstimate,
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
