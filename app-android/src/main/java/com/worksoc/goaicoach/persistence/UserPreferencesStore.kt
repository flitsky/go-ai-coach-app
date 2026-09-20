package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.preferences.DefaultAppFontScale
import com.worksoc.goaicoach.application.preferences.MagnifierSettings
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

    /**
     * ⚠️ **대국 설정 토글류(아래 `putIfChanged` 호출들)는 앱 기본값과 같으면 아예 안 적는다**
     * (2026-09-20 사용자 지시). [UserPreferencesSnapshot]의 생성자 기본값이 곧 "앱이 인메모리로
     * 들고 있는 기본값"이고, 다음 앱 버전이 그 기본값을 바꾸면 **이 토글을 한 번도 안 건드린
     * 사용자**는 자동으로 새 기본값을 따라간다 — 로컬 파일에 옛 기본값이 굳어 있지 않기 때문이다.
     * 사용자가 실제로 건드려 앱 기본값과 달라진 토글만 파일에 남고, 그 값은 미래의 기본값 변경과
     * 무관하게 그대로 지켜진다.
     *
     * 판 크기·룰셋·핸디캡·덤·좌석 구성 등 "지난 대국 설정값"은 이 취급에서 뺐다 — 그건
     * 켜짐/꺼짐 선호가 아니라 "마지막으로 둔 판 그대로"를 이어가는 값이라 성격이 다르다.
     */
    fun encode(snapshot: UserPreferencesSnapshot): String {
        val json = JSONObject()
            .put("schema", CurrentSchemaVersion)
            .put("boardSize", snapshot.boardSize.value)
            .put("playerSetup", encodePlayerSetup(snapshot.playerSetup))
            .put("ruleset", snapshot.ruleset.name)
            .put("handicapCount", snapshot.handicapCount)
            .put("komi", snapshot.komi)
            .put("autoPlayDelayMillis", snapshot.autoPlayDelayMillis)
            .put("searchTimeSettings", encodeSearchTimeSettings(snapshot.searchTimeSettings))
            .put("hasSeenOnboarding", snapshot.hasSeenOnboarding)
            // ⚠️ 배율류는 **문자열로** 저장한다 — `Float`를 `Double`로 넣으면
            // `1.2000000476837158`이 된다(#81에서 글꼴 배율로 확인했다).
            .put("magnifierSizeScale", snapshot.magnifierSizeScale.toString())
            .put("magnifierZoom", snapshot.magnifierZoom.toString())
            // ⚠️ **`Float`를 `Double`로 넣지 않는다** — `1.3f.toDouble()`이
            // `1.2999999523162842`로 저장돼(2026-09-04 실기에서 확인) 사람이 읽을 수 없고,
            // 개발자 도구가 이 파일을 쓰는 자리라 손 편집도 전제된다. 왕복은 문자열이 정확하다.
            .put("appFontScale", snapshot.appFontScale.toString())

        val defaults = UserPreferencesSnapshot()
        fun putIfChanged(key: String, value: Boolean, default: Boolean) {
            if (value != default) json.put(key, value)
        }
        putIfChanged("topMovesEnabled", snapshot.topMovesEnabled, defaults.topMovesEnabled)
        putIfChanged("showCoordinates", snapshot.showCoordinates, defaults.showCoordinates)
        putIfChanged("showMoveNumbers", snapshot.showMoveNumbers, defaults.showMoveNumbers)
        putIfChanged("showLastMoveRing", snapshot.showLastMoveRing, defaults.showLastMoveRing)
        putIfChanged("showOwnershipOverlay", snapshot.showOwnershipOverlay, defaults.showOwnershipOverlay)
        putIfChanged("isDirectPlayEnabled", snapshot.isDirectPlayEnabled, defaults.isDirectPlayEnabled)
        putIfChanged("showMoveReview", snapshot.showMoveReview, defaults.showMoveReview)
        putIfChanged("isPlayHapticEnabled", snapshot.isPlayHapticEnabled, defaults.isPlayHapticEnabled)
        putIfChanged("isDelayedPlayEnabled", snapshot.isDelayedPlayEnabled, defaults.isDelayedPlayEnabled)
        putIfChanged("isPlayEffectEnabled", snapshot.isPlayEffectEnabled, defaults.isPlayEffectEnabled)
        putIfChanged("isBoardMaxSize", snapshot.isBoardMaxSize, defaults.isBoardMaxSize)
        putIfChanged("isPlayMagnifierEnabled", snapshot.isPlayMagnifierEnabled, defaults.isPlayMagnifierEnabled)

        return json.toString()
    }

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
            // ⚠️ **토글류의 폴백은 하드코딩 상수가 아니라 `defaults`(현재 코드의
            // `UserPreferencesSnapshot()`)를 그대로 읽는다** — 키가 없다는 것은 "사용자가 이
            // 설정을 건드린 적 없다"는 뜻이므로, 지금 앱 버전의 기본값을 그대로 물려받아야
            // 다음 버전이 기본값을 바꿨을 때 자동으로 따라간다(2026-09-20 사용자 지시,
            // `encode`의 `putIfChanged` 주석과 같은 축).
            val defaults = UserPreferencesSnapshot()
            UserPreferencesSnapshot(
                boardSize = BoardSize(json.optInt("boardSize", BoardSize.Thirteen.value)),
                playerSetup = decodePlayerSetup(json.optJSONObject("playerSetup")),
                ruleset = enumOrDefault(json.optString("ruleset"), Ruleset.Japanese),
                handicapCount = json.optInt("handicapCount", 0),
                komi = json.optDouble("komi", com.worksoc.goaicoach.shared.DefaultKomi),
                topMovesEnabled = json.optBoolean("topMovesEnabled", defaults.topMovesEnabled),
                showCoordinates = json.optBoolean("showCoordinates", defaults.showCoordinates),
                showMoveNumbers = json.optBoolean("showMoveNumbers", defaults.showMoveNumbers),
                showLastMoveRing = json.optBoolean("showLastMoveRing", defaults.showLastMoveRing),
                showOwnershipOverlay = json.optBoolean("showOwnershipOverlay", defaults.showOwnershipOverlay),
                autoPlayDelayMillis = AutoPlayDelaySetting
                    .fromMillis(json.optLong("autoPlayDelayMillis", AutoPlayDelaySetting.Default.millis))
                    .millis,
                searchTimeSettings = searchTimeSettings,
                isDirectPlayEnabled = json.optBoolean("isDirectPlayEnabled", defaults.isDirectPlayEnabled),
                showMoveReview = json.optBoolean("showMoveReview", defaults.showMoveReview),
                hasSeenOnboarding = json.optBoolean("hasSeenOnboarding", false),
                isPlayHapticEnabled = json.optBoolean("isPlayHapticEnabled", defaults.isPlayHapticEnabled),
                isDelayedPlayEnabled = json.optBoolean("isDelayedPlayEnabled", defaults.isDelayedPlayEnabled),
                isPlayEffectEnabled = json.optBoolean("isPlayEffectEnabled", defaults.isPlayEffectEnabled),
                isBoardMaxSize = json.optBoolean("isBoardMaxSize", defaults.isBoardMaxSize),
                isPlayMagnifierEnabled = json.optBoolean("isPlayMagnifierEnabled", defaults.isPlayMagnifierEnabled),
                magnifierSizeScale = MagnifierSettings.sanitizeSizeScale(
                    json.optString("magnifierSizeScale").toFloatOrNull() ?: MagnifierSettings.defaultSizeScale,
                ),
                magnifierZoom = MagnifierSettings.sanitizeZoom(
                    json.optString("magnifierZoom").toFloatOrNull() ?: MagnifierSettings.defaultZoom,
                ),
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
