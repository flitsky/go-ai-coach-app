package com.worksoc.goaicoach.kv

// 안드로이드 `persistence/ConsumableInventoryStore.kt`의 공용 사본(백로그 #294 — P2) — 저장소 클래스를 빼고 코덱만, import만 바꿨다(org.json → kv.json). 고칠 때는 둘을 함께 고친다(`KvStoreParityTest`).

import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableItemId
import com.worksoc.goaicoach.application.consumable.ConsumableStorePort
import com.worksoc.goaicoach.kv.json.JSONObject

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
