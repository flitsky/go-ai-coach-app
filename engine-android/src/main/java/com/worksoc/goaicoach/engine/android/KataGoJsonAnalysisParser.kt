package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import org.json.JSONObject

internal object KataGoJsonAnalysisParser {
    fun parseCandidates(
        response: String,
        player: StoneColor,
        boardSize: BoardSize,
        maxCandidates: Int,
    ): List<CandidateMove> {
        val root = JSONObject(response)
        val moveInfos = root.optJSONArray("moveInfos") ?: return emptyList()
        val parsed = buildList {
            for (index in 0 until moveInfos.length()) {
                val moveInfo = moveInfos.optJSONObject(index) ?: continue
                val move = moveInfo.optString("move", "").toMove(player, boardSize) ?: continue
                val blackScoreLead = moveInfo.optNullableDouble("scoreLead")
                val blackWinRate = moveInfo.optNullableDouble("winrate")
                add(
                    OrderedCandidate(
                        order = moveInfo.optInt("order", index),
                        candidate = CandidateMove(
                            move = move,
                            winRate = blackWinRate?.toPlayerWinRate(player),
                            scoreLead = blackScoreLead?.let { -it },
                            visits = moveInfo.optNullableInt("visits"),
                            policyPrior = moveInfo.optNullableDouble("prior"),
                            note = "KataGo JSON order ${moveInfo.optInt("order", index)}",
                        ),
                    ),
                )
            }
        }

        val topScoreLead = parsed
            .minByOrNull { it.order }
            ?.candidate
            ?.scoreLead

        return parsed
            .sortedBy { it.order }
            .take(maxCandidates)
            .map { ordered ->
                val scoreLead = ordered.candidate.scoreLead
                if (topScoreLead == null || scoreLead == null) {
                    ordered.candidate
                } else {
                    ordered.candidate.copy(
                        pointLoss = scoreLead.pointLossFromTop(
                            player = player,
                            topScoreLead = topScoreLead,
                        ),
                    )
                }
            }
    }

    private data class OrderedCandidate(
        val order: Int,
        val candidate: CandidateMove,
    )

    private fun Double.pointLossFromTop(
        player: StoneColor,
        topScoreLead: Double,
    ): Double {
        val rawLoss = when (player) {
            StoneColor.Black -> this - topScoreLead
            StoneColor.White -> topScoreLead - this
        }
        return if (kotlin.math.abs(rawLoss) < 0.000001) 0.0 else rawLoss.coerceAtLeast(0.0)
    }

    private fun Double.toPlayerWinRate(player: StoneColor): Double =
        when (player) {
            StoneColor.Black -> this
            StoneColor.White -> 1.0 - this
        }.coerceIn(0.0, 1.0)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun String.toMove(
        player: StoneColor,
        boardSize: BoardSize,
    ): Move? =
        when (lowercase()) {
            "pass" -> Move.Pass(player)
            "resign" -> Move.Resign(player)
            else -> runCatching { Move.Play(player, BoardCoordinate.fromLabel(this, boardSize)) }.getOrNull()
        }
}
