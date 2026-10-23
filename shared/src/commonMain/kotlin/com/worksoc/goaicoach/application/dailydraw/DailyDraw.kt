package com.worksoc.goaicoach.application.dailydraw

import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import com.worksoc.goaicoach.application.botcharacter.runBotCharacterShardGrant
import com.worksoc.goaicoach.application.botcharacter.runBotCharacterUnlock
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableStorePort
import com.worksoc.goaicoach.application.consumable.runConsumableGrant
import kotlin.random.Random

/*
 * 실험실의 「1일 1회 뽑기」(백로그 #283, 사용자 2026-10-23) — 규칙은 이 파일 한 곳이다.
 *
 * 사용자가 정한 것:
 * - **광고 1편을 보면 한 번** 뽑는다. 구독자는 광고 없이 한 번. 뽑으면 쿨타임이 돈다.
 * - 상품 넷과 확률: 캐릭터 조각 1개 30% · 형세 보기 · 추천 수 1회권 10장씩 30% · 광고 스킵권 1장 30% · **캐릭터 완전판 10%**.
 * - 조각과 완전판은 **아직 갖지 않은 조각 길 캐릭터(2 · 4단계) 중 가장 낮은 등급**에게 간다. 출석 캐릭터(3 · 5단계)는 출석으로만 열린다 —
 *   7 · 28일차의 목표가 살아 있고, 이미 가진 캐릭터가 출석 회차에 걸리는 「유령 출석 보상」(`FEATURE_ACCESS_PRINCIPLES.md` §8.4)이 잠든 채로 남는다.
 *
 * 스레드가 정한 것:
 * - 쿨타임은 **뽑은 때부터 24시간**([DailyDrawCooldownMillis]) — 출석의 UTC 하루로 세면 한국에서는 오전 9시에 풀려 「쿨타임」과 다르게 읽힌다.
 * - 줄 수 없는 상품은 **빼고 나머지끼리 다시 나눈다**: 조각 길 캐릭터를 다 가졌으면 조각 · 완전판이, 광고 스킵권이 보유 상한(9)이면 스킵권이 빠진다
 *   (꽝이 없다). 1회권은 늘 남는다 — 99장 상한이면 넘친 만큼 버려질 뿐이다(출석과 같은 처리).
 * - 시계를 되감아 마지막으로 뽑은 때가 미래가 되면 막지 않는다 — 오프라인 앱이라 시계를 믿을 수밖에 없고(함정 12), 막으면 정직한 사용자가 갇힌다.
 */

/** 뽑은 때부터 다시 뽑을 수 있을 때까지. */
const val DailyDrawCooldownMillis: Long = 24L * 60 * 60 * 1000

/** 「1회권」 상품이 주는 장수 — 형세 보기와 추천 수에 **각각**. */
const val DailyDrawTicketsEach: Int = 10

/** 상품의 종류와 기본 무게(백분율). 무게의 합이 100이라 화면에 그대로 확률로 적는다. */
enum class DailyDrawPrizeKind(val weight: Int) {
    CharacterShard(30),
    Tickets(30),
    AdSkipTicket(30),
    Character(10),
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
    /** 조각 · 완전판이 간 캐릭터. 다른 상품이면 `null`. */
    val character: BotCharacter? = null,
    /** 이번 뽑기로 캐릭터를 **영구 획득**했는가 — 완전판이거나, 조각이 필요 수를 채웠다. */
    val unlocked: Boolean = false,
    /** 조각을 받은 뒤 그 캐릭터에 모인 조각 수(획득까지 갔으면 0). */
    val shards: Int = 0,
)

/** 조각 · 완전판이 갈 캐릭터 — 아직 갖지 않은 조각 길 캐릭터 중 가장 낮은 등급. 다 가졌으면 `null`. */
fun dailyDrawCharacterTarget(collection: BotCollectionState): BotCharacter? =
    BotCharacterCatalog.all
        .filter { character -> character.unlockSource is BotUnlockSource.AdShards && !collection.isClaimed(character.id) }
        .minByOrNull { character -> character.tierWithinGroup ?: Int.MAX_VALUE }

/** 지금 나올 수 있는 상품과 그 무게 — 줄 수 없는 상품은 빠진다. 화면이 확률을 적을 때도 이것을 쓴다. */
fun dailyDrawOdds(inventory: ConsumableInventory, collection: BotCollectionState): Map<DailyDrawPrizeKind, Int> {
    val hasTarget = dailyDrawCharacterTarget(collection) != null
    val skipFull = inventory.isAtMaxStock(ConsumableCatalog.PremiumOnce.id)
    return DailyDrawPrizeKind.entries
        .filter { kind ->
            when (kind) {
                DailyDrawPrizeKind.CharacterShard, DailyDrawPrizeKind.Character -> hasTarget
                DailyDrawPrizeKind.AdSkipTicket -> !skipFull
                DailyDrawPrizeKind.Tickets -> true
            }
        }
        .associateWith { kind -> kind.weight }
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
    val odds = dailyDrawOdds(consumableStore.load(), collectionStore.load())
    val kind = pickDailyDrawPrize(odds, random.nextInt(odds.values.sum()))
    val target = dailyDrawCharacterTarget(collectionStore.load())
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
            val character = requireNotNull(target) { "조각 상품은 대상 캐릭터가 있을 때만 나온다(dailyDrawOdds)." }
            val grant = runBotCharacterShardGrant(character, collectionStore)
            DailyDrawResult(kind, character, unlocked = grant?.unlocked == true, shards = grant?.state?.shardsFor(character.id) ?: 0)
        }
        DailyDrawPrizeKind.Character -> {
            val character = requireNotNull(target) { "완전판 상품은 대상 캐릭터가 있을 때만 나온다(dailyDrawOdds)." }
            runBotCharacterUnlock(character.id, collectionStore)
            DailyDrawResult(kind, character, unlocked = true)
        }
    }
    drawStore.markDrawn(nowMillis)
    return result
}
