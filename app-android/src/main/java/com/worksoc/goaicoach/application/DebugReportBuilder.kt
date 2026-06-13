package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.summary
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DeadStoneRemoval
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe

internal data class DebugReportSnapshot(
    val mode: MatchMode,
    val playerSetup: PlayerSetup,
    val engineName: String,
    val engineDiagnostic: String,
    val engineProfile: EngineProfile,
    val playLevel: PlayLevelSetting,
    val analysisPreset: AnalysisPreset,
    val analysisCacheStats: String,
    val positionAnalysisCacheStats: String = "disabled",
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    val isGameEnded: Boolean,
    val topMovesEnabled: Boolean,
    val topMoveCandidateCount: Int,
    val moveAnalysisCoverage: String,
    val gameState: GameState,
    val engineMessage: String,
    val candidateText: String,
    val scoreText: String,
    val scoreSnapshots: List<ScoreSnapshot>,
    val moveReviewText: String,
    val lastMoveText: String,
    val endgameLog: String,
    val engineBenchmarkText: String,
    val turnTimeText: String = "Time B 0.0s / W 0.0s",
    val turnTimeDebugText: String = "blackMillis=0, whiteMillis=0, currentTurn=Black, currentElapsedMillis=0",
    val runtimeEventLogText: String = "Runtime event log not loaded.",
    val searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
)

internal fun buildDebugReport(snapshot: DebugReportSnapshot): String =
    buildDebugReport(
        mode = snapshot.mode,
        playerSetup = snapshot.playerSetup,
        engineName = snapshot.engineName,
        engineDiagnostic = snapshot.engineDiagnostic,
        engineProfile = snapshot.engineProfile,
        playLevel = snapshot.playLevel,
        analysisPreset = snapshot.analysisPreset,
        analysisCacheStats = snapshot.analysisCacheStats,
        positionAnalysisCacheStats = snapshot.positionAnalysisCacheStats,
        isEngineReady = snapshot.isEngineReady,
        isEngineBusy = snapshot.isEngineBusy,
        isGameEnded = snapshot.isGameEnded,
        topMovesEnabled = snapshot.topMovesEnabled,
        topMoveCandidateCount = snapshot.topMoveCandidateCount,
        moveAnalysisCoverage = snapshot.moveAnalysisCoverage,
        gameState = snapshot.gameState,
        engineMessage = snapshot.engineMessage,
        candidateText = snapshot.candidateText,
        scoreText = snapshot.scoreText,
        scoreSnapshots = snapshot.scoreSnapshots,
        moveReviewText = snapshot.moveReviewText,
        lastMoveText = snapshot.lastMoveText,
        endgameLog = snapshot.endgameLog,
        engineBenchmarkText = snapshot.engineBenchmarkText,
        turnTimeText = snapshot.turnTimeText,
        turnTimeDebugText = snapshot.turnTimeDebugText,
        runtimeEventLogText = snapshot.runtimeEventLogText,
        searchTimeSettings = snapshot.searchTimeSettings,
    )

internal fun buildDebugReport(
    mode: MatchMode,
    playerSetup: PlayerSetup,
    engineName: String,
    engineDiagnostic: String,
    engineProfile: EngineProfile,
    playLevel: PlayLevelSetting,
    analysisPreset: AnalysisPreset,
    analysisCacheStats: String,
    positionAnalysisCacheStats: String = "disabled",
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    isGameEnded: Boolean,
    topMovesEnabled: Boolean,
    topMoveCandidateCount: Int,
    moveAnalysisCoverage: String,
    gameState: GameState,
    engineMessage: String,
    candidateText: String,
    scoreText: String,
    scoreSnapshots: List<ScoreSnapshot>,
    moveReviewText: String,
    lastMoveText: String,
    endgameLog: String,
    engineBenchmarkText: String,
    turnTimeText: String = "Time B 0.0s / W 0.0s",
    turnTimeDebugText: String = "blackMillis=0, whiteMillis=0, currentTurn=Black, currentElapsedMillis=0",
    runtimeEventLogText: String = "Runtime event log not loaded.",
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
): String {
    val localScoreText = BoardScorer.score(gameState).toDisplayText()

    return buildString {
        appendLine("Go AI Coach debug report")
        appendLine("createdAtMillis=${System.currentTimeMillis()}")
        appendLine()
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
        appendLine("[Board]")
        appendLine(gameState.toBoardText())
        appendLine()
        appendLine("[Stones]")
        appendLine(gameState.toStonesText())
        appendLine()
        appendLine("[Moves]")
        appendLine(gameState.toMovesText())
        appendLine()
        appendLine("[EndgameLog]")
        appendLine(endgameLog)
        appendLine()
        appendLine("[LocalRulesetScoreNow]")
        appendLine(localScoreText)
        appendLine()
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
        appendLine("[EngineDiagnostic]")
        appendLine(engineDiagnostic)
        appendLine()
        appendLine("[EngineBenchmark]")
        appendLine(engineBenchmarkText)
        appendLine()
        appendLine("[RuntimeEventLog]")
        appendLine(runtimeEventLogText)
    }.trim()
}

internal fun buildEndgameLog(
    source: String,
    state: GameState,
    finalScoreText: String,
    detail: String,
): String =
    buildString {
        appendLine("source=$source")
        appendLine("recordedAtMillis=${System.currentTimeMillis()}")
        appendLine("detail=$detail")
        appendLine("moveCount=${state.moves.size}")
        appendLine("lastTwoMoves=${state.moves.takeLast(2).joinToString { it.describe(state.boardSize) }}")
        appendLine("consecutivePasses=${state.hasConsecutivePasses()}")
        appendLine("boardFull=${state.isBoardFull()}")
        appendLine("capturedByBlack=${state.capturedBy(StoneColor.Black)}")
        appendLine("capturedByWhite=${state.capturedBy(StoneColor.White)}")
        appendLine("finalScoreText:")
        appendLine(finalScoreText)
    }.trim()

internal fun List<DeadStoneRemoval>.toLogText(boardSize: BoardSize): String =
    if (isEmpty()) {
        "none"
    } else {
        joinToString { removal ->
            "${removal.coordinate.label(boardSize)}=${removal.color.label}"
        }
    }

internal fun List<BoardCoordinate>.toCoordinateLogText(boardSize: BoardSize): String =
    if (isEmpty()) {
        "none"
    } else {
        distinct().joinToString { it.label(boardSize) }
    }

internal fun List<CandidateMove>.toCandidateLogText(boardSize: BoardSize): String =
    if (isEmpty()) {
        "none"
    } else {
        joinToString(separator = " | ") { candidate ->
            buildString {
                append(candidate.move.describe(boardSize))
                candidate.scoreLead?.let { append(" scoreLead=$it") }
                candidate.pointLoss?.let { append(" pointLoss=$it") }
                candidate.winRate?.let { append(" winRate=$it") }
            }
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
