package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.savedgame.SavedGameStorePort
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
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
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import org.json.JSONArray
import org.json.JSONObject

internal class GameSessionStore(context: Context) : SavedGameStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun save(snapshot: SavedGameSnapshot) {
        // A finished game is never "resumable" (see SavedGameSnapshot.isResumable), but a
        // snapshot carrying a final-score judgement still needs to survive process death so
        // the result popup can be restored on the next cold start.
        if (!snapshot.isResumable && snapshot.finalScoreJudgement == null) {
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
                if (snapshot == null || (!snapshot.isResumable && snapshot.finalScoreJudgement == null)) {
                    clear()
                }
            }
            ?.takeIf { snapshot -> snapshot.isResumable || snapshot.finalScoreJudgement != null }
    }

    override fun clear() {
        prefs.edit().remove(SessionKey).apply()
    }

    override fun readRawJson(): String? {
        return prefs.getString(SessionKey, null)
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
            .put("handicapCount", snapshot.gameState.handicapCount)
            .put("moves", encodeMoves(snapshot.gameState.moves, snapshot.gameState.boardSize))
            .put("playerSetup", encodePlayerSetup(snapshot.playerSetup))
            .put("playLevel", encodePlayLevel(snapshot.playLevel))
            .put("topMovesEnabled", snapshot.topMovesEnabled)
            .put("scoreSnapshots", encodeScoreSnapshots(snapshot.scoreSnapshots))
            .put("finalScoreJudgement", snapshot.finalScoreJudgement?.let(::encodeFinalScoreJudgement) ?: JSONObject.NULL)
            .toString()

    fun decode(raw: String): SavedGameSnapshot? =
        runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", SchemaVersion) != SchemaVersion) {
                return@runCatching null
            }
            val boardSize = BoardSize(json.optInt("boardSize", BoardSize.Nine.value))
            val ruleset = enumOrDefault(json.optString("ruleset"), Ruleset.Japanese)
            val handicapCount = json.optInt("handicapCount", 0)
            val moves = decodeMoves(json.optJSONArray("moves") ?: JSONArray(), boardSize)
            val gameState = GameStateReplayer.replay(
                boardSize = boardSize,
                ruleset = ruleset,
                moves = moves,
                handicapCount = handicapCount,
            )
            val scoreSnapshots = decodeScoreSnapshots(json.optJSONArray("scoreSnapshots") ?: JSONArray())
            SavedGameSnapshot(
                gameState = gameState,
                playerSetup = decodePlayerSetup(json.optJSONObject("playerSetup")),
                playLevel = decodePlayLevel(json.optJSONObject("playLevel")),
                topMovesEnabled = json.optBoolean("topMovesEnabled", false),
                savedAtMillis = json.optLong("savedAtMillis", 0L),
                scoreSnapshots = scoreSnapshots,
                finalScoreJudgement = json.optJSONObject("finalScoreJudgement")?.let(::decodeFinalScoreJudgement),
            )
        }.getOrNull()

    private fun encodeFinalScoreJudgement(judgement: FinalScoreJudgement): JSONObject =
        JSONObject()
            .put("winner", judgement.winner?.name ?: JSONObject.NULL)
            .put("margin", judgement.margin ?: JSONObject.NULL)
            .put("ruleset", judgement.ruleset.name)
            .put("isEstimatedDisplay", judgement.isEstimatedDisplay)
            .put("removedBlack", judgement.removedBlack)
            .put("removedWhite", judgement.removedWhite)
            .put("blackArea", judgement.blackArea ?: JSONObject.NULL)
            .put("whiteAreaWithKomi", judgement.whiteAreaWithKomi ?: JSONObject.NULL)
            .put("capturedByBlack", judgement.capturedByBlack)
            .put("capturedByWhite", judgement.capturedByWhite)
            .put("komi", judgement.komi ?: JSONObject.NULL)

    private fun decodeFinalScoreJudgement(json: JSONObject): FinalScoreJudgement =
        FinalScoreJudgement(
            winner = if (json.isNull("winner")) null else enumOrDefault(json.optString("winner"), StoneColor.Black),
            margin = if (json.isNull("margin")) null else json.optDouble("margin"),
            ruleset = enumOrDefault(json.optString("ruleset"), Ruleset.Japanese),
            isEstimatedDisplay = json.optBoolean("isEstimatedDisplay", false),
            removedBlack = json.optInt("removedBlack", 0),
            removedWhite = json.optInt("removedWhite", 0),
            blackArea = if (json.isNull("blackArea")) null else json.optDouble("blackArea"),
            whiteAreaWithKomi = if (json.isNull("whiteAreaWithKomi")) null else json.optDouble("whiteAreaWithKomi"),
            capturedByBlack = json.optInt("capturedByBlack", 0),
            capturedByWhite = json.optInt("capturedByWhite", 0),
            komi = if (json.isNull("komi")) null else json.optDouble("komi"),
        )

    private fun encodeScoreSnapshots(snapshots: List<ScoreSnapshot>): JSONArray =
        JSONArray().also { array ->
            snapshots.forEach { snapshot ->
                array.put(
                    JSONObject()
                        .put("moveNumber", snapshot.moveNumber)
                        .put("whiteScoreLead", snapshot.whiteScoreLead ?: JSONObject.NULL)
                        .put("whiteWinRate", snapshot.whiteWinRate ?: JSONObject.NULL)
                        .put("source", snapshot.source.name)
                )
            }
        }

    private fun decodeScoreSnapshots(json: JSONArray): List<ScoreSnapshot> =
        List(json.length()) { index ->
            val item = json.getJSONObject(index)
            val moveNumber = item.getInt("moveNumber")
            val whiteScoreLead = if (item.isNull("whiteScoreLead")) null else item.getDouble("whiteScoreLead")
            val whiteWinRate = if (item.isNull("whiteWinRate")) null else item.getDouble("whiteWinRate")
            val source = enumOrDefault(item.optString("source"), ScoreSnapshotSource.EngineEstimate)
            ScoreSnapshot(
                moveNumber = moveNumber,
                whiteScoreLead = whiteScoreLead,
                whiteWinRate = whiteWinRate,
                source = source
            )
        }

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
