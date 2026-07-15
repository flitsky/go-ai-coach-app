package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.DefaultKomi
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor

internal object KataGoProtocolCommands {
    fun boardSize(boardSize: BoardSize): String = "boardsize ${boardSize.value}"

    fun komi(komi: Double = DefaultKomi): String = "komi $komi"

    fun rules(ruleset: Ruleset): String = "kata-set-rules ${ruleset.katagoName}"

    fun clearBoard(): String = "clear_board"

    fun setFreeHandicap(positions: List<BoardCoordinate>, boardSize: BoardSize): String {
        val vertices = positions.joinToString(" ") { it.label(boardSize) }
        return "set_free_handicap $vertices"
    }

    fun play(move: Move, boardSize: BoardSize): String =
        "play ${move.player.toGtpColor()} ${move.toGtpVertex(boardSize)}"

    fun genMove(player: StoneColor): String = "genmove ${player.toGtpColor()}"

    fun undo(): String = "undo"

    fun clearSearchCache(): String = "clear_cache"

    fun rawNn(): String = "kata-raw-nn 0"

    fun searchAnalyze(player: StoneColor, limit: AnalysisLimit): String {
        val timeMillis = limit.timeMillis ?: return "kata-search_analyze ${player.toGtpColor()}"
        val centiseconds = ((timeMillis + 9) / 10).coerceAtLeast(1)
        return "kata-search_analyze ${player.toGtpColor()} $centiseconds"
    }

    fun setMaxVisits(visits: Int): String = "kata-set-param maxVisits $visits"

    fun setMaxTime(timeMillis: Long): String {
        val seconds = (timeMillis / 1_000.0).coerceAtLeast(0.001)
        return "kata-set-param maxTime $seconds"
    }

    fun finalScore(): String = "final_score"

    fun finalStatusList(status: String): String = "final_status_list $status"

    fun quit(): String = "quit"
}

internal fun StoneColor.toGtpColor(): String =
    when (this) {
        StoneColor.Black -> "B"
        StoneColor.White -> "W"
    }

internal fun Move.toGtpVertex(boardSize: BoardSize): String =
    when (this) {
        is Move.Play -> coordinate.label(boardSize)
        is Move.Pass -> "pass"
        is Move.Resign -> "resign"
    }
