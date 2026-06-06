package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.OwnershipEstimate
import com.worksoc.goaicoach.shared.OwnershipPoint
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.math.abs

internal object KataGoAnalysisParser {
    private val whitespace = Regex("\\s+")
    private val infoBoundary = Regex("(?=\\binfo move\\s)")
    private val finalScorePattern = Regex("^([BW])\\+([0-9]+(?:\\.[0-9]+)?)$")

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

    fun attachPointLoss(
        candidates: List<CandidateMove>,
        player: StoneColor,
    ): List<CandidateMove> {
        val topScoreLead = candidates.firstOrNull { it.scoreLead != null }?.scoreLead
            ?: return candidates
        val playerSign = when (player) {
            StoneColor.Black -> -1.0
            StoneColor.White -> 1.0
        }

        return candidates.map { candidate ->
            val scoreLead = candidate.scoreLead ?: return@map candidate
            val rawPointLoss = playerSign * (topScoreLead - scoreLead)
            val pointLoss = if (abs(rawPointLoss) < 0.000001) {
                0.0
            } else {
                rawPointLoss.coerceAtLeast(0.0)
            }
            candidate.copy(pointLoss = pointLoss)
        }
    }

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
            policyPrior = fields["prior"]?.toDoubleOrNull(),
            note = fields["order"]?.let { "KataGo order $it" },
        )
    }

    fun parsePolicyCandidates(
        response: String,
        player: StoneColor,
        boardSize: BoardSize,
        maxCandidates: Int,
        excludedCoordinates: Set<BoardCoordinate> = emptySet(),
    ): List<CandidateMove> {
        val rows = response
            .lineSequence()
            .dropWhile { it.trim() != "policy" }
            .drop(1)
            .take(boardSize.value)
            .map { line ->
                line.trim()
                    .split(whitespace)
                    .filter { it.isNotBlank() }
                    .mapNotNull { it.toDoubleOrNull() }
            }
            .toList()

        if (rows.size != boardSize.value || rows.any { it.size != boardSize.value }) {
            return emptyList()
        }

        return rows
            .flatMapIndexed { row, values ->
                values.mapIndexed { column, prior ->
                    BoardCoordinate(row, column) to prior
                }
            }
            .asSequence()
            .filter { (coordinate, prior) -> coordinate !in excludedCoordinates && prior > 0.0 }
            .sortedByDescending { (_, prior) -> prior }
            .take(maxCandidates)
            .mapIndexed { index, (coordinate, prior) ->
                CandidateMove(
                    move = Move.Play(player, coordinate),
                    policyPrior = prior,
                    note = "NN policy fallback ${index + 1}",
                )
            }
            .toList()
    }

    fun parseScoreEstimate(
        response: String,
        boardSize: BoardSize,
        ownershipThreshold: Double = 0.15,
    ): ScoreEstimate {
        val fields = response
            .lineSequence()
            .map { it.trim() }
            .mapNotNull { line ->
                val tokens = line.split(whitespace).filter { it.isNotBlank() }
                if (tokens.size == 2) tokens[0] to tokens[1] else null
            }
            .toMap()
        val ownership = parseOwnershipEstimate(response, boardSize, ownershipThreshold)

        return ScoreEstimate(
            status = EngineStatus.ready("KataGo raw NN score estimate complete."),
            whiteWinRate = fields["whiteWin"]?.toDoubleOrNull(),
            whiteScoreLead = fields["whiteLead"]?.toDoubleOrNull(),
            ownership = ownership,
            summary = "Raw NN estimate. Positive score lead favors White; negative favors Black. Ownership counts are an influence indicator, not final scoring.",
        )
    }

    fun parseFinalScore(rawScore: String): FinalScoreResult {
        val trimmed = rawScore.trim()
        val match = finalScorePattern.matchEntire(trimmed)
        if (match == null) {
            return FinalScoreResult(
                status = EngineStatus.ready("KataGo final score complete."),
                rawScore = trimmed,
                summary = "KataGo final_score returned: $trimmed",
            )
        }

        val winner = when (match.groupValues[1]) {
            "B" -> StoneColor.Black
            "W" -> StoneColor.White
            else -> null
        }
        val margin = match.groupValues[2].toDouble()
        return FinalScoreResult(
            status = EngineStatus.ready("KataGo final score complete."),
            rawScore = trimmed,
            winner = winner,
            margin = margin,
            summary = "KataGo final_score returned $trimmed.",
        )
    }

    fun parseFinalStatusList(
        response: String,
        boardSize: BoardSize,
    ): List<BoardCoordinate> =
        response
            .replace(",", " ")
            .lineSequence()
            .flatMap { line -> line.trim().split(whitespace) }
            .map { token -> token.trim() }
            .filter { token -> token.isNotBlank() && token != "=" }
            .mapNotNull { token ->
                runCatching { BoardCoordinate.fromLabel(token, boardSize) }.getOrNull()
            }
            .distinct()
            .toList()

    private fun parseOwnershipEstimate(
        response: String,
        boardSize: BoardSize,
        threshold: Double,
    ): OwnershipEstimate? {
        val rows = response
            .lineSequence()
            .dropWhile { it.trim() != "whiteOwnership" }
            .drop(1)
            .take(boardSize.value)
            .map { line ->
                line.trim()
                    .split(whitespace)
                    .filter { it.isNotBlank() }
                    .mapNotNull { it.toDoubleOrNull() }
            }
            .toList()

        if (rows.size != boardSize.value || rows.any { it.size != boardSize.value }) {
            return null
        }

        var blackLikely = 0
        var whiteLikely = 0
        var unclear = 0
        val points = mutableListOf<OwnershipPoint>()
        val absoluteThreshold = abs(threshold)
        rows.forEachIndexed { row, values ->
            values.forEachIndexed { column, value ->
                when {
                    value <= -absoluteThreshold -> blackLikely += 1
                    value >= absoluteThreshold -> whiteLikely += 1
                    else -> unclear += 1
                }
                points += OwnershipPoint(
                    coordinate = BoardCoordinate(row, column),
                    value = value,
                )
            }
        }

        return OwnershipEstimate(
            blackLikelyPoints = blackLikely,
            whiteLikelyPoints = whiteLikely,
            neutralOrUnclearPoints = unclear,
            threshold = absoluteThreshold,
            points = points,
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
