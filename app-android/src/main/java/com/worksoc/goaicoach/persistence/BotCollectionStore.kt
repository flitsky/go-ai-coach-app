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

        /**
     * 획득 캐릭터·조각 진행도 저장분을 통째로 지워 **설치 직후와 같은 상태로 되돌린다**(정식 릴리즈 초기화, 백로그 #63).
     *
     * ⚠️ 기본값을 `save`하지 않고 키를 **제거**한다 — 기본값을 써 넣으면 "한 번도 저장한 적 없음"과
     * "기본값을 저장함"이 저장소에서 구분되지 않고, 나중에 스키마가 늘 때 그 둘의 의미가 갈릴 수 있다.
     * 신규 설치를 그대로 재현하는 쪽이 초기화의 정의에 맞다.
     */
    fun clear() {
        prefs.edit().clear().commit()
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
    private const val ShardsKey = "adShards"

    fun encode(state: BotCollectionState): String {
        val shards = JSONObject()
        // 0 이하는 애초에 정규형에 없지만, 외부에서 만들어진 값이 들어와도 기록하지 않는다.
        state.adShards.forEach { (id, count) -> if (count > 0) shards.put(id.raw, count) }
        return JSONObject()
            .put("schema", CurrentSchemaVersion)
            .put("claimedBots", JSONArray(state.claimedBots.map { it.raw }))
            .put(ShardsKey, shards)
            .toString()
    }

    /**
     * ⚠️ 조각 진행도(#11)를 더하면서도 **스키마 번호를 올리지 않았다.** 이 코덱은 번호가 다르면
     * `null`을 돌려주고 호출부가 기본 상태로 폴백하므로, 번호를 올리면 **이미 수집한 캐릭터가
     * 통째로 날아간다.** 새 필드는 없으면 빈 값으로 읽으면 그만이라 하위호환으로 충분하다 —
     * 기존 저장분에는 `shards` 키가 없고, 그 경우 진행도만 0에서 시작한다.
     */
    fun decode(raw: String): BotCollectionState? =
        runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", -1) != CurrentSchemaVersion) return@runCatching null

            BotCollectionState(
                claimedBots = decodeClaimedBots(json.optJSONArray("claimedBots")),
                adShards = decodeShards(json.optJSONObject(ShardsKey)),
            )
        }.getOrNull()

    /** [decodeClaimedBots]와 같은 이유로 모르는 id도 살린다. 0 이하이거나 숫자가 아닌 항목만 버린다. */
    private fun decodeShards(json: JSONObject?): Map<BotCharacterId, Int> {
        if (json == null) return emptyMap()
        return buildMap {
            for (raw in json.keys()) {
                val count = json.optInt(raw, 0)
                if (raw.isNotEmpty() && count > 0) put(BotCharacterId(raw), count)
            }
        }
    }

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
