package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameHistoryResult
import com.worksoc.goaicoach.application.gamehistory.GameHistoryStorePort
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.decodePlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.encodePlayerSetup
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import org.json.JSONArray
import org.json.JSONObject

/**
 * `SavedGameStorePort`/[GameSessionStore]와는 별개 Port다 — 저건 "진행 중인 대국 1개
 * 이어하기" 전용, 이건 끝난 대국들의 누적 목록이다(킥오프 플랜 6장).
 *
 * 다른 스토어와 달리 JSON blob 하나가 **배열**(entries 하나당 객체 하나)을 담는다. Phase 1은
 * 보관 개수 제한이 없다 — 계속 쌓이면 커지는 문제는 열린 질문으로 남아 있다(6장).
 */
internal class GameHistoryStore(context: Context) : GameHistoryStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun appendCompletedGame(entry: GameHistoryEntry) {
        val current = loadAll()
        prefs.edit()
            .putString(EntriesKey, GameHistoryCodec.encodeAll(current + entry))
            .apply()
    }

    override fun loadAll(): List<GameHistoryEntry> {
        val raw = prefs.getString(EntriesKey, null) ?: return emptyList()
        return GameHistoryCodec.decodeAll(raw)
    }

    private companion object {
        const val PrefsName = "go_ai_coach_game_history"
        const val EntriesKey = "entries"
    }
}

internal object GameHistoryCodec {
    private const val CurrentSchemaVersion = 1

    fun encodeAll(entries: List<GameHistoryEntry>): String =
        JSONObject()
            .put("schema", CurrentSchemaVersion)
            .put("entries", JSONArray(entries.map(::encodeEntry)))
            .toString()

    fun decodeAll(raw: String): List<GameHistoryEntry> =
        runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", -1) != CurrentSchemaVersion) return@runCatching emptyList()
            val array = json.optJSONArray("entries") ?: return@runCatching emptyList()
            (0 until array.length()).mapNotNull { i -> decodeEntry(array.optJSONObject(i)) }
        }.getOrDefault(emptyList())

    private fun encodeEntry(entry: GameHistoryEntry): JSONObject =
        JSONObject()
            .put("id", entry.id)
            .put("playedAtMillis", entry.playedAtMillis)
            .put("boardSize", entry.boardSize)
            .put("ruleset", entry.ruleset.name)
            .put("komi", entry.komi)
            .put("handicapCount", entry.handicapCount)
            .put("playerSetup", encodePlayerSetup(entry.playerSetup))
            .put("moveCount", entry.moveCount)
            .put("humanColor", entry.humanColor.name)
            .put("result", entry.result.name)
            .put("margin", entry.margin ?: JSONObject.NULL)

    private fun decodeEntry(json: JSONObject?): GameHistoryEntry? {
        if (json == null) return null
        return runCatching {
            GameHistoryEntry(
                id = json.getString("id"),
                playedAtMillis = json.optLong("playedAtMillis", 0L),
                boardSize = json.optInt("boardSize", 9),
                ruleset = enumOrDefault(json.optString("ruleset"), Ruleset.Japanese),
                komi = json.optDouble("komi", 0.0),
                handicapCount = json.optInt("handicapCount", 0),
                playerSetup = decodePlayerSetup(json.optJSONObject("playerSetup")),
                moveCount = json.optInt("moveCount", 0),
                humanColor = enumOrDefault(json.optString("humanColor"), StoneColor.Black),
                result = enumOrDefault(json.optString("result"), GameHistoryResult.Win),
                margin = if (json.isNull("margin")) null else json.optDouble("margin"),
            )
        }.getOrNull()
    }
}
