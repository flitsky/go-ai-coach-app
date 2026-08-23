package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.botcharacter.BotCharacterId
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import org.json.JSONArray
import org.json.JSONObject

internal class BotCollectionStore(context: Context) : BotCollectionStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun save(state: BotCollectionState) {
        prefs.edit()
            .putString(StateKey, BotCollectionCodec.encode(state))
            .apply()
    }

    override fun load(): BotCollectionState {
        val raw = prefs.getString(StateKey, null) ?: return BotCollectionState()
        return BotCollectionCodec.decode(raw) ?: BotCollectionState()
    }

    private companion object {
        // 출석/히스토리와 한 blob에 섞지 않고 기능별로 분리한다(킥오프 플랜 3장).
        const val PrefsName = "go_ai_coach_bot_collection"
        const val StateKey = "bot_collection_state"
    }
}

internal object BotCollectionCodec {
    private const val CurrentSchemaVersion = 1

    fun encode(state: BotCollectionState): String =
        JSONObject()
            .put("schema", CurrentSchemaVersion)
            .put("claimedBots", JSONArray(state.claimedBots.map { it.raw }))
            .toString()

    fun decode(raw: String): BotCollectionState? =
        runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", -1) != CurrentSchemaVersion) return@runCatching null

            BotCollectionState(claimedBots = decodeClaimedBots(json.optJSONArray("claimedBots")))
        }.getOrNull()

    /**
     * 카탈로그에 없는 id도 걸러내지 않고 그대로 살린다 — 상위 버전에서 수집한 캐릭터를 가진 채
     * 다운그레이드한 사용자의 컬렉션이 조용히 지워지는 걸 막기 위함이다. 빈 문자열만 버린다.
     */
    private fun decodeClaimedBots(array: JSONArray?): Set<BotCharacterId> {
        if (array == null) return emptySet()
        return buildSet {
            for (i in 0 until array.length()) {
                val raw = array.optString(i)
                if (raw.isNotEmpty()) add(BotCharacterId(raw))
            }
        }
    }
}
