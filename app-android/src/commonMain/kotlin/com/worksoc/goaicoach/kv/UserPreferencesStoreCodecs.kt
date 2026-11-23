package com.worksoc.goaicoach.kv

// 안드로이드 `persistence/UserPreferencesStore.kt`의 공용 사본(백로그 #294 — P2) — 저장소 클래스를 빼고 코덱만, import만 바꿨다(org.json → kv.json). 고칠 때는 둘을 함께 고친다(`KvStoreParityTest`).

import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.application.preferences.migrateSettingsSchema
import com.worksoc.goaicoach.kv.PlayerSetupJsonCodec.decodePlayerSetup
import com.worksoc.goaicoach.kv.PlayerSetupJsonCodec.encodePlayerSetup
import com.worksoc.goaicoach.kv.json.JSONObject
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.policy.SearchTimeLimit
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings

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
            // 글꼴 배율(`"appFontScale"`)은 더 적지 않는다(QA-05, 2026-11-23) — 옛 파일의 그 키는 다음 저장에서 사라진다.

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
        putIfChanged("isLargeHeldStoneEnabled", snapshot.isLargeHeldStoneEnabled, defaults.isLargeHeldStoneEnabled)

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
                isLargeHeldStoneEnabled = json.optBoolean("isLargeHeldStoneEnabled", defaults.isLargeHeldStoneEnabled),
                // ⚠️ **없으면 0이다** — 이 키가 생기기 전에 저장한 사용자가 그 경우이고,
                // 바로 그 사람들에게 마이그레이션이 돌아야 한다. 기본값(현재 세대)을 쓰면
                // **아무에게도 안 돈다.**
                settingsSchemaGeneration = json.optInt("settingsSchemaGeneration", 0),
                // ⚠️ 옛 `"appFontScale"`(1.3 포함)은 **읽지 않는다**(QA-05, 2026-11-23) — 앱은 늘 1.0이라 옮길 것이 없다(#73 전례).
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
