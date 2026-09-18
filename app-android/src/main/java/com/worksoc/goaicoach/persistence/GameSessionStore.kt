package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.savedgame.SavedGameStorePort
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.decodePlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.decodePlayLevel
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.encodePlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.encodePlayLevel
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
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
            .put("moves", ReplayJsonCodec.encodeMoves(snapshot.gameState.moves, snapshot.gameState.boardSize))
            .put("playerSetup", encodePlayerSetup(snapshot.playerSetup))
            .put("playLevel", encodePlayLevel(snapshot.playLevel))
            .put("topMovesEnabled", snapshot.topMovesEnabled)
            .put("scoreSnapshots", ReplayJsonCodec.encodeScoreSnapshots(snapshot.scoreSnapshots))
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
            val moves = ReplayJsonCodec.decodeMoves(json.optJSONArray("moves") ?: JSONArray(), boardSize)
            val gameState = GameStateReplayer.replay(
                boardSize = boardSize,
                ruleset = ruleset,
                moves = moves,
                handicapCount = handicapCount,
            )
            val scoreSnapshots = ReplayJsonCodec.decodeScoreSnapshots(json.optJSONArray("scoreSnapshots") ?: JSONArray())
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
            .put("handicapCount", judgement.handicapCount)

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
            handicapCount = json.optInt("handicapCount", 0),
        )

}
