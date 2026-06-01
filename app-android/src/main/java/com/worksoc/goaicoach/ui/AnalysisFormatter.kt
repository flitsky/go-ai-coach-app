package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardSize
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
            candidate.scoreLead?.let { append(" score=${it}") }
            candidate.visits?.let { append(" visits=${it}") }
            candidate.policyPrior?.let { append(" prior=${(it * 100).roundToInt()}%") }
            candidate.note?.let { append(" - ${it}") }
            if (index != candidates.lastIndex) {
                appendLine()
            }
        }
    }
}
