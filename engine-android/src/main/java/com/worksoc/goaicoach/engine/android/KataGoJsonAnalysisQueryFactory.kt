package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
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
        komi: Double = DefaultKomi,
        initialStones: List<Pair<StoneColor, BoardCoordinate>> = emptyList(),
        initialPlayer: StoneColor = StoneColor.Black,
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
            .put("komi", komi)
            .put("boardXSize", boardSize.value)
            .put("boardYSize", boardSize.value)
            .put("initialPlayer", initialPlayer.toGtpColor())
            .put("initialStones", initialStones.toJsonInitialStones(boardSize))
            .put("moves", queryMoves.toJsonMoves(boardSize))
            .put("analyzeTurns", JSONArray().put(queryMoves.size))
            .put("maxVisits", limit.visits)
            .put("includeOwnership", false)
            .put("includeMovesOwnership", false)
            .put("includePolicy", includePolicyOverride ?: (refineMove == null && limit.includePolicy))
            .put("overrideSettings", overrideSettings)
            .put("priority", 0)
    }

    private fun List<Pair<StoneColor, BoardCoordinate>>.toJsonInitialStones(boardSize: BoardSize): JSONArray =
        JSONArray().also { stones ->
            forEach { (color, coord) ->
                stones.put(
                    JSONArray()
                        .put(color.toGtpColor())
                        .put(coord.label(boardSize)),
                )
            }
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
