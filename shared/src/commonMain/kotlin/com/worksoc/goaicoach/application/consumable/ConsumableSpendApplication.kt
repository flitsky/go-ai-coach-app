package com.worksoc.goaicoach.application.consumable

import com.worksoc.goaicoach.application.premium.AllowedVia
import com.worksoc.goaicoach.application.premium.FeatureAccess
import com.worksoc.goaicoach.application.premium.FeatureAccessPolicy
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.PremiumStateStorePort

/** [decideConsumableSpend]의 판정 결과. */
sealed class ConsumableSpendDecision {
    /**
     * 재고를 **건드리지 않고** 통과시켰다 — 이미 [via] 경로로 접근 권한이 있었다.
     * 프리미엄이 켜져 있는 동안 잔량이 억울하게 닳는 것을 막는 4.5절의 우선순위 규칙이다.
     */
    data class AllowedWithoutSpending(val via: AllowedVia) : ConsumableSpendDecision()

    /**
     * 소모품 1장을 실제로 차감했다. [inventory]는 차감 후 재고, [remaining]은 이 종류의 잔량.
     * [premiumState]는 [ConsumableEffect.PremiumGrant]를 썼을 때만 채워지는 "켜진 프리미엄
     * 상태"이고, 기능 1회권일 때는 `null`(프리미엄 상태를 건드리지 않는다).
     */
    data class Spent(
        val inventory: ConsumableInventory,
        val remaining: Int,
        val premiumState: PremiumState?,
    ) : ConsumableSpendDecision()

    /** 접근 권한도 재고도 없다 — 기존 잠금 흐름(광고 시청/구매)으로 보내야 한다. */
    data object OutOfStock : ConsumableSpendDecision()
}

/**
 * 6계층(Session & Continuity) — 소모품 1장을 쓸지 말지 판정하는 **순수 함수**.
 *
 * 4.5절의 ⚠️ 우선순위 규칙이 여기 산다: 구매/광고/영구 클레임 중 무엇으로든 이미 쓸 수 있는
 * 기능이면 재고를 **차감하지 않고** 통과시킨다. 판정 자체는 기존 [FeatureAccessPolicy]에
 * 위임하므로, 프리미엄 정책이 바뀌어도 고칠 곳이 두 군데로 갈라지지 않는다.
 *
 * [FeatureAccessPolicy] 자체를 고쳐 소모품을 네 번째 허용 경로로 넣지 않은 이유: 그 함수는
 * 부수효과 없는 조회(`resolve`)인데 소모품은 **보는 순간 줄어드는** 자원이라, 같은 함수에
 * 합치면 "확인만 했는데 재고가 닳는" 구조가 된다. 실제 잠금 UI 배선에서 두 판정을 어떻게
 * 조합할지는 #15의 몫이다.
 */
fun decideConsumableSpend(
    item: ConsumableItem,
    inventory: ConsumableInventory,
    premiumState: PremiumState,
    nowMillis: Long,
): ConsumableSpendDecision {
    alreadyAllowedVia(item, premiumState, nowMillis)?.let { via ->
        return ConsumableSpendDecision.AllowedWithoutSpending(via)
    }
    if (!inventory.has(item.id)) return ConsumableSpendDecision.OutOfStock

    val spent = inventory.withConsumed(item.id)
    return ConsumableSpendDecision.Spent(
        inventory = spent,
        remaining = spent.countOf(item.id),
        premiumState = when (item.effect) {
            is ConsumableEffect.FeatureUse -> null
            // 광고를 본 것과 같은 상태로 켠다. claimedFeatures는 별개 축이라 반드시 이어붙인다 —
            // 통째로 덮어쓰면 이미 받은 영구 클레임(예: 1일차 무르기)이 사라진다(#4의 회귀 이력).
            ConsumableEffect.PremiumGrant ->
                PremiumState.adGranted(nowMillis).copy(claimedFeatures = premiumState.claimedFeatures)
        },
    )
}

/** 소모품을 쓰지 않고도 이미 통과할 수 있는 경로가 있으면 그 경로를, 없으면 `null`. */
private fun alreadyAllowedVia(
    item: ConsumableItem,
    premiumState: PremiumState,
    nowMillis: Long,
): AllowedVia? =
    when (val effect = item.effect) {
        is ConsumableEffect.FeatureUse ->
            (FeatureAccessPolicy.resolve(effect.featureId, premiumState, nowMillis) as? FeatureAccess.Allowed)?.via
        // 프리미엄을 켜는 표는 특정 기능에 묶이지 않으므로 "지금 프리미엄이 켜져 있는가"만 본다.
        // 이미 켜져 있다면 한 장을 써 봐야 얻는 게 없으니 차감하지 않는다.
        ConsumableEffect.PremiumGrant -> FeatureAccessPolicy.activeVia(premiumState, nowMillis)
    }

/**
 * 5계층(App Service) — 소모품 [amount]개를 지급하고 저장한다. 출석 2~4일차 보상 지급(#13)이
 * 이 함수를 부른다. 상한([ConsumableInventory.MaxPerItem]) 처리는 재고 쪽이 담당하므로 여기서는
 * 저장만 책임진다.
 */
fun runConsumableGrant(
    item: ConsumableItem,
    amount: Int,
    consumableStore: ConsumableStorePort,
): ConsumableInventory {
    val granted = consumableStore.load().withGranted(item.id, amount)
    consumableStore.save(granted)
    return granted
}

/**
 * 5계층(App Service) — [decideConsumableSpend]의 판정을 저장소에 반영한다. 실제 기능 사용
 * 지점의 배선(#15)이 이 함수를 부른다.
 *
 * 차감이 일어난 경우에만 저장하므로, 프리미엄으로 통과했거나 재고가 없던 호출은 디스크를
 * 건드리지 않는다. [ConsumableEffect.PremiumGrant]를 쓴 경우에는 켜진 프리미엄 상태까지 같이
 * 저장한다 — 두 저장이 갈라지면 "표는 닳았는데 프리미엄은 안 켜진" 상태가 남기 때문이다.
 */
fun runConsumableSpend(
    item: ConsumableItem,
    consumableStore: ConsumableStorePort,
    premiumStore: PremiumStateStorePort,
    nowMillis: Long,
): ConsumableSpendDecision {
    val decision = decideConsumableSpend(
        item = item,
        inventory = consumableStore.load(),
        premiumState = premiumStore.load(),
        nowMillis = nowMillis,
    )
    if (decision is ConsumableSpendDecision.Spent) {
        consumableStore.save(decision.inventory)
        decision.premiumState?.let { state -> premiumStore.save(state) }
    }
    return decision
}
