package com.worksoc.goaicoach.shared

import kotlin.math.abs
import kotlin.math.roundToInt

enum class EndgameScoreSource {
    CleanedLocalArea,
    UnsettledEngineEstimate,
    UnsettledPrePassTopMoveEstimate,
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
        prePassCandidates: List<CandidateMove> = emptyList(),
        disagreementThreshold: Double = DefaultDisagreementThreshold,
    ): EndgameScoreSelection {
        val localLead = localScore.whiteScoreLead()
        val prePassLead = prePassCandidates.bestScoredPlayableLead()
        val prePassScore = prePassLead?.toUnsettledFinalScore(
            localScore = localScore,
            statusMessage = "Pre-pass Top Moves estimate complete.",
            summaryPrefix = "KataGo pre-pass Top Moves estimate",
        )
        val engineLead = engineEstimate?.whiteScoreLead
        val engineScore = engineLead?.toUnsettledFinalScore(
            localScore = localScore,
            statusMessage = "KataGo NN endgame estimate complete.",
            summaryPrefix = "KataGo NN estimate after pass/pass",
        )

        return if (
            localLead != null &&
            prePassLead != null &&
            prePassScore != null &&
            prePassCandidates.hasScoredPassCandidate() &&
            abs(prePassLead - localLead) >= disagreementThreshold
        ) {
            EndgameScoreSelection(
                displayScore = prePassScore,
                source = EndgameScoreSource.UnsettledPrePassTopMoveEstimate,
            )
        } else if (
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

    private fun List<CandidateMove>.bestScoredPlayableLead(): Double? =
        firstOrNull { candidate ->
            candidate.scoreLead != null && candidate.move is Move.Play
        }?.scoreLead

    private fun List<CandidateMove>.hasScoredPassCandidate(): Boolean =
        any { candidate ->
            candidate.scoreLead != null && candidate.move is Move.Pass
        }

    private fun Double.toUnsettledFinalScore(
        localScore: FinalScoreResult,
        statusMessage: String,
        summaryPrefix: String,
    ): FinalScoreResult {
        val lead = this
        val winner = if (lead >= 0.0) StoneColor.White else StoneColor.Black
        val margin = abs(lead)
        val prefix = when (winner) {
            StoneColor.Black -> "B"
            StoneColor.White -> "W"
        }

        return FinalScoreResult(
            status = EngineStatus.ready(statusMessage),
            rawScore = "$prefix+${margin.formatOneDecimal()}?",
            winner = winner,
            margin = margin,
            summary = "$summaryPrefix. Local area final on the current board is ${localScore.rawScore}; the position may still require cleanup or playout.",
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
