package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.decodePlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.encodePlayerSetup
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import org.json.JSONObject

internal data class UserPreferencesSnapshot(
    val playerSetup: PlayerSetup = PlayerSetup(),
    val ruleset: Ruleset = Ruleset.Japanese,
    val topMovesEnabled: Boolean = false,
    val showCoordinates: Boolean = true,
    val showMoveNumbers: Boolean = false,
    val showLastMoveRing: Boolean = true,
    val showOwnershipOverlay: Boolean = true,
    val autoPlayDelayMillis: Long = AutoPlayDelaySetting.Default.millis,
    val searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
)

internal class UserPreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    fun save(snapshot: UserPreferencesSnapshot) {
        prefs.edit()
            .putString(PreferencesKey, UserPreferencesCodec.encode(snapshot))
            .apply()
    }

    fun load(): UserPreferencesSnapshot {
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

    private fun encodeSearchTimeSettings(settings: SearchTimeSettings): JSONObject =
        JSONObject()
            .put("b16Millis", settings.normalized().b16Millis)
            .put("b32Millis", settings.normalized().b32Millis)
            .put("b64Millis", settings.normalized().b64Millis)

    private fun decodeSearchTimeSettings(json: JSONObject?): SearchTimeSettings =
        SearchTimeSettings(
            b16Millis = json?.optLong("b16Millis", SearchTimeSettings().b16Millis) ?: SearchTimeSettings().b16Millis,
            b32Millis = json?.optLong("b32Millis", SearchTimeSettings().b32Millis) ?: SearchTimeSettings().b32Millis,
            b64Millis = json?.optLong("b64Millis", SearchTimeSettings().b64Millis) ?: SearchTimeSettings().b64Millis,
        ).normalized()
}
