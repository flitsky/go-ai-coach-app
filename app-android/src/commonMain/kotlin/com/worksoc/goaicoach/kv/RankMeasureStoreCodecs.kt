package com.worksoc.goaicoach.kv

// 안드로이드 `persistence/RankMeasureStore.kt`의 공용 사본(백로그 #294 — P2) — 저장소 클래스를 빼고 코덱만, import만 바꿨다(org.json → kv.json). 고칠 때는 둘을 함께 고친다(`KvStoreParityTest`).

import com.worksoc.goaicoach.application.rankmeasure.RankMeasureMaxPromotionSteps
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureState
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureStorePort
import com.worksoc.goaicoach.application.rankmeasure.RankPromotionOffer
import com.worksoc.goaicoach.kv.json.JSONObject
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank

/**
 * 승급 대국 설정 창에서 **마지막으로 시작한** 진영과 판 크기(백로그 #286 — 사용자: *"19*19를 선택해서 플레이 했었을 경우, 앱 재실행하여
 * 메뉴 진입시에 그대로 유지"*). 설정 창은 이 값으로 열린다.
 *
 * ⚠️ 기력 상태([RankMeasureState])와 **다른 키**다 — 「급수 초기화하기」는 기력만 처음으로 되돌리고 고른 판은 남긴다.
 * 일반 대국 설정(`UserPreferencesSnapshot`)과도 섞지 않는다 — 승급 대국의 판은 일반 설정을 덮어쓰지 않는다(`leaveRankMeasureGame`).
 * 판 크기는 고른 그대로 남긴다 — 그 기력에서 못 두는 판이면 설정 창이 둘 수 있는 가장 작은 판으로 보인다(`rankMeasureBoardSizeFor`).
 */
internal data class RankMeasureLastSetup(
    val userColor: StoneColor = StoneColor.Black,
    val boardSize: BoardSize = BoardSize.Nine,
)

/** 모르는 색 · 지원하지 않는 판 크기는 그 필드의 처음 값으로 읽는다(함정 69 — 키만 더한다). */
internal object RankMeasureLastSetupCodec {
    fun encode(setup: RankMeasureLastSetup): String =
        JSONObject()
            .put("userColor", setup.userColor.name)
            .put("boardSize", setup.boardSize.value)
            .toString()

    fun decode(raw: String): RankMeasureLastSetup? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val defaults = RankMeasureLastSetup()
        return RankMeasureLastSetup(
            userColor = StoneColor.entries.firstOrNull { it.name == json.optString("userColor") } ?: defaults.userColor,
            boardSize = BoardSize.supported().firstOrNull { it.value == json.optInt("boardSize") } ?: defaults.boardSize,
        )
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
            .put("consecutiveLosses", state.consecutiveLosses)
            .put("lastCountedGameId", state.lastCountedGameId ?: JSONObject.NULL)
            .put("promotionOffer", state.promotionOffer?.let(::encodeOffer) ?: JSONObject.NULL)
            .toString()

    fun decode(raw: String): RankMeasureState? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val defaults = RankMeasureState()
        return RankMeasureState(
            rank = KgsRank.ofStepCoerced(json.optInt("rankStep", defaults.rank.step)),
            hasStarted = json.optBoolean("hasStarted", false),
            // 깨진 값은 「측정 기록 없음」으로 읽는다 — 0으로 읽어 20급을 최고 기력으로 만들어 내지 않는다.
            peakRank = json.optInt("peakRankStep", 0).takeIf { it >= KgsRank.Weakest.step }?.let(KgsRank::ofStepCoerced),
            consecutiveLosses = json.optInt("consecutiveLosses", 0).coerceAtLeast(0),
            // 옛 저장분의 `consecutiveWins`·`rally`(2026-10-06의 랠리 규칙)와 `recentDanResults`(2026-10-07의 「최근 5판 중 3판」 — #259가
            // 「10집 이상 승리」로 갈아 끼웠다)는 읽지 않는다 — 규칙이 바뀌었다. 키가 남아 있어도 해가 없다(스키마 번호는 그대로, 함정 69).
            lastCountedGameId = json.optString("lastCountedGameId", "").takeIf { it.isNotBlank() && !json.isNull("lastCountedGameId") },
            promotionOffer = json.optJSONObject("promotionOffer")?.let(::decodeOffer),
        )
    }

    /** 고르지 않은 승급(#268) — 키가 없던 옛 저장분은 `null`(고를 것이 없다). */
    private fun encodeOffer(offer: RankPromotionOffer): JSONObject =
        JSONObject()
            .put("steps", offer.steps)
            .put("margin", offer.margin)
            .put("byResignation", offer.byResignation)

    /** 폭이 1보다 작거나 집 수 차이가 없으면 깨진 것이다 — 고를 것이 없는 것으로 읽는다. 폭은 한 판의 최대로 당긴다. */
    private fun decodeOffer(json: JSONObject): RankPromotionOffer? {
        val steps = json.optInt("steps", 0)
        val margin = json.optDouble("margin", Double.NaN)
        if (steps < 1 || margin.isNaN()) return null
        return RankPromotionOffer(
            steps = steps.coerceAtMost(RankMeasureMaxPromotionSteps),
            margin = margin,
            byResignation = json.optBoolean("byResignation", false),
        )
    }
}
