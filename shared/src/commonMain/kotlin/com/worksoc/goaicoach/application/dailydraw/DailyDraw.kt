package com.worksoc.goaicoach.application.dailydraw

import com.worksoc.goaicoach.application.attendance.AttendanceReward
import com.worksoc.goaicoach.application.attendance.AttendanceRewardPolicy
import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import com.worksoc.goaicoach.application.botcharacter.TopCharacterAttendanceTier
import com.worksoc.goaicoach.application.botcharacter.WeeklyRewardCycleTier
import com.worksoc.goaicoach.application.botcharacter.runBotCharacterShardGrant
import com.worksoc.goaicoach.application.botcharacter.runBotCharacterUnlock
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableStorePort
import com.worksoc.goaicoach.application.consumable.runConsumableGrant
import kotlin.random.Random

/*
 * 실험실의 「1일 1회 뽑기」(백로그 #283 · #289, 사용자 2026-10-23 · 10-24) — 규칙은 이 파일 한 곳이다.
 *
 * 사용자가 정한 것:
 * - **광고 1편을 보면 한 번** 뽑는다. 구독자는 광고 없이 한 번. 뽑으면 쿨타임이 돈다.
 * - 상품 여섯과 확률(#289): 캐릭터 조각 1개 30% · 형세 보기 · 추천 수 1회권 10장씩 30% · 광고 스킵권 1장 30%
 *   · **광고 캐릭터 7%** · **수제자 반상(출석 7일차) 2%** · **관장 천원(출석 28일차) 1%**.
 * - **90% 묶음**(조각 · 1회권 · 스킵권)은 줄 수 없는 것이 빠지면 **남은 것끼리 90%를 나눈다** — 조각 길을 다 가졌으면 1회권 · 스킵권이 45%씩.
 * - **10% 묶음**(캐릭터 셋)은 **늘 7 · 2 · 1로 고정**이다. 줄 수 없으면(이미 가졌다 · 광고 캐릭터를 다 가졌다) 그 자리에서
 *   **7일 단위 보상**(출석 14 · 21일차 묶음, `AttendanceRewardPolicy.weeklyRepeatBundle`)으로 바꿔 준다.
 * - 광고 캐릭터 · 조각은 **아직 갖지 않은 조각 길 캐릭터(2 · 4단계) 중 가장 낮은 등급**에게 간다.
 * - 반상 · 천원을 뽑기로 열 수 있게 한 것은 #283의 결정(출석 캐릭터는 출석으로만)을 뒤집은 것이다 — 뽑기를 더 해 보고 싶게 하려고.
 *   그 대신 출석일이 오면 이미 가진 캐릭터는 7일 단위 보상으로 대체된다(`AttendanceRewardPolicy.resolve`, 같은 묶음).
 * - ⚠️ 뽑기 · 출석으로 얻은 캐릭터는 **구독과 상관없이 폰에 저장된다**(`claimedBots`) — 구독의 「모든 캐릭터」는 판정(`isAvailable`)일 뿐이라,
 *   구독자가 뽑아도 [dailyDrawPrizeCharacter]는 `isClaimed`로 고르고 실제로 저장한다. 해지해도 남는다.
 *
 * 스레드가 정한 것:
 * - 쿨타임은 **뽑은 때부터 24시간**([DailyDrawCooldownMillis]) — 출석의 UTC 하루로 세면 한국에서는 오전 9시에 풀려 「쿨타임」과 다르게 읽힌다.
 * - 광고 스킵권이 보유 상한(9)이면 스킵권도 「줄 수 없는 것」으로 90% 묶음에서 빠진다(조각과 같은 처리 — #289에서 사용자가 말한 것은 조각뿐).
 *   1회권은 늘 남는다 — 99장 상한이면 넘친 만큼 버려질 뿐이다(출석과 같은 처리). 대체 지급의 스킵권 3장도 상한을 넘친 만큼은 버려진다.
 * - 시계를 되감아 마지막으로 뽑은 때가 미래가 되면 막지 않는다 — 오프라인 앱이라 시계를 믿을 수밖에 없고(함정 12), 막으면 정직한 사용자가 갇힌다.
 */

/** 뽑은 때부터 다시 뽑을 수 있을 때까지. */
const val DailyDrawCooldownMillis: Long = 24L * 60 * 60 * 1000

/** 「1회권」 상품이 주는 장수 — 형세 보기와 추천 수에 **각각**. */
const val DailyDrawTicketsEach: Int = 10

/** 90% 묶음의 몫 — 그 안에서 줄 수 있는 상품끼리 나눈다. 나머지 10%는 캐릭터 셋의 고정 몫이다. */
const val DailyDrawCommonShare: Int = 90

/** 상품의 종류와 기본 확률(백분율). 합이 100이고, [dailyDrawOdds]도 늘 합 100을 내서 화면에 그대로 적는다. */
enum class DailyDrawPrizeKind(val weight: Int, val isCharacterPrize: Boolean = false) {
    CharacterShard(30),
    Tickets(30),
    AdSkipTicket(30),

