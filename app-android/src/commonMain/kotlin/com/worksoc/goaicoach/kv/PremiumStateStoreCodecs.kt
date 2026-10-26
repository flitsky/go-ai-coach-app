package com.worksoc.goaicoach.kv

// 안드로이드 `persistence/PremiumStateStore.kt`의 공용 사본(백로그 #294 — P2) — 저장소 클래스를 빼고 코덱만, import만 바꿨다(org.json → kv.json). 고칠 때는 둘을 함께 고친다(`KvStoreParityTest`).

import com.worksoc.goaicoach.application.premium.port.PremiumStateStorePort
import com.worksoc.goaicoach.application.premium.state.FeatureId
import com.worksoc.goaicoach.application.premium.state.PremiumSource
import com.worksoc.goaicoach.application.premium.state.PremiumState
import com.worksoc.goaicoach.kv.json.JSONArray
import com.worksoc.goaicoach.kv.json.JSONObject

internal object PremiumStateCodec {
    fun encode(state: PremiumState): String =
        JSONObject()
            .put("source", state.source.name)
            .put("adGrantStartedAtMillis", state.adGrantStartedAtMillis ?: JSONObject.NULL)
            .put("claimedFeatures", JSONArray(state.claimedFeatures.map { it.name }))
            // ⚠️ **키를 더하기만 한다**(함정 55) — 이 코덱에는 스키마 번호 등호 검사가 없으므로
            // 기존 기록은 이 키가 없는 채로 읽히고 `null`이 된다. 그것이 "아직 확인한 적 없음"이다.
            .put("lastSubscriptionVerifiedAtMillis", state.lastSubscriptionVerifiedAtMillis ?: JSONObject.NULL)
            .toString()

    fun decode(raw: String): PremiumState? =
        runCatching {
            val json = JSONObject(raw)
            PremiumState(
                source = enumOrDefault(json.optString("source"), PremiumSource.None),
                adGrantStartedAtMillis = json.optLongOrNull("adGrantStartedAtMillis"),
                claimedFeatures = json.optClaimedFeatures(),
                lastSubscriptionVerifiedAtMillis = json.optLongOrNull("lastSubscriptionVerifiedAtMillis"),
            )
        }.getOrNull()

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    // "claimedFeatures" 배열 도입 이전(구버전)엔 "isUndoClaimed" 단일 불리언으로 저장됐다 —
    // 새 키가 있으면 그걸 쓰고, 없으면 구버전 불리언을 [FeatureId.Undo] 하나짜리 집합으로
    // 마이그레이션한다. 배열 원소 중 알 수 없는 값(향후 버전 다운그레이드 등)은 개별
    // 무시한다 — 손상된 원소 하나가 전체 decode를 실패시키면 안 되므로.
    private fun JSONObject.optClaimedFeatures(): Set<FeatureId> {
        val array = optJSONArray("claimedFeatures")
        if (array != null) {
            return (0 until array.length())
                .mapNotNull { i -> runCatching { FeatureId.valueOf(array.getString(i)) }.getOrNull() }
                .toSet()
        }
        return if (optBoolean("isUndoClaimed", false)) setOf(FeatureId.Undo) else emptySet()
    }
}
