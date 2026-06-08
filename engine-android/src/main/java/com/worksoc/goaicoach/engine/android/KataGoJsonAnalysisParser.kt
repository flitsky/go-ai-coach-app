package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.CandidateMoveSource
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import org.json.JSONObject

internal object KataGoJsonAnalysisParser {
    fun parseRootWhiteScoreLead(response: String): Double? =
        JSONObject(response)
            .optJSONObject("rootInfo")
            ?.optNullableDouble("scoreLead")
            ?.let { -it }

    fun parseCandidates(
        response: String,
        player: StoneColor,
        boardSize: BoardSize,
        maxCandidates: Int,
    ): List<CandidateMove> {
        val root = JSONObject(response)
        val moveInfos = root.optJSONArray("moveInfos") ?: return emptyList()
        val rootScoreLead = root.optJSONObject("rootInfo")
            ?.optNullableDouble("scoreLead")
            ?.let { -it }
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
                            engineOrder = moveInfo.optInt("order", index),
                            source = CandidateMoveSource.EngineSearch,
                            note = "KataGo JSON order ${moveInfo.optInt("order", index)}",
                        ),
                    ),
                )
            }
        }

        val referenceScoreLead = rootScoreLead ?: parsed
            .minByOrNull { it.order }
            ?.candidate
            ?.scoreLead

        return parsed
            .sortedBy { it.order }
            .take(maxCandidates)
            .map { ordered ->
                val scoreLead = ordered.candidate.scoreLead
                if (referenceScoreLead == null || scoreLead == null) {
                    ordered.candidate
                } else {
                    ordered.candidate.copy(
                        pointLoss = scoreLead.pointLossFromReference(
                            player = player,
                            referenceScoreLead = referenceScoreLead,
                        ),
                    )
                }
            }
    }

    fun parsePolicyCandidates(
        response: String,
        player: StoneColor,
        boardSize: BoardSize,
        maxCandidates: Int,
        excludedCoordinates: Set<BoardCoordinate> = emptySet(),
    ): List<CandidateMove> {
        val policy = JSONObject(response).optJSONArray("policy") ?: return emptyList()
        val boardPointCount = boardSize.value * boardSize.value
        if (policy.length() < boardPointCount) {
            return emptyList()
        }

        return (0 until boardPointCount)
            .asSequence()
            .map { index ->
                val coordinate = BoardCoordinate(
                    row = index / boardSize.value,
                    column = index % boardSize.value,
                )
                coordinate to policy.optDouble(index, -1.0)
            }
            .filter { (coordinate, prior) ->
                prior >= 0.0 && coordinate !in excludedCoordinates
            }
            .sortedByDescending { (_, prior) -> prior }
            .take(maxCandidates)
            .mapIndexed { index, (coordinate, prior) ->
                CandidateMove(
                    move = Move.Play(player, coordinate),
                    policyPrior = prior,
                    source = CandidateMoveSource.PolicyOnly,
                    note = "KataGo JSON policy fallback ${index + 1}",
                )
            }
            .toList()
    }

    fun parseRefinedCandidate(
        response: String,
        player: StoneColor,
        move: Move.Play,
        referenceScoreLead: Double,
        policyPrior: Double?,
    ): CandidateMove? {
        val rootInfo = JSONObject(response).optJSONObject("rootInfo") ?: return null
        val blackScoreLead = rootInfo.optNullableDouble("scoreLead") ?: return null
        val scoreLead = -blackScoreLead
        val blackWinRate = rootInfo.optNullableDouble("winrate")
        return CandidateMove(
            move = move,
            winRate = blackWinRate?.toPlayerWinRate(player),
            scoreLead = scoreLead,
            pointLoss = scoreLead.pointLossFromReference(
                player = player,
                referenceScoreLead = referenceScoreLead,
            ),
            visits = rootInfo.optNullableInt("visits"),
            policyPrior = policyPrior,
            source = CandidateMoveSource.PolicyRefine,
            note = "KataGo JSON refine",
        )
    }

    private data class OrderedCandidate(
        val order: Int,
        val candidate: CandidateMove,
    )

    private fun Double.pointLossFromReference(
        player: StoneColor,
        referenceScoreLead: Double,
    ): Double {
        val rawLoss = when (player) {
            StoneColor.Black -> this - referenceScoreLead
            StoneColor.White -> referenceScoreLead - this
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
