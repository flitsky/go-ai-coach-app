package com.worksoc.goaicoach.application.analysis

import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe
import com.worksoc.goaicoach.shared.pointLossLabel
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
            candidate.pointLossLabel()?.let { append(" loss=$it") }
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

internal fun translateScoreText(text: String): String {
    if (text == "Score estimate not current.") return "형세 판단 대기 중"
    if (text == "No score estimate yet.") return "형세 분석 진행 전"
    
    return text.lines().joinToString("\n") { line ->
        var translated = line.trim()
        
        val summaryRegex = Regex("""^(B|W)\+([\d.]+)(?:\s+(Area|Territory))?$""")
        summaryRegex.matchEntire(translated)?.let { match ->
            val color = if (match.groupValues[1] == "B") "흑" else "백"
            val score = match.groupValues[2]
            val rule = match.groupValues[3].let {
                when (it) {
                    "Area" -> "영역"
                    "Territory" -> "집"
                    else -> ""
                }
            }
            translated = if (rule.isNotEmpty()) {
                "$color ${score}집 우세 ($rule)"
            } else {
                "$color ${score}집 우세"
            }
            return@joinToString translated
        }

        val winRateRegex = Regex("""White win:\s*(\d+)%\s*/\s*Black win:\s*(\d+)%""")
        winRateRegex.find(translated)?.let { match ->
            translated = "백 승률: ${match.groupValues[1]}% / 흑 승률: ${match.groupValues[2]}%"
            return@joinToString translated
        }

        val leadRegex = Regex("""Lead:\s*(White|Black)\s+by\s+([\d.]+)\s+points""")
        leadRegex.find(translated)?.let { match ->
            val leader = if (match.groupValues[1] == "Black") "흑" else "백"
            translated = "우세: $leader ${match.groupValues[2]}집"
            return@joinToString translated
        }

        val influenceRegex = Regex("""Influence:\s*Black\s+([\d.]+),\s*White\s+([\d.]+),\s*unclear\s+([\d.]+)\s*\(threshold\s*([\d.]+)\)""")
        influenceRegex.find(translated)?.let { match ->
            translated = "영향력: 흑 ${match.groupValues[1]}, 백 ${match.groupValues[2]}, 불확실 ${match.groupValues[3]} (임계값 ${match.groupValues[4]})"
            return@joinToString translated
        }

        val winnerRegex = Regex("""Winner:\s*(White|Black)(?:\s+by\s+([\d.]+))?""")
        winnerRegex.find(translated)?.let { match ->
            val winner = if (match.groupValues[1] == "Black") "흑" else "백"
            val margin = match.groupValues[2]
            translated = if (margin.isNotEmpty()) {
                "승자: $winner ${margin}집"
            } else {
                "승자: $winner"
            }
            return@joinToString translated
        }

        val finalRegex = Regex("""Final:\s*(.*)""")
        finalRegex.find(translated)?.let { match ->
            val raw = match.groupValues[1]
            val subMatch = summaryRegex.matchEntire(raw)
            val scoreText = if (subMatch != null) {
                val color = if (subMatch.groupValues[1] == "B") "흑" else "백"
                val score = subMatch.groupValues[2]
                "$color ${score}집 우세"
            } else {
                raw
            }
            translated = "최종 결과: $scoreText"
            return@joinToString translated
        }

        val scoreRegex = Regex("""Score:\s*Black\s+([\d.]+)\s*/\s*White\+komi\s+([\d.]+)\s*\(komi\s*([\d.]+)\)""")
        scoreRegex.find(translated)?.let { match ->
            translated = "점수: 흑 ${match.groupValues[1]} / 백+덤 ${match.groupValues[2]} (덤 ${match.groupValues[3]})"
            return@joinToString translated
        }

        translated
    }
}
