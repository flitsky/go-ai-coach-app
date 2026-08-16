package com.worksoc.goaicoach.application.debugreport

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.summary
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe

internal fun StringBuilder.appendDebugReportHeader(createdAtMillis: Long) {
    appendLine("Go AI Coach debug report")
    appendLine("createdAtMillis=$createdAtMillis")
    appendLine()
}

internal fun StringBuilder.appendRuntimeSection(
    mode: Any,
    playerSetup: PlayerSetup,
    engineName: String,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    isGameEnded: Boolean,
    engineProfile: EngineProfile,
    playLevel: PlayLevelSetting,
    searchTimeSettings: SearchTimeSettings,
    analysisPreset: AnalysisPreset,
    analysisCacheStats: String,
    positionAnalysisCacheStats: String,
    topMovesEnabled: Boolean,
    topMoveCandidateCount: Int,
    moveAnalysisCoverage: String,
    turnTimeText: String,
    turnTimeDebugText: String,
) {
    appendLine("[Runtime]")
    appendLine("mode=$mode")
    appendLine("playerSetup=${playerSetup.summary(engineName)}")
    appendLine("blackSeat=${playerSetup.black.summary(engineName)}")
    appendLine("whiteSeat=${playerSetup.white.summary(engineName)}")
    appendLine("engineName=$engineName")
    appendLine("engineReady=$isEngineReady")
    appendLine("engineBusy=$isEngineBusy")
    appendLine("gameEnded=$isGameEnded")
    appendLine("engineProfile=${engineProfile.name}/${engineProfile.mode}/${engineProfile.difficulty.label}")
    appendLine("playLevel=${playLevel.displayLabel} (${playLevel.selectionPolicy.description})")
    appendLine("analysisLimit=visits:${engineProfile.analysisLimit.visits}, timeMillis:${engineProfile.analysisLimit.timeMillis}, candidates:${engineProfile.analysisLimit.candidateCount}")
    appendLine("searchTimeSettings=${searchTimeSettings.normalized().summaryText()}")
    appendLine("analysisPreset=${analysisPreset.label}")
    appendLine("analysisCache=$analysisCacheStats")
    appendLine("positionAnalysisCache=$positionAnalysisCacheStats")
    appendLine("topMovesEnabled=$topMovesEnabled")
    appendLine("topMoveCandidateCount=$topMoveCandidateCount")
    appendLine("moveAnalysisCoverage=$moveAnalysisCoverage")
    appendLine("turnTime=$turnTimeText")
    appendLine("turnTimeDebug=$turnTimeDebugText")
    appendLine()
}

internal fun StringBuilder.appendGameStateSection(gameState: GameState) {
    appendLine("[GameState]")
    appendLine("boardSize=${gameState.boardSize.value}")
    appendLine("ruleset=${gameState.ruleset}")
    appendLine("nextPlayer=${gameState.nextPlayer.label}")
    appendLine("moves=${gameState.moves.size}")
    appendLine("capturedByBlack=${gameState.capturedBy(StoneColor.Black)}")
    appendLine("capturedByWhite=${gameState.capturedBy(StoneColor.White)}")
    appendLine("consecutivePasses=${gameState.hasConsecutivePasses()}")
    appendLine("boardFull=${gameState.isBoardFull()}")
    appendLine("koPoint=${gameState.koPoint?.label(gameState.boardSize) ?: "none"}")
    appendLine("koForbiddenFor=${gameState.koForbiddenFor?.label ?: "none"}")
    appendLine()
}

internal fun StringBuilder.appendBoardSections(gameState: GameState) {
    appendLine("[Board]")
    appendLine(gameState.toBoardText())
    appendLine()
    appendLine("[Stones]")
    appendLine(gameState.toStonesText())
    appendLine()
    appendLine("[Moves]")
    appendLine(gameState.toMovesText())
    appendLine()
}

internal fun StringBuilder.appendScoreTimelineSection(scoreSnapshots: List<ScoreSnapshot>) {
    appendLine("[ScoreTimeline]")
    if (scoreSnapshots.isEmpty()) {
        appendLine("none")
    } else {
        scoreSnapshots.forEach { snapshot ->
            appendLine(
                "${snapshot.moveNumber}. source=${snapshot.source}, whiteScoreLead=${snapshot.whiteScoreLead ?: "none"}, whiteWinRate=${snapshot.whiteWinRate ?: "none"}",
            )
        }
    }
    appendLine()
}

internal fun StringBuilder.appendDisplayedTextsSection(
    lastMoveText: String,
    engineMessage: String,
    scoreText: String,
    moveReviewText: String,
    candidateText: String,
) {
    appendLine("[DisplayedTexts]")
    appendLine("lastMove=$lastMoveText")
    appendLine("engineMessage:")
    appendLine(engineMessage)
    appendLine("scoreText:")
    appendLine(scoreText)
    appendLine("moveReviewText:")
    appendLine(moveReviewText)
    appendLine("candidateText:")
    appendLine(candidateText)
    appendLine()
}

internal fun StringBuilder.appendNamedTextSection(name: String, text: String, trailingBlankLine: Boolean = true) {
    appendLine("[$name]")
    appendLine(text)
    if (trailingBlankLine) {
        appendLine()
    }
}

private fun GameState.toBoardText(): String =
    buildString {
        val columns = boardColumnLabels(boardSize)
        append("   ")
        columns.forEach { column -> append(column).append(' ') }
        appendLine()

        for (row in 0 until boardSize.value) {
            val rowLabel = boardSize.value - row
            append(rowLabel.toString().padStart(2, ' ')).append(' ')
            for (column in 0 until boardSize.value) {
                val coordinate = BoardCoordinate(row, column)
                val marker = when (stoneAt(coordinate)) {
                    StoneColor.Black -> "X"
                    StoneColor.White -> "O"
                    null -> "."
                }
                append(marker).append(' ')
            }
            append(rowLabel)
            appendLine()
        }

        append("   ")
        columns.forEach { column -> append(column).append(' ') }
    }

private fun GameState.toStonesText(): String {
    if (stones.isEmpty()) {
        return "(none)"
    }

    return stones.entries
        .sortedWith(compareBy({ it.key.row }, { it.key.column }))
        .joinToString(separator = "\n") { (coordinate, color) ->
            "${coordinate.label(boardSize)}=${color.label}"
        }
}

private fun GameState.toMovesText(): String {
    if (moves.isEmpty()) {
        return "(none)"
    }

    return moves
        .mapIndexed { index, move -> "${index + 1}. ${move.describe(boardSize)}" }
        .joinToString(separator = "\n")
}

private fun boardColumnLabels(boardSize: BoardSize): List<Char> {
    val columns = "ABCDEFGHJKLMNOPQRSTUVWXYZ"
    return columns.take(boardSize.value).toList()
}
