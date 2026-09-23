package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.preferences.DefaultAppFontScale
import com.worksoc.goaicoach.application.preferences.sanitizeAppFontScale
import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot
import com.worksoc.goaicoach.application.preferences.migrateSettingsSchema
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.decodePlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.encodePlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.Ruleset
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

    /**
     * ⚠️ **읽는 김에 설정 세대를 올린다**(백로그 #188) — 기본값이 바뀐 필드를 옛 사용자에게도
     * 미치게 하는 유일한 지점이다. `migrateSettingsSchema`는 올릴 것이 없으면 **같은 인스턴스를
     * 그대로** 돌려주므로, 대부분의 실행에서는 아무 일도 일어나지 않는다.
     *
     * ⚠️ **올렸으면 곧바로 저장한다** — 저장하지 않으면 앱을 켤 때마다 다시 돌고, 그 사이
     * 사용자가 고친 값이 매번 되돌려진다.
     */
    override fun load(): UserPreferencesSnapshot {
        val raw = prefs.getString(PreferencesKey, null) ?: return UserPreferencesSnapshot()
        val stored = UserPreferencesCodec.decode(raw) ?: return UserPreferencesSnapshot()
        val migrated = migrateSettingsSchema(stored)
        if (migrated !== stored) save(migrated)
        return migrated
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
            // **설정 세대**(백로그 #188). ⚠️ 키만 더한다(함정 55) — 스키마를 갈아엎으면
            // `wipeToFreshInstall`이 조용히 지나친다.
            .put("settingsSchemaGeneration", snapshot.settingsSchemaGeneration)
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
        putIfChanged("isPlayEffectEnabled", snapshot.isPlayEffectEnabled, defaults.isPlayEffectEnabled)
        putIfChanged("isBoardMaxSize", snapshot.isBoardMaxSize, defaults.isBoardMaxSize)

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
                komi = json.optDouble("komi", com.worksoc.goaicoach.shared.domain.DefaultKomi),
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
                isPlayEffectEnabled = json.optBoolean("isPlayEffectEnabled", defaults.isPlayEffectEnabled),
                isBoardMaxSize = json.optBoolean("isBoardMaxSize", defaults.isBoardMaxSize),
                // ⚠️ **없으면 0이다** — 이 키가 생기기 전에 저장한 사용자가 그 경우이고,
                // 바로 그 사람들에게 마이그레이션이 돌아야 한다. 기본값(현재 세대)을 쓰면
                // **아무에게도 안 돈다.**
                settingsSchemaGeneration = json.optInt("settingsSchemaGeneration", 0),
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