    /** 광고(조각 길) 캐릭터 완전판 — 아직 없는 것 중 가장 낮은 등급. */
    Character(7, isCharacterPrize = true),

    /** 출석 7일차 캐릭터(수제자 반상). */
    Day7Character(2, isCharacterPrize = true),

    /** 출석 28일차 캐릭터(관장 천원). */
    Day28Character(1, isCharacterPrize = true),
}

/** 지금 뽑을 수 있는가. */
sealed interface DailyDrawAvailability {
    data object Ready : DailyDrawAvailability

    /** 뽑은 지 하루가 안 됐다 — [remainingMillis] 뒤에 다시 뽑는다. */
    data class Cooldown(val remainingMillis: Long) : DailyDrawAvailability
}

/** 뽑은 결과 — 화면이 알릴 것을 그대로 싣는다(화면이 저장소 사본으로 다시 추론하지 않게, 백로그 #68과 같은 이유). */
data class DailyDrawResult(
    val kind: DailyDrawPrizeKind,
    /** 조각 · 캐릭터가 간 캐릭터 — 대체 지급이면 이미 가진 그 캐릭터(광고 캐릭터를 다 가졌으면 `null`). 다른 상품이면 `null`. */
    val character: BotCharacter? = null,
    /** 이번 뽑기로 캐릭터를 **영구 획득**했는가 — 캐릭터 상품이거나, 조각이 필요 수를 채웠다. */
    val unlocked: Boolean = false,
    /** 조각을 받은 뒤 그 캐릭터에 모인 조각 수(획득까지 갔으면 0). */
    val shards: Int = 0,
    /** 캐릭터 상품을 줄 수 없어 **7일 단위 보상으로 바꿔 줬다**(#289). */
    val substituted: Boolean = false,
    /** 대체로 **실제로 들어간** 소모품과 수 — 상한에 걸려 버려진 만큼은 뺐다(출석 팝업의 「보유 상한」과 같은 이유). */
    val substituteGrants: List<AttendanceReward.Consumable> = emptyList(),
)

/** 조각 · 광고 캐릭터가 갈 캐릭터 — 아직 갖지 않은 조각 길 캐릭터 중 가장 낮은 등급. 다 가졌으면 `null`. */
fun dailyDrawCharacterTarget(collection: BotCollectionState): BotCharacter? =
    BotCharacterCatalog.all
        .filter { character -> character.unlockSource is BotUnlockSource.AdShards && !collection.isClaimed(character.id) }
        .minByOrNull { character -> character.tierWithinGroup ?: Int.MAX_VALUE }

/**
 * 캐릭터 상품이 가리키는 캐릭터 — 반상 · 천원은 늘 그 캐릭터(이미 가졌어도), 광고 캐릭터는 [dailyDrawCharacterTarget](다 가졌으면 `null`).
 * 캐릭터 상품이 아니면 `null`. 출석 캐릭터는 카탈로그의 회차로 찾는다 — 회차를 여기 또 적지 않는다.
 */
fun dailyDrawPrizeCharacter(kind: DailyDrawPrizeKind, collection: BotCollectionState): BotCharacter? =
    when (kind) {
        DailyDrawPrizeKind.Character -> dailyDrawCharacterTarget(collection)
        DailyDrawPrizeKind.Day7Character -> BotCharacterCatalog.forAttendanceTier(WeeklyRewardCycleTier).firstOrNull()
        DailyDrawPrizeKind.Day28Character -> BotCharacterCatalog.forAttendanceTier(TopCharacterAttendanceTier).firstOrNull()
        DailyDrawPrizeKind.CharacterShard, DailyDrawPrizeKind.Tickets, DailyDrawPrizeKind.AdSkipTicket -> null
    }

/** 이 캐릭터 상품을 지금 **그대로** 줄 수 있는가 — 아니면 7일 단위 보상으로 대체된다. */
fun dailyDrawCharacterGrantable(kind: DailyDrawPrizeKind, collection: BotCollectionState): Boolean =
    dailyDrawPrizeCharacter(kind, collection)?.let { character -> !collection.isClaimed(character.id) } == true

/**
 * 지금의 확률(백분율, 합 100) — 화면이 상품마다 한 줄씩 적을 때도 이것을 쓴다.
 * 90% 묶음은 줄 수 있는 것끼리 [DailyDrawCommonShare]를 무게대로 나누고(30 · 30 · 30 → 45 · 45 → 90), 캐릭터 셋은 늘 7 · 2 · 1이다.
 */
fun dailyDrawOdds(inventory: ConsumableInventory, collection: BotCollectionState): Map<DailyDrawPrizeKind, Int> {
    val hasTarget = dailyDrawCharacterTarget(collection) != null
    val skipFull = inventory.isAtMaxStock(ConsumableCatalog.PremiumOnce.id)
    val commons = DailyDrawPrizeKind.entries.filter { kind ->
        when (kind) {
            DailyDrawPrizeKind.CharacterShard -> hasTarget
            DailyDrawPrizeKind.AdSkipTicket -> !skipFull
            DailyDrawPrizeKind.Tickets -> true
            else -> false
        }
    }
    val commonWeight = commons.sumOf { kind -> kind.weight }
    return DailyDrawPrizeKind.entries
        .mapNotNull { kind ->
            when {
                kind.isCharacterPrize -> kind to kind.weight
                kind in commons -> kind to kind.weight * DailyDrawCommonShare / commonWeight
                else -> null
            }
        }
        .toMap()
}

