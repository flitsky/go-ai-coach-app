package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableItemId
import com.worksoc.goaicoach.application.consumable.ConsumableStorePort
import org.json.JSONObject

internal class ConsumableInventoryStore(context: Context) : ConsumableStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun save(inventory: ConsumableInventory) {
        prefs.edit()
            .putString(StateKey, ConsumableInventoryCodec.encode(inventory))
            .apply()
    }

    override fun load(): ConsumableInventory {
        val raw = prefs.getString(StateKey, null) ?: return ConsumableInventory()
        return ConsumableInventoryCodec.decode(raw) ?: ConsumableInventory()
    }

    private companion object {
        // 출석/히스토리/봇 컬렉션과 한 blob에 섞지 않고 기능별로 분리한다(킥오프 플랜 3장).
        const val PrefsName = "go_ai_coach_consumables"
        const val StateKey = "consumable_inventory"
    }
}

internal object ConsumableInventoryCodec {
    private const val CurrentSchemaVersion = 1
    private const val CountsKey = "counts"

    fun encode(inventory: ConsumableInventory): String {
        val counts = JSONObject()
        // 정규형대로 0 이하는 애초에 없지만, 외부에서 만들어진 값이 들어와도 기록하지 않는다.
        inventory.counts.forEach { (id, count) ->
            if (count > 0) counts.put(id.raw, count)
        }
        return JSONObject()
            .put("schema", CurrentSchemaVersion)
            .put(CountsKey, counts)
            .toString()
    }

    fun decode(raw: String): ConsumableInventory? =
        runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", -1) != CurrentSchemaVersion) return@runCatching null

            ConsumableInventory(counts = decodeCounts(json.optJSONObject(CountsKey)))
        }.getOrNull()

    /**
     * 카탈로그에 없는 종류도 걸러내지 않고 그대로 살린다 — 상위 버전에서 받은 재고를 가진 채
     * 다운그레이드한 사용자의 잔량이 조용히 지워지는 걸 막기 위함이다(`BotCollectionCodec`과
     * 같은 이유). 잔량이 0 이하이거나 숫자가 아닌 항목만 버린다.
     */
    private fun decodeCounts(json: JSONObject?): Map<ConsumableItemId, Int> {
        if (json == null) return emptyMap()
        return buildMap {
            for (raw in json.keys()) {
                val count = json.optInt(raw, 0)
                if (raw.isNotEmpty() && count > 0) put(ConsumableItemId(raw), count)
            }
        }
    }
}
