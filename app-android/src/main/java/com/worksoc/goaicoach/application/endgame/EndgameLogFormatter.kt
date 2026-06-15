package com.worksoc.goaicoach.application.endgame

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DeadStoneRemoval
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe

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