/** [roll]은 `0 until 무게의 합` — 무게를 차례로 쌓아 떨어지는 칸을 고른다. */
fun pickDailyDrawPrize(odds: Map<DailyDrawPrizeKind, Int>, roll: Int): DailyDrawPrizeKind {
    var floor = 0
    for ((kind, weight) in odds) {
        floor += weight
        if (roll < floor) return kind
    }
    return odds.keys.last()
}

/** 지금 뽑을 수 있는가 — 순수 판정. */
fun dailyDrawAvailability(lastDrawnAtMillis: Long?, nowMillis: Long): DailyDrawAvailability {
    if (lastDrawnAtMillis == null || lastDrawnAtMillis > nowMillis) return DailyDrawAvailability.Ready
    val remaining = lastDrawnAtMillis + DailyDrawCooldownMillis - nowMillis
    return if (remaining > 0) DailyDrawAvailability.Cooldown(remaining) else DailyDrawAvailability.Ready
}

/**
 * 6계층 — 한 번 뽑아 지급하고, 뽑은 때를 적는다. 쿨타임이면 아무것도 쓰지 않고 `null`.
 *
 * ⚠️ 상품은 **광고를 본 뒤에** 정한다(호출부가 광고가 끝난 다음에 부른다) — 광고가 떠 있는 동안 출석 보상이 같은 재고 · 수집에 썼을 수 있어,
 *   저장소를 지금 읽어 확률을 다시 짠다. 지급은 기존 6계층 함수(read-modify-write)를 그대로 지난다.
 * ⚠️ 뽑은 때는 지급과 함께 적는다 — 둘이 갈라지면 「받았는데 쿨타임이 안 돈다」거나 「쿨타임만 돌고 못 받았다」가 남는다.
 */
fun runDailyDraw(
    consumableStore: ConsumableStorePort,
    collectionStore: BotCollectionStorePort,
    drawStore: DailyDrawStorePort,
    nowMillis: Long,
    random: Random,
): DailyDrawResult? {
    if (dailyDrawAvailability(drawStore.lastDrawnAtMillis(), nowMillis) != DailyDrawAvailability.Ready) return null
    val collection = collectionStore.load()
    val odds = dailyDrawOdds(consumableStore.load(), collection)
    val kind = pickDailyDrawPrize(odds, random.nextInt(odds.values.sum()))
    val result = when (kind) {
        DailyDrawPrizeKind.Tickets -> {
            runConsumableGrant(ConsumableCatalog.EvalOnce, DailyDrawTicketsEach, consumableStore)
            runConsumableGrant(ConsumableCatalog.TopMovesOnce, DailyDrawTicketsEach, consumableStore)
            DailyDrawResult(kind)
        }
        DailyDrawPrizeKind.AdSkipTicket -> {
            runConsumableGrant(ConsumableCatalog.PremiumOnce, 1, consumableStore)
            DailyDrawResult(kind)
        }
        DailyDrawPrizeKind.CharacterShard -> {
            val character = requireNotNull(dailyDrawCharacterTarget(collection)) { "조각 상품은 대상 캐릭터가 있을 때만 나온다(dailyDrawOdds)." }
            val grant = runBotCharacterShardGrant(character, collectionStore)
            DailyDrawResult(kind, character, unlocked = grant?.unlocked == true, shards = grant?.state?.shardsFor(character.id) ?: 0)
        }
        DailyDrawPrizeKind.Character, DailyDrawPrizeKind.Day7Character, DailyDrawPrizeKind.Day28Character -> {
            val character = dailyDrawPrizeCharacter(kind, collection)
            if (dailyDrawCharacterGrantable(kind, collection) && character != null) {
                runBotCharacterUnlock(character.id, collectionStore)
                DailyDrawResult(kind, character, unlocked = true)
            } else {
                DailyDrawResult(kind, character, substituted = true, substituteGrants = grantWeeklyBundle(consumableStore))
            }
        }
    }
    drawStore.markDrawn(nowMillis)
    return result
}

/** 7일 단위 보상을 지급하고, 실제로 들어간 수를 돌려준다(상한에 걸려 버려진 만큼은 뺀다). */
private fun grantWeeklyBundle(consumableStore: ConsumableStorePort): List<AttendanceReward.Consumable> =
    AttendanceRewardPolicy.weeklyRepeatBundle.map { reward ->
        val granted = consumableStore.load().grantableAmount(reward.item.id, reward.amount)
        runConsumableGrant(reward.item, reward.amount, consumableStore)
        reward.copy(amount = granted)
    }
