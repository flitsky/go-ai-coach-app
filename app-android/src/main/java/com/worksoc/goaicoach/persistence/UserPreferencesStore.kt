package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.preferences.DefaultAppFontScale
import com.worksoc.goaicoach.application.preferences.GameSetupUxMode
import com.worksoc.goaicoach.application.preferences.sanitizeAppFontScale
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
            .put("komi", snapshot.komi)
            .put("topMovesEnabled", snapshot.topMovesEnabled)
            .put("showCoordinates", snapshot.showCoordinates)
            .put("showMoveNumbers", snapshot.showMoveNumbers)
            .put("showLastMoveRing", snapshot.showLastMoveRing)
            .put("showOwnershipOverlay", snapshot.showOwnershipOverlay)
            .put("autoPlayDelayMillis", snapshot.autoPlayDelayMillis)
            .put("searchTimeSettings", encodeSearchTimeSettings(snapshot.searchTimeSettings))
            .put("isDirectPlayEnabled", snapshot.isDirectPlayEnabled)
            .put("showMoveReview", snapshot.showMoveReview)
            .put("hasSeenOnboarding", snapshot.hasSeenOnboarding)
            .put("gameSetupUxMode", snapshot.gameSetupUxMode.name)
            .put("isPlayHapticEnabled", snapshot.isPlayHapticEnabled)
            .put("isBoardMaxSize", snapshot.isBoardMaxSize)
            .put("isPlayMagnifierEnabled", snapshot.isPlayMagnifierEnabled)
            // ⚠️ **`Float`를 `Double`로 넣지 않는다** — `1.3f.toDouble()`이
            // `1.2999999523162842`로 저장돼(2026-09-04 실기에서 확인) 사람이 읽을 수 없고,
            // 개발자 도구가 이 파일을 쓰는 자리라 손 편집도 전제된다. 왕복은 문자열이 정확하다.
            .put("appFontScale", snapshot.appFontScale.toString())
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
                boardSize = BoardSize(json.optInt("boardSize", BoardSize.Thirteen.value)),
                playerSetup = decodePlayerSetup(json.optJSONObject("playerSetup")),
                ruleset = enumOrDefault(json.optString("ruleset"), Ruleset.Japanese),
                handicapCount = json.optInt("handicapCount", 0),
                komi = json.optDouble("komi", com.worksoc.goaicoach.shared.DefaultKomi),
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
                showMoveReview = json.optBoolean("showMoveReview", false),
                hasSeenOnboarding = json.optBoolean("hasSeenOnboarding", false),
                gameSetupUxMode = enumOrDefault(json.optString("gameSetupUxMode"), GameSetupUxMode.Compact),
                isPlayHapticEnabled = json.optBoolean("isPlayHapticEnabled", true),
                isBoardMaxSize = json.optBoolean("isBoardMaxSize", true),
                isPlayMagnifierEnabled = json.optBoolean("isPlayMagnifierEnabled", true),
                // ⚠️ 읽는 쪽에서 좁힌다 — 0이나 음수가 흘러들면 글자 높이가 0이 돼 화면이
                // 통째로 사라진다. 개발자 도구가 이 파일을 쓰므로 손 편집도 가능한 자리다.
                // 숫자로 저장된 예전 값(문자열로 바꾸기 전)도 `optString`이 그대로 읽어 준다.
                appFontScale = sanitizeAppFontScale(
                    json.optString("appFontScale").toFloatOrNull() ?: DefaultAppFontScale,
                ),
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
