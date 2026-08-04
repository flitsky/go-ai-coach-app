package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.premium.PremiumSource
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.PremiumStateStorePort
import org.json.JSONObject

/**
 * 4계층(External Integration) Extended API 본체 — [PremiumStateStorePort]를 실제
 * SharedPreferences에 연결하는 어댑터. `docs/ARCHITECTURE.md` 2계층 설명대로, 저장된 값을
 * 그대로 상위에 흘려보내지 않고 [PremiumState.isClockPlausibleAt]로 신뢰도를 판정한 뒤
 * 신뢰할 수 없는 값(파싱 실패 포함)은 기본 상태로 폴백한다.
 */
internal class PremiumStateStore(context: Context) : PremiumStateStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun save(state: PremiumState) {
        prefs.edit()
            .putString(StateKey, PremiumStateCodec.encode(state))
            .apply()
    }

    override fun load(): PremiumState {
        val raw = prefs.getString(StateKey, null) ?: return PremiumState()
        val decoded = PremiumStateCodec.decode(raw) ?: return PremiumState()
        return decoded.takeIf { state -> state.isClockPlausibleAt(System.currentTimeMillis()) } ?: PremiumState()
    }

    private companion object {
        const val PrefsName = "go_ai_coach_premium_state"
        const val StateKey = "premium_state"
    }
}

internal object PremiumStateCodec {
    fun encode(state: PremiumState): String =
        JSONObject()
            .put("source", state.source.name)
            .put("adGrantStartedAtMillis", state.adGrantStartedAtMillis ?: JSONObject.NULL)
            .toString()

    fun decode(raw: String): PremiumState? =
        runCatching {
            val json = JSONObject(raw)
            PremiumState(
                source = enumOrDefault(json.optString("source"), PremiumSource.None),
                adGrantStartedAtMillis = json.optLongOrNull("adGrantStartedAtMillis"),
            )
        }.getOrNull()

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)
}
