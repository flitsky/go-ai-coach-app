package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureState
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureStorePort
import com.worksoc.goaicoach.shared.policy.KgsRank
import org.json.JSONObject

/**
 * 기력 측정 대국의 상태(내 기력 · 최고 기력 · 연승·연패)를 남긴다(백로그 #219).
 *
 * ⚠️ `UserPreferencesSnapshot`에 얹지 않고 제 저장소를 갖는다(함정 2 — 자동저장이 스냅샷을 처음부터 다시 조립한다).
 * 이름이 앱 접두사(`go_ai_coach_`)로 시작해 「앱 최초 실행 상태로 되돌리기」가 함께 지운다(`wipeToFreshInstall`, 함정 55).
 * 권한 저장소가 아니라 `ReleaseResetCoordinator`의 목록(함정 6)에는 넣지 않는다.
 */
internal class RankMeasureStore(context: Context) : RankMeasureStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun load(): RankMeasureState =
        prefs.getString(StateKey, null)?.let(RankMeasureCodec::decode) ?: RankMeasureState()

    override fun save(state: RankMeasureState) {
        prefs.edit().putString(StateKey, RankMeasureCodec.encode(state)).apply()
    }

    private companion object {
        const val PrefsName = "go_ai_coach_rank_measure"
        const val StateKey = "rank_measure_state"
    }
}

/**
 * ⚠️ **스키마 번호를 올리지 않는다 — 키만 더한다**(함정 69). 모든 필드를 기본값과 함께 읽으므로, 새 필드가 없는 옛 저장분도
 * 깨진 저장분도 기본 상태의 그 필드로 읽힌다. 범위를 벗어난 급수는 끝 칸으로 당긴다.
 */
internal object RankMeasureCodec {
    fun encode(state: RankMeasureState): String =
        JSONObject()
            .put("schema", 1)
            .put("rankStep", state.rank.step)
            .put("hasStarted", state.hasStarted)
            .put("peakRankStep", state.peakRank?.step ?: JSONObject.NULL)
            .put("consecutiveWins", state.consecutiveWins)
            .put("rally", state.isRally)
            .put("consecutiveLosses", state.consecutiveLosses)
            .put("lastCountedGameId", state.lastCountedGameId ?: JSONObject.NULL)
            .toString()

    fun decode(raw: String): RankMeasureState? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val defaults = RankMeasureState()
        return RankMeasureState(
            rank = KgsRank.ofStepCoerced(json.optInt("rankStep", defaults.rank.step)),
            hasStarted = json.optBoolean("hasStarted", false),
            // 깨진 값은 「측정 기록 없음」으로 읽는다 — 0으로 읽어 20급을 최고 기력으로 만들어 내지 않는다.
            peakRank = json.optInt("peakRankStep", 0).takeIf { it >= KgsRank.Weakest.step }?.let(KgsRank::ofStepCoerced),
            consecutiveWins = json.optInt("consecutiveWins", 0).coerceAtLeast(0),
            isRally = json.optBoolean("rally", false),
            consecutiveLosses = json.optInt("consecutiveLosses", 0).coerceAtLeast(0),
            lastCountedGameId = json.optString("lastCountedGameId", "").takeIf { it.isNotBlank() && !json.isNull("lastCountedGameId") },
        )
    }
}
