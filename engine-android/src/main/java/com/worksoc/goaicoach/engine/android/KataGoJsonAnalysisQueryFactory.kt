package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.DefaultKomi
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import org.json.JSONArray
import org.json.JSONObject

internal object KataGoJsonAnalysisQueryFactory {
    fun build(
        id: String,
        boardSize: BoardSize,
        ruleset: Ruleset,
        playedMoves: List<Move>,
        limit: AnalysisLimit,
        refineMove: Move.Play? = null,
        includePolicyOverride: Boolean? = null,
    ): JSONObject {
        val overrideSettings = JSONObject()
        limit.timeMillis?.let { overrideSettings.put("maxTime", it / 1_000.0) }
        val queryMoves = if (refineMove == null) {
            playedMoves
        } else {
            playedMoves + refineMove
        }

        return JSONObject()
            .put("id", id)
            .put("rules", ruleset.katagoName)
            .put("komi", DefaultKomi)
            .put("boardXSize", boardSize.value)
            .put("boardYSize", boardSize.value)
            .put("initialPlayer", "B")
            .put("initialStones", JSONArray())
            .put("moves", queryMoves.toJsonMoves(boardSize))
            .put("analyzeTurns", JSONArray().put(queryMoves.size))
            .put("maxVisits", limit.visits)
            .put("includeOwnership", false)
            .put("includeMovesOwnership", false)
            .put("includePolicy", includePolicyOverride ?: (refineMove == null && limit.includePolicy))
            .put("overrideSettings", overrideSettings)
            .put("priority", 0)
    }

    private fun List<Move>.toJsonMoves(boardSize: BoardSize): JSONArray =
        JSONArray().also { moves ->
            forEach { move ->
                moves.put(
                    JSONArray()
                        .put(move.player.toGtpColor())
                        .put(move.toGtpVertex(boardSize)),
                )
            }
        }
}
