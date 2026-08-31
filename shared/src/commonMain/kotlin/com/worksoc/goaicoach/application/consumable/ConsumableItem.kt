package com.worksoc.goaicoach.application.consumable

import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.PremiumState

/**
 * 소모품 종류의 영구 식별자. 저장소(`persistence/ConsumableInventoryStore.kt`)에 JSON 키로 그대로
 * 기록되므로 **한 번 정한 [raw] 값은 바꾸지 않는다** — 값을 바꾸면 이미 쌓아 둔 재고가 통째로
 * 유실된다.
 *
 * enum이 아니라 `data class`인 이유는 [com.worksoc.goaicoach.application.botcharacter.BotCharacterId]와
 * 같다 — 상위 버전에서 받은 *모르는 종류*의 재고도 버리지 않고 보존해야 하는데, enum은 모르는
 * 값을 표현할 수 없다.
 */
data class ConsumableItemId(val raw: String)

/**
 * 소모품 1장을 실제로 썼을 때 일어나는 일. 이 축이 있어야 "기능 1회권"과 "광고 스킵권"을 한
 * 재고 원장에서 같이 다룰 수 있다 — 전자는 특정 기능 한 번에 묶이지만 후자는 기능과 무관하게
 * 프리미엄 자체를 켜기 때문이다.
 */
sealed class ConsumableEffect {
    /** [featureId] 기능을 이번 한 번 쓸 수 있게 해준다. */
    data class FeatureUse(val featureId: FeatureId) : ConsumableEffect()

    /**
     * 리워드 광고를 **보지 않고** 프리미엄 모드를 켠다. 지속 시간은 광고를 실제로 봤을 때와
     * 똑같이 [PremiumState.AdGrantDurationMillis](1시간)다 — "광고 1회분을 대신 내는 표"라는
     * 뜻이라 보상 가치가 광고 시청과 어긋나지 않는다(2026-08-24 사용자 확정).
     *
     * 참고: 현재 이 앱에 **강제로 뜨는 광고는 없다.** 배너 컴포저블은 정의만 되어 있고 어느
     * 화면에도 붙어 있지 않으며(`ui/BannerAdView.kt`), 사용자가 보는 유일한 광고는 잠긴 기능을
     * 풀려고 자발적으로 보는 리워드 광고다(`ui/PremiumPurchaseGlue.kt`). 그래서 이 표가 스킵하는
     * 대상도 그 리워드 광고 하나뿐이다.
     */
    data object PremiumGrant : ConsumableEffect()
}

/** 소모품 한 종류의 카탈로그 정의. "몇 개 갖고 있는가"는 [ConsumableInventory]가 따로 관리한다. */
data class ConsumableItem(
    val id: ConsumableItemId,
    val effect: ConsumableEffect,
    /**
     * 이 종류를 최대 몇 개까지 들고 있을 수 있는가(백로그 #55, 2026-08-31 사용자 확정).
     *
     * 상한을 두는 이유는 재고 관리가 아니라 **행동 유도**다 — 쌓이면 가치가 희석되고 "모으는
     * 재미"에 빠지므로, *"어차피 또 받을 것이니 쓰자"* 쪽으로 민다. 그래서 종류마다 다르다.
     */
    val maxStock: Int = DefaultMaxStock,
)

/** 소모품 기본 보유 상한. */
const val DefaultMaxStock: Int = 99

/**
 * 광고 스킵권만 낮은 상한을 쓴다 — 이건 **광고를 안 보게 해 주는** 표라, 넉넉히 쌓이면
 * 프리미엄 구독(#26)과 광고 수익 양쪽의 의미가 함께 옅어진다.
 */
const val PremiumOnceMaxStock: Int = 9

/**
 * 6계층(Session & Continuity) — 소모품 카탈로그. 킥오프 플랜 4.5절의 3종이 전부이며, 각각 출석
 * 2·3·4일차 보상으로 10개씩 들어온다(4.2절). 지급 배선은 #13, 실제 소비 배선은 #15의 몫이다.
 */
object ConsumableCatalog {

    /** 출석 2일차 보상 — '단발성 형세 보기' 1회권. */
    val EvalOnce = ConsumableItem(
        id = ConsumableItemId("eval_once"),
        effect = ConsumableEffect.FeatureUse(FeatureId.Eval),
    )

    /** 출석 3일차 보상 — '단발성 추천 수' 1회권. */
    val TopMovesOnce = ConsumableItem(
        id = ConsumableItemId("top_moves_once"),
        effect = ConsumableEffect.FeatureUse(FeatureId.TopMoves),
    )

    /** 출석 4일차 보상 — '단발성 프리미엄 모드 활성화'(= 광고 스킵권). */
    val PremiumOnce = ConsumableItem(
        id = ConsumableItemId("premium_once"),
        effect = ConsumableEffect.PremiumGrant,
        maxStock = PremiumOnceMaxStock,
    )

    val all: List<ConsumableItem> = listOf(EvalOnce, TopMovesOnce, PremiumOnce)

    fun byId(id: ConsumableItemId): ConsumableItem? = all.firstOrNull { item -> item.id == id }

    /** 저장된 키 문자열을 카탈로그 정의로 되돌린다. 모르는 키면 `null`(다운그레이드 등). */
    fun byRawId(raw: String): ConsumableItem? = byId(ConsumableItemId(raw))

    /**
     * 이 기능을 1회권으로 쓸 수 있다면 그 소모품을 돌려준다. 1회권이 없는 기능
     * ([FeatureId.Undo]는 1일차에 영구 클레임으로 풀리고, [FeatureId.MoveReview]는 보상 대상이
     * 아니다)에는 `null`.
     */
    /**
     * 이 종류의 보유 상한. 카탈로그에 없는 id(다운그레이드로 흘러든 옛 저장값 등)는 기본 상한을
     * 쓴다 — 모르는 값 때문에 지급이 통째로 막히는 것보다 낫다.
     */
    fun maxStockOf(id: ConsumableItemId): Int = byId(id)?.maxStock ?: DefaultMaxStock

    fun forFeature(featureId: FeatureId): ConsumableItem? =
        all.firstOrNull { item ->
            (item.effect as? ConsumableEffect.FeatureUse)?.featureId == featureId
        }
}
