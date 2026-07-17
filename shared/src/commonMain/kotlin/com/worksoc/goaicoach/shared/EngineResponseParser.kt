package com.worksoc.goaicoach.shared

import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.math.roundToInt

fun Double.formatOneDecimal(): String =
    ((this * 10).roundToInt() / 10.0).toString()

fun extractScoreLead(text: String): Double? {
    val regex = Regex("""scoreLead=([-\d.]+)""")
    return regex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
}

fun extractAiSelectedRank(text: String): String? {
    val regex = Regex("""AI\s+[sS]elected\s+rank\s+(\d+/\d+)""")
    return regex.find(text)?.groupValues?.getOrNull(1)
}

fun extractVisitDiagnostics(text: String): String? {
    val regex = Regex("""Visit diagnostics:\s*(request=\d+,\s*root=[^,]+,\s*elapsedMs=\d+,\s*timeCapMs=[^,]+,\s*fill=[A-Z]+)""")
    return regex.find(text)?.groupValues?.getOrNull(1)
}

fun extractSearchedCount(rawText: String, candidateText: String?): Int {
    val returnedRegex = Regex("""Returned (\d+) searched candidate""")
    returnedRegex.find(rawText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

    candidateText?.let { cand ->
        val count = cand.lines().count { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && trimmed.first().isDigit()
        }
        if (count > 0) return count
    }
    return 4
}

fun formatCandidateLineCompact(line: String): String {
    var formatted = line.trim()
    formatted = formatted.replace(Regex("""\s+visits=\d+"""), "")
    formatted = formatted.replace(Regex("""\s+prior=\d+%"""), "")
    val hyphenIndex = formatted.indexOf(" - ")
    if (hyphenIndex != -1) {
        formatted = formatted.substring(0, hyphenIndex).trim()
    }
    return formatted
}

data class SideAnalysisDebugState(
    val black: SideAnalysisDebugText,
    val white: SideAnalysisDebugText,
) {
    val hasAnyContent: Boolean
        get() = black.hasContent || white.hasContent
}

data class SideAnalysisDebugText(
    val candidateText: String? = null,
    val selectedMoveText: String? = null,
    val moveReviewText: String? = null,
) {
    val hasContent: Boolean
        get() = candidateText != null || selectedMoveText != null || moveReviewText != null
}

fun buildSideAnalysisDebugState(
    moves: List<Move>,
    candidateText: String,
    engineMessage: String,
    moveReviewText: String,
    sideAnalysisTexts: Map<StoneColor, String>,
): SideAnalysisDebugState {
    val candidateOwner = inferAnalysisOwner(candidateText)
    val analysisTextsByPlayer = sideAnalysisTexts
        .filterValues { text -> text.isNotBlank() }
        .toMutableMap()
    if (candidateOwner != null && candidateText.isNotBlank()) {
        analysisTextsByPlayer[candidateOwner] = candidateText
    }
    val selectedMoveText = extractAiSelectedLine(
        listOf(engineMessage, candidateText).joinToString("\n"),
    )
    val selectedOwner = selectedMoveText?.let {
        candidateOwner ?: lastMovePlayer(moves)
    }
    val reviewOwner = moveReviewText
        .takeIf { it.isNotBlank() }
        ?.let { lastMovePlayer(moves) }

    fun stateFor(player: StoneColor): SideAnalysisDebugText {
        val sideText = analysisTextsByPlayer[player]
        return SideAnalysisDebugText(
            candidateText = sideText,
            selectedMoveText = extractAiSelectedLine(sideText.orEmpty())
                ?: selectedMoveText.takeIf { selectedOwner == player },
            moveReviewText = moveReviewText
                .takeIf { it.isNotBlank() && reviewOwner == player },
        )
    }

    return SideAnalysisDebugState(
        black = stateFor(StoneColor.Black),
        white = stateFor(StoneColor.White),
    )
}

fun inferAnalysisOwner(candidateText: String): StoneColor? {
    val explicit = Regex("""\bfor\s+(Black|White)\b""")
        .find(candidateText)
        ?.groupValues
        ?.getOrNull(1)
        ?.toStoneColorOrNull()
    if (explicit != null) return explicit

    return Regex("""(?m)^\d+\.\s+(Black|White)\b""")
        .find(candidateText)
        ?.groupValues
        ?.getOrNull(1)
        ?.toStoneColorOrNull()
}

fun extractAiSelectedLine(engineMessage: String): String? =
    engineMessage
        .lines()
        .firstOrNull { line -> line.contains("AI selected") }
        ?.trim()

fun lastMovePlayer(moves: List<Move>): StoneColor? =
    when (val lastMove = moves.lastOrNull()) {
        is Move.Play -> lastMove.player
        is Move.Pass -> lastMove.player
        is Move.Resign -> lastMove.player
        null -> null
    }

fun String.toStoneColorOrNull(): StoneColor? =
    when (this) {
        "Black" -> StoneColor.Black
        "White" -> StoneColor.White
        else -> null
    }
