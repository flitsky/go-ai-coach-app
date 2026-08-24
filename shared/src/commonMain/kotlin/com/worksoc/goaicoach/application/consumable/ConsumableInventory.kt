package com.worksoc.goaicoach.application.consumable

/**
 * 6계층(Session & Continuity) — 소모품 재고 원장.
 *
 * [com.worksoc.goaicoach.application.premium.PremiumState.claimedFeatures]는 "한 번 켜지면 계속
 * 켜져 있는" boolean 원장이라 **쓰면 줄어드는 이 개념을 담을 수 없다**(킥오프 플랜 4.5절) —
 * 3장의 "신규 기능은 서로 다른 Port로 분리한다" 원칙에 따라 별도 타입 + 별도 Port로 뒀다.
 *
 * [counts]의 정규형에는 **0 이하 항목이 남지 않는다** — 다 쓴 종류는 키째 사라진다. 카탈로그
 * ([ConsumableCatalog])에 없는 키가 들어 있을 수도 있는데(상위 버전에서 받은 뒤 다운그레이드)
 * 그런 재고도 버리지 않고 그대로 보존한다.
 */
data class ConsumableInventory(
    val counts: Map<ConsumableItemId, Int> = emptyMap(),
) {
    /** 이 종류의 잔량. 없거나 음수면 0. */
    fun countOf(id: ConsumableItemId): Int = (counts[id] ?: 0).coerceAtLeast(0)

    fun has(id: ConsumableItemId): Boolean = countOf(id) > 0

    /**
     * [amount]개를 지급한다(#13에서 출석 보상으로 호출). 종류별 상한 [MaxPerItem]을 넘지 않는다 —
     * 출석 주기가 반복되면 무한히 쌓이기 때문에 잠금장치를 뒀다(2026-08-24 사용자 확정).
     * [amount]가 0 이하면 아무 일도 하지 않는다.
     */
    fun withGranted(id: ConsumableItemId, amount: Int): ConsumableInventory {
        if (amount <= 0) return this
        // Long으로 더한다 — amount가 Int.MAX_VALUE에 가까우면 Int 덧셈이 음수로 넘칠 수 있다.
        val next = (countOf(id).toLong() + amount).coerceAtMost(MaxPerItem.toLong()).toInt()
        if (next == countOf(id)) return this
        return copy(counts = counts + (id to next))
    }

    /**
     * [amount]개를 차감한다(기본 1개). 0 미만으로는 내려가지 않고, 0이 되면 키 자체를 지워
     * "한 번도 받은 적 없음"과 같은 정규형으로 되돌린다. 재고가 없으면 아무 일도 하지 않는다.
     *
     * ⚠️ 이 함수는 **프리미엄 활성 여부를 보지 않는다.** 프리미엄이 켜져 있는 동안 잔량이
     * 억울하게 닳지 않게 하는 우선순위 판정은 [decideConsumableSpend]가 담당한다 — 소비 지점은
     * 이 함수를 직접 부르지 말고 그쪽을 거쳐야 한다(4.5절).
     */
    fun withConsumed(id: ConsumableItemId, amount: Int = 1): ConsumableInventory {
        if (amount <= 0) return this
        val current = countOf(id)
        if (current == 0) return this
        val next = (current.toLong() - amount).coerceAtLeast(0L).toInt()
        return if (next == 0) copy(counts = counts - id) else copy(counts = counts + (id to next))
    }

    companion object {
        /** 종류별 재고 상한. 잔량 표시(#15)가 두 자리로 고정되는 부수 효과도 있다. */
        const val MaxPerItem: Int = 99
    }
}
