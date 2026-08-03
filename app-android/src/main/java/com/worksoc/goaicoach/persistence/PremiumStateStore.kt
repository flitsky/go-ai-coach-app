package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.premium.PremiumSource
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.PremiumStateStorePort
import org.json.JSONObject

internal class PremiumStateStore(context: Context) : PremiumStateStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun save(state: PremiumState) {
        prefs.edit()
            .putString(StateKey, PremiumStateCodec.encode(state))
            .apply()
    }

    override fun load(): PremiumState {
        val raw = prefs.getString(StateKey, null) ?: return PremiumState()
        return PremiumStateCodec.decode(raw) ?: PremiumState()
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
            .put("adGrantMatchGeneration", state.adGrantMatchGeneration ?: JSONObject.NULL)
            .put("adGrantStartedAtMillis", state.adGrantStartedAtMillis ?: JSONObject.NULL)
            .toString()

    fun decode(raw: String): PremiumState? =
        runCatching {
            val json = JSONObject(raw)
            PremiumState(
                source = enumOrDefault(json.optString("source"), PremiumSource.None),
                adGrantMatchGeneration = json.optLongOrNull("adGrantMatchGeneration"),
                adGrantStartedAtMillis = json.optLongOrNull("adGrantStartedAtMillis"),
            )
        }.getOrNull()

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)
}
