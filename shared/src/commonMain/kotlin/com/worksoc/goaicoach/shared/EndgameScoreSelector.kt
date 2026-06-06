package com.worksoc.goaicoach.shared

import kotlin.math.abs
import kotlin.math.roundToInt

enum class EndgameScoreSource {
    CleanedLocalArea,
    UnsettledEngineEstimate,
}

data class EndgameScoreSelection(
    val displayScore: FinalScoreResult,
    val source: EndgameScoreSource,
)

object EndgameScoreSelector {
    fun selectDisplayScore(
        cleanup: DeadStoneCleanupResult,
        localScore: FinalScoreResult,
        engineEstimate: ScoreEstimate?,
        disagreementThreshold: Double = DefaultDisagreementThreshold,
    ): EndgameScoreSelection {
        val localLead = localScore.whiteScoreLead()
        val engineLead = engineEstimate?.whiteScoreLead
        val engineScore = engineEstimate?.toUnsettledFinalScore(localScore)

        return if (
            cleanup.removedCount == 0 &&
            localLead != null &&
            engineLead != null &&
            engineScore != null &&
            abs(engineLead - localLead) >= disagreementThreshold
        ) {
            EndgameScoreSelection(
                displayScore = engineScore,
                source = EndgameScoreSource.UnsettledEngineEstimate,
            )
        } else {
            EndgameScoreSelection(
                displayScore = localScore,
                source = EndgameScoreSource.CleanedLocalArea,
            )
        }
    }

    private fun ScoreEstimate.toUnsettledFinalScore(localScore: FinalScoreResult): FinalScoreResult? {
        val lead = whiteScoreLead ?: return null
        val winner = if (lead >= 0.0) StoneColor.White else StoneColor.Black
        val margin = abs(lead)
        val prefix = when (winner) {
            StoneColor.Black -> "B"
            StoneColor.White -> "W"
        }

        return FinalScoreResult(
            status = EngineStatus.ready("KataGo NN endgame estimate complete."),
            rawScore = "$prefix+${margin.formatOneDecimal()}?",
            winner = winner,
            margin = margin,
            summary = "KataGo NN estimate after pass/pass. Local area final on the current board is ${localScore.rawScore}; the position may still require cleanup or playout.",
        )
    }

    private fun FinalScoreResult.whiteScoreLead(): Double? =
        when {
            whiteAreaWithKomi != null && blackArea != null -> whiteAreaWithKomi - blackArea
            margin != null && winner == StoneColor.White -> margin
            margin != null && winner == StoneColor.Black -> -margin
            margin != null -> 0.0
            else -> null
        }

    private fun Double.formatOneDecimal(): String =
        ((this * 10).roundToInt() / 10.0).toString()

    private const val DefaultDisagreementThreshold = 10.0
}
