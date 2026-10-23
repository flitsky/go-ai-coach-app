package com.worksoc.goaicoach.application.consumable

/**
 * 「광고를 보고 광고 스킵권 1장 받기」(백로그 #283, 사용자 2026-10-23) — 여유가 있을 때 스킵권을 미리 챙겨 두려는 사람에게 주는 길.
 *
 * 규칙(사용자): **광고 1편 = 스킵권 1장, 하루 한 번.** 받으면 **쿨타임**이 돈다 — 받은 때부터 [AdSkipTicketAdCooldownMillis](24시간).
 * - 값어치는 그대로다: 스킵권 1장은 「리워드 광고 1편을 대신 내는 표」(프리미엄 1시간)이니, 광고 1편으로 1장을 받는 것은 그 광고를 미리 보는 것뿐이다.
 * - 하루 한 번인 이유: 스킵권은 출석 보상(4 · 14 · 21일차)에서 일부러 귀하게 둔 표다(상한 9 — `ATTENDANCE_REWARD_POLICY.md` §3).
 *   막지 않으면 광고만 연달아 보고 상한을 채워 출석 보상의 무게가 사라진다(함정 14 — 같은 권한에 지급 경로 둘).
 * - 24시간 쿨타임은 스레드가 정했다 — 「하루 한 번」을 출석의 UTC 하루로 세면 한국에서는 오전 9시에 풀려 「쿨타임」과 다르게 읽힌다.
 *
 * ⚠️ 시계를 되감아 마지막으로 받은 때가 미래가 되면 **막지 않는다** — 오프라인 앱이라 시계를 믿을 수밖에 없고(함정 12), 거기서 막으면
 *   시계를 고친 정직한 사용자가 갇힌다. 앞으로 감아 당겨 받는 것도 막을 길이 없다(출석과 같은 처지).
 */
const val AdSkipTicketAdCooldownMillis: Long = 24L * 60 * 60 * 1000

/** 「광고 보고 스킵권 받기」 단추가 지금 무엇을 보여야 하는가. */
sealed interface AdSkipTicketAdAvailability {
    /** 광고를 보면 1장을 받는다. */
    data object Ready : AdSkipTicketAdAvailability

    /** 받은 지 하루가 안 됐다 — [remainingMillis] 뒤에 다시 받을 수 있다. */
    data class Cooldown(val remainingMillis: Long) : AdSkipTicketAdAvailability

    /** 이미 보유 상한이다 — 광고를 봐도 받을 것이 없으니 광고를 띄우지 않는다. 쿨타임보다 먼저 본다. */
    data object AtMaxStock : AdSkipTicketAdAvailability
}

/** 광고로 스킵권을 마지막으로 받은 때를 기억하는 저장소. */
interface AdSkipTicketAdStorePort {
    /** 마지막으로 받은 때(epoch ms). 한 번도 받지 않았으면 `null`. */
    fun lastEarnedAtMillis(): Long?

    fun markEarned(atMillis: Long)
}

/** 지금 받을 수 있는가 — 순수 판정. */
fun adSkipTicketAdAvailability(
    inventory: ConsumableInventory,
    lastEarnedAtMillis: Long?,
    nowMillis: Long,
): AdSkipTicketAdAvailability {
    if (inventory.isAtMaxStock(ConsumableCatalog.PremiumOnce.id)) return AdSkipTicketAdAvailability.AtMaxStock
    if (lastEarnedAtMillis == null || lastEarnedAtMillis > nowMillis) return AdSkipTicketAdAvailability.Ready
    val remaining = lastEarnedAtMillis + AdSkipTicketAdCooldownMillis - nowMillis
    return if (remaining > 0) AdSkipTicketAdAvailability.Cooldown(remaining) else AdSkipTicketAdAvailability.Ready
}

/**
 * 6계층 — 광고를 끝까지 본 뒤 스킵권 1장을 지급하고, 받은 때를 적는다. 지금 받을 수 없으면(쿨타임 · 상한) 아무것도 쓰지 않고 `null`.
 *
 * ⚠️ 판정을 **광고를 본 뒤에 다시** 한다 — 광고가 떠 있는 동안 출석 보상이 같은 재고에 써서 상한에 닿았을 수 있다.
 * 받은 때는 지급과 함께 적는다 — 둘이 갈라지면 「받았는데 쿨타임이 안 돈다」거나 「쿨타임만 돌고 못 받았다」가 남는다.
 */
fun runAdSkipTicketAdGrant(
    consumableStore: ConsumableStorePort,
    adStore: AdSkipTicketAdStorePort,
    nowMillis: Long,
): ConsumableInventory? {
    val availability = adSkipTicketAdAvailability(consumableStore.load(), adStore.lastEarnedAtMillis(), nowMillis)
    if (availability != AdSkipTicketAdAvailability.Ready) return null
    val granted = runConsumableGrant(ConsumableCatalog.PremiumOnce, 1, consumableStore)
    adStore.markEarned(nowMillis)
    return granted
}
