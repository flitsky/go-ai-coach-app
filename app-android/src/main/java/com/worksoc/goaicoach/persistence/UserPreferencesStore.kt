package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.decodePlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.encodePlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeLimit
import com.worksoc.goaicoach.shared.SearchTimeSettings
import org.json.JSONObject

internal class UserPreferencesStore(context: Context) : UserPreferencesStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun save(snapshot: UserPreferencesSnapshot) {
        prefs.edit()
            .putString(PreferencesKey, UserPreferencesCodec.encode(snapshot))
            .apply()
    }

    override fun load(): UserPreferencesSnapshot {
        val raw = prefs.getString(PreferencesKey, null) ?: return UserPreferencesSnapshot()
        return UserPreferencesCodec.decode(raw) ?: UserPreferencesSnapshot()
    }

    private companion object {
        const val PrefsName = "go_ai_coach_user_preferences"
        const val PreferencesKey = "user_preferences"
    }
}

internal object UserPreferencesCodec {
    private const val CurrentSchemaVersion = 2
    private const val LegacySchemaVersion = 1

    fun encode(snapshot: UserPreferencesSnapshot): String =
        JSONObject()
            .put("schema", CurrentSchemaVersion)
            .put("boardSize", snapshot.boardSize.value)
            .put("playerSetup", encodePlayerSetup(snapshot.playerSetup))
            .put("ruleset", snapshot.ruleset.name)
            .put("handicapCount", snapshot.handicapCount)
            .put("topMovesEnabled", snapshot.topMovesEnabled)
            .put("showCoordinates", snapshot.showCoordinates)
            .put("showMoveNumbers", snapshot.showMoveNumbers)
            .put("showLastMoveRing", snapshot.showLastMoveRing)
            .put("showOwnershipOverlay", snapshot.showOwnershipOverlay)
            .put("autoPlayDelayMillis", snapshot.autoPlayDelayMillis)
            .put("searchTimeSettings", encodeSearchTimeSettings(snapshot.searchTimeSettings))
            .put("isDirectPlayEnabled", snapshot.isDirectPlayEnabled)
            .toString()

    fun decode(raw: String): UserPreferencesSnapshot? =
        runCatching {
            val json = JSONObject(raw)
            val schema = if (json.has("schema")) {
                json.optInt("schema", -1)
            } else {
                LegacySchemaVersion
            }
            val searchTimeSettings = when (schema) {
                CurrentSchemaVersion -> decodeSearchTimeSettings(json.optJSONObject("searchTimeSettings"))
                LegacySchemaVersion -> decodeLegacySearchTimeSettings(json.optJSONObject("searchTimeSettings"))
                else -> return@runCatching null
            }
            UserPreferencesSnapshot(
                boardSize = BoardSize(json.optInt("boardSize", BoardSize.Nine.value)),
                playerSetup = decodePlayerSetup(json.optJSONObject("playerSetup")),
                ruleset = enumOrDefault(json.optString("ruleset"), Ruleset.Japanese),
                handicapCount = json.optInt("handicapCount", 0),
                topMovesEnabled = json.optBoolean("topMovesEnabled", false),
                showCoordinates = json.optBoolean("showCoordinates", false),
                showMoveNumbers = json.optBoolean("showMoveNumbers", false),
                showLastMoveRing = json.optBoolean("showLastMoveRing", true),
                showOwnershipOverlay = json.optBoolean("showOwnershipOverlay", true),
                autoPlayDelayMillis = AutoPlayDelaySetting
                    .fromMillis(json.optLong("autoPlayDelayMillis", AutoPlayDelaySetting.Default.millis))
                    .millis,
                searchTimeSettings = searchTimeSettings,
                isDirectPlayEnabled = json.optBoolean("isDirectPlayEnabled", true),
            )
        }.getOrNull()

    private fun encodeSearchTimeSettings(settings: SearchTimeSettings): JSONObject {
        val normalized = settings.normalized()
        return JSONObject()
            .put("limit", normalized.limit.name)
    }

    private fun decodeSearchTimeSettings(json: JSONObject?): SearchTimeSettings {
        val storedLimit = json
            ?.takeIf { it.has("limit") }
            ?.optString("limit")
        val limit = SearchTimeLimit.fromStoredName(storedLimit)
        return SearchTimeSettings(limit).normalized()
    }

    private fun decodeLegacySearchTimeSettings(json: JSONObject?): SearchTimeSettings {
        if (json == null) {
            return SearchTimeSettings()
        }
        if (!json.optBoolean("timeCapEnabled", true)) {
            return SearchTimeSettings(SearchTimeLimit.Off)
        }

        val maximumLegacyMillis = listOf(
            legacyMillis(json, "b16Millis", 1_000L),
            legacyMillis(json, "b32Millis", 2_000L),
            legacyMillis(json, "b64Millis", 3_000L),
        ).maxOrNull() ?: 3_000L
        return SearchTimeSettings(SearchTimeLimit.ceilingFor(maximumLegacyMillis))
    }

    private fun legacyMillis(
        json: JSONObject,
        key: String,
        default: Long,
    ): Long =
        json.optLong(key, default)
            .takeIf { millis -> millis > 0L }
            ?: default
}
