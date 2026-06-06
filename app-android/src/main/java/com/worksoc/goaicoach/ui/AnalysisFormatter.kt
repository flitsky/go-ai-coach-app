package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe
import kotlin.math.roundToInt

internal fun AnalysisResult.toCandidateText(boardSize: BoardSize): String {
    if (candidates.isEmpty()) {
        return summary
    }
    return buildString {
        appendLine(summary)
        candidates.forEachIndexed { index, candidate ->
            append(index + 1)
            append(". ")
            append(candidate.move.describe(boardSize))
            candidate.winRate?.let { append(" WR=${(it * 100).roundToInt()}%") }
            candidate.playerPerspectiveScoreLead()?.let { append(" lead=${it.formatSignedOneDecimal()}") }
            candidate.pointLoss?.let { append(" loss=${it.formatOneDecimal()}") }
            candidate.visits?.let { append(" visits=${it}") }
            candidate.policyPrior?.let { append(" prior=${(it * 100).roundToInt()}%") }
            candidate.note?.let { append(" - ${it}") }
            if (index != candidates.lastIndex) {
                appendLine()
            }
        }
    }
}

internal fun ScoreEstimate.toDisplayText(): String =
    buildString {
        appendLine(summary)
        whiteWinRate?.let { winRate ->
            appendLine("White win: ${(winRate * 100).roundToInt()}% / Black win: ${((1.0 - winRate) * 100).roundToInt()}%")
        }
        whiteScoreLead?.let { lead ->
            val leader = if (lead >= 0.0) StoneColor.White else StoneColor.Black
            appendLine("Lead: ${leader.label} by ${kotlin.math.abs(lead).formatOneDecimal()} points")
        }
        ownership?.let { estimate ->
            appendLine(
                "Influence: Black ${estimate.blackLikelyPoints}, White ${estimate.whiteLikelyPoints}, unclear ${estimate.neutralOrUnclearPoints} (threshold ${estimate.threshold.formatTwoDecimals()})",
            )
        }
    }.trim()

internal fun FinalScoreResult.toDisplayText(): String =
    buildString {
        appendLine(summary)
        appendLine("Final: $rawScore")
        winner?.let { winner ->
            appendLine("Winner: ${winner.label}${margin?.let { " by ${it.formatOneDecimal()}" } ?: ""}")
        }
        val blackAreaValue = blackArea
        val whiteAreaWithKomiValue = whiteAreaWithKomi
        val komiValue = komi
        if (blackAreaValue != null && whiteAreaWithKomiValue != null && komiValue != null) {
            appendLine("Score: Black ${blackAreaValue.formatOneDecimal()} / White+komi ${whiteAreaWithKomiValue.formatOneDecimal()} (komi ${komiValue.formatOneDecimal()})")
        }
    }.trim()

private fun Double.formatOneDecimal(): String =
    ((this * 10).roundToInt() / 10.0).toString()

private fun Double.formatTwoDecimals(): String =
    ((this * 100).roundToInt() / 100.0).toString()

private fun Double.formatSignedOneDecimal(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    val normalized = if (kotlin.math.abs(rounded) < 0.05) 0.0 else rounded
    return if (normalized > 0.0) "+$normalized" else normalized.toString()
}

private fun CandidateMove.playerPerspectiveScoreLead(): Double? {
    val player = when (val candidateMove = move) {
        is Move.Play -> candidateMove.player
        is Move.Pass -> candidateMove.player
        is Move.Resign -> candidateMove.player
    }
    return scoreLead?.let { scoreLead ->
        when (player) {
            StoneColor.Black -> -scoreLead
            StoneColor.White -> scoreLead
        }
    }
}
