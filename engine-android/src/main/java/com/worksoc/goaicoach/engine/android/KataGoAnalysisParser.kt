package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor

internal object KataGoAnalysisParser {
    private val whitespace = Regex("\\s+")
    private val infoBoundary = Regex("(?=\\binfo move\\s)")

    fun parseCandidates(
        response: String,
        player: StoneColor,
        boardSize: BoardSize,
        maxCandidates: Int = 5,
    ): List<CandidateMove> =
        response
            .split(infoBoundary)
            .asSequence()
            .map { it.trim() }
            .filter { it.startsWith("info move ") }
            .mapNotNull { parseCandidate(it, player, boardSize) }
            .take(maxCandidates)
            .toList()

    private fun parseCandidate(
        info: String,
        player: StoneColor,
        boardSize: BoardSize,
    ): CandidateMove? {
        val tokens = info.split(whitespace).filter { it.isNotBlank() }
        if (tokens.size < 3 || tokens[0] != "info" || tokens[1] != "move") {
            return null
        }

        val move = tokens[2].toMove(player, boardSize) ?: return null
        val fields = mutableMapOf<String, String>()
        var index = 3
        while (index < tokens.size - 1) {
            val key = tokens[index]
            if (key == "pv" || key == "info") {
                break
            }
            fields[key] = tokens[index + 1]
            index += 2
        }

        return CandidateMove(
            move = move,
            winRate = fields["winrate"]?.toDoubleOrNull()?.normalizeWinRate(),
            scoreLead = fields["scoreLead"]?.toDoubleOrNull(),
            visits = fields["visits"]?.toIntOrNull(),
            note = fields["order"]?.let { "KataGo order $it" },
        )
    }

    private fun String.toMove(
        player: StoneColor,
        boardSize: BoardSize,
    ): Move? =
        when (lowercase()) {
            "pass" -> Move.Pass(player)
            "resign" -> Move.Resign(player)
            else -> runCatching { Move.Play(player, BoardCoordinate.fromLabel(this, boardSize)) }.getOrNull()
        }

    private fun Double.normalizeWinRate(): Double =
        if (this > 1.0) {
            this / 10_000.0
        } else {
            this
        }
}
