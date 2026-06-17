package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.decodePlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.encodePlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Ruleset
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
    private const val SchemaVersion = 1

    fun encode(snapshot: UserPreferencesSnapshot): String =
        JSONObject()
            .put("schema", SchemaVersion)
            .put("boardSize", snapshot.boardSize.value)
            .put("playerSetup", encodePlayerSetup(snapshot.playerSetup))
            .put("ruleset", snapshot.ruleset.name)
            .put("topMovesEnabled", snapshot.topMovesEnabled)
            .put("showCoordinates", snapshot.showCoordinates)
            .put("showMoveNumbers", snapshot.showMoveNumbers)
            .put("showLastMoveRing", snapshot.showLastMoveRing)
            .put("showOwnershipOverlay", snapshot.showOwnershipOverlay)
            .put("autoPlayDelayMillis", snapshot.autoPlayDelayMillis)
            .put("searchTimeSettings", encodeSearchTimeSettings(snapshot.searchTimeSettings))
            .toString()

    fun decode(raw: String): UserPreferencesSnapshot? =
        runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", SchemaVersion) != SchemaVersion) {
                return@runCatching null
            }
            UserPreferencesSnapshot(
                boardSize = BoardSize(json.optInt("boardSize", BoardSize.Nine.value)),
                playerSetup = decodePlayerSetup(json.optJSONObject("playerSetup")),
                ruleset = enumOrDefault(json.optString("ruleset"), Ruleset.Japanese),
                topMovesEnabled = json.optBoolean("topMovesEnabled", false),
                showCoordinates = json.optBoolean("showCoordinates", true),
                showMoveNumbers = json.optBoolean("showMoveNumbers", false),
                showLastMoveRing = json.optBoolean("showLastMoveRing", true),
                showOwnershipOverlay = json.optBoolean("showOwnershipOverlay", true),
                autoPlayDelayMillis = AutoPlayDelaySetting
                    .fromMillis(json.optLong("autoPlayDelayMillis", AutoPlayDelaySetting.Default.millis))
                    .millis,
                searchTimeSettings = decodeSearchTimeSettings(json.optJSONObject("searchTimeSettings")),
            )
        }.getOrNull()

    private fun encodeSearchTimeSettings(settings: SearchTimeSettings): JSONObject {
        val normalized = settings.normalized()
        return JSONObject()
            .put("timeCapEnabled", normalized.timeCapEnabled)
            .put("b16Millis", normalized.b16Millis)
            .put("b32Millis", normalized.b32Millis)
            .put("b64Millis", normalized.b64Millis)
    }

    private fun decodeSearchTimeSettings(json: JSONObject?): SearchTimeSettings {
        val defaults = SearchTimeSettings()
        return SearchTimeSettings(
            b16Millis = json?.optLong("b16Millis", defaults.b16Millis) ?: defaults.b16Millis,
            b32Millis = json?.optLong("b32Millis", defaults.b32Millis) ?: defaults.b32Millis,
            b64Millis = json?.optLong("b64Millis", defaults.b64Millis) ?: defaults.b64Millis,
            timeCapEnabled = json?.optBoolean("timeCapEnabled", defaults.timeCapEnabled)
                ?: defaults.timeCapEnabled,
        ).normalized()
    }
}
