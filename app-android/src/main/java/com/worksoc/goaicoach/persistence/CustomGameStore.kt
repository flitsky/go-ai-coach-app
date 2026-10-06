package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.customgame.CustomGameState
import com.worksoc.goaicoach.application.customgame.CustomGameStorePort
import com.worksoc.goaicoach.shared.policy.KgsRank
import org.json.JSONObject

/**
 * 커스텀 대국의 상태(다음 판의 상대 급수 · 자동 조정 켬/끔 · 승급 랠리)를 남긴다(백로그 #217).
 *
 * ⚠️ `UserPreferencesSnapshot`에 얹지 않고 제 저장소를 갖는다(함정 2 — 자동저장이 스냅샷을 처음부터 다시 조립한다).
 * 이름이 앱 접두사(`go_ai_coach_`)로 시작해 「앱 최초 실행 상태로 되돌리기」가 함께 지운다(`wipeToFreshInstall`, 함정 55).
 * 권한 저장소가 아니라 `ReleaseResetCoordinator`의 목록(함정 6)에는 넣지 않는다.
 */
internal class CustomGameStore(context: Context) : CustomGameStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun load(): CustomGameState =
        prefs.getString(StateKey, null)?.let(CustomGameCodec::decode) ?: CustomGameState()

    override fun save(state: CustomGameState) {
        prefs.edit().putString(StateKey, CustomGameCodec.encode(state)).apply()
    }

    private companion object {
        const val PrefsName = "go_ai_coach_custom_game"
        const val StateKey = "custom_game_state"
    }
}

/**
 * ⚠️ **스키마 번호를 올리지 않는다 — 키만 더한다**(함정 69). 모든 필드를 기본값과 함께 읽으므로, 새 필드가 없는 옛 저장분도
 * 깨진 저장분도 기본 상태의 그 필드로 읽힌다. 범위를 벗어난 급수는 끝 칸으로 당긴다.
 */
internal object CustomGameCodec {
    fun encode(state: CustomGameState): String =
        JSONObject()
            .put("schema", 1)
            .put("rankStep", state.rank.step)
            .put("autoAdjust", state.autoAdjustEnabled)
            .put("consecutiveWins", state.consecutiveWins)
            .put("rally", state.isRally)
            .put("lastCountedGameId", state.lastCountedGameId ?: JSONObject.NULL)
            .toString()

    fun decode(raw: String): CustomGameState? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val defaults = CustomGameState()
        return CustomGameState(
            rank = KgsRank.ofStepCoerced(json.optInt("rankStep", defaults.rank.step)),
            autoAdjustEnabled = json.optBoolean("autoAdjust", defaults.autoAdjustEnabled),
            consecutiveWins = json.optInt("consecutiveWins", 0).coerceAtLeast(0),
            isRally = json.optBoolean("rally", false),
            lastCountedGameId = json.optString("lastCountedGameId", "").takeIf { it.isNotBlank() && !json.isNull("lastCountedGameId") },
        )
    }
}
