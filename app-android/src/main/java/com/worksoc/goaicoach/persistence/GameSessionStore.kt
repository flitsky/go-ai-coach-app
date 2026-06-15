package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.savedgame.SavedGameStorePort
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.decodePlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.decodePlayLevel
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.encodePlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.encodePlayLevel
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.GameStateReplayer
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import org.json.JSONArray
import org.json.JSONObject

internal class GameSessionStore(context: Context) : SavedGameStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun save(snapshot: SavedGameSnapshot) {
        if (!snapshot.isResumable) {
            clear()
            return
        }
        prefs.edit()
            .putString(SessionKey, SavedGameSessionCodec.encode(snapshot))
            .apply()
    }

    override fun load(): SavedGameSnapshot? {
        val raw = prefs.getString(SessionKey, null) ?: return null
        return SavedGameSessionCodec.decode(raw)
            .also { snapshot ->
                if (snapshot == null || !snapshot.isResumable) {
                    clear()
                }
            }
            ?.takeIf { snapshot -> snapshot.isResumable }
    }

    override fun clear() {
        prefs.edit().remove(SessionKey).apply()
    }

    private companion object {
        const val PrefsName = "go_ai_coach_session"
        const val SessionKey = "active_game_snapshot"
    }
}

internal object SavedGameSessionCodec {
    private const val SchemaVersion = 1

    fun encode(snapshot: SavedGameSnapshot): String =
        JSONObject()
            .put("schema", SchemaVersion)
            .put("savedAtMillis", snapshot.savedAtMillis)
            .put("boardSize", snapshot.gameState.boardSize.value)
            .put("ruleset", snapshot.gameState.ruleset.name)
            .put("moves", encodeMoves(snapshot.gameState.moves, snapshot.gameState.boardSize))
            .put("playerSetup", encodePlayerSetup(snapshot.playerSetup))
            .put("playLevel", encodePlayLevel(snapshot.playLevel))
            .put("topMovesEnabled", snapshot.topMovesEnabled)
            .toString()

    fun decode(raw: String): SavedGameSnapshot? =
        runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", SchemaVersion) != SchemaVersion) {
                return@runCatching null
            }
            val boardSize = BoardSize(json.optInt("boardSize", BoardSize.Nine.value))
            val ruleset = enumOrDefault(json.optString("ruleset"), Ruleset.Japanese)
            val moves = decodeMoves(json.optJSONArray("moves") ?: JSONArray(), boardSize)
            val gameState = GameStateReplayer.replay(
                boardSize = boardSize,
                ruleset = ruleset,
                moves = moves,
            )
            SavedGameSnapshot(
                gameState = gameState,
                playerSetup = decodePlayerSetup(json.optJSONObject("playerSetup")),
                playLevel = decodePlayLevel(json.optJSONObject("playLevel")),
                topMovesEnabled = json.optBoolean("topMovesEnabled", false),
                savedAtMillis = json.optLong("savedAtMillis", 0L),
            )
        }.getOrNull()

    private fun encodeMoves(
        moves: List<Move>,
        boardSize: BoardSize,
    ): JSONArray =
        JSONArray().also { array ->
            moves.forEach { move ->
                array.put(
                    JSONObject()
                        .put("type", move.typeName())
                        .put("player", move.player.name)
                        .also { moveJson ->
                            if (move is Move.Play) {
                                moveJson.put("coordinate", move.coordinate.label(boardSize))
                            }
                        },
                )
            }
        }

    private fun decodeMoves(
        json: JSONArray,
        boardSize: BoardSize,
    ): List<Move> =
        List(json.length()) { index ->
            val moveJson = json.getJSONObject(index)
            val player = enumOrDefault(moveJson.getString("player"), StoneColor.Black)
            when (moveJson.getString("type")) {
                "play" -> Move.Play(
                    player = player,
                    coordinate = BoardCoordinate.fromLabel(moveJson.getString("coordinate"), boardSize),
                )
                "pass" -> Move.Pass(player)
                "resign" -> Move.Resign(player)
                else -> error("Unknown move type: ${moveJson.getString("type")}")
            }
        }

    private fun Move.typeName(): String =
        when (this) {
            is Move.Play -> "play"
            is Move.Pass -> "pass"
            is Move.Resign -> "resign"
        }
}
