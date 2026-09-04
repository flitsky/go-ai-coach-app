package com.worksoc.goaicoach.application.botcharacter

import com.worksoc.goaicoach.application.premium.AdRewardOutcome

/**
 * 광고 1회 시청분의 조각을 적립한 결과(#11).
 *
 * [unlocked]가 참이면 이번 시청으로 캐릭터가 **영구 획득**됐다는 뜻이다 — 호출부는 그때만
 * "새 캐릭터를 얻었다"고 알리면 되고, 그 전까지는 진행도만 갱신하면 된다.
 */
data class BotCharacterShardGrant(
    val state: BotCollectionState,
    val unlocked: Boolean,
)

/**
 * 조각 광고 한 번의 결과 전부(백로그 #68) — 시청 결과와 **그 시청이 만든 변화**를 함께 나른다.
 *
 * ⚠️ **이 타입이 생긴 이유는 화면이 획득 여부를 자기 사본으로 다시 추론하고 있었기 때문이다.**
 * 예전 배선은 [runBotCharacterShardGrant]가 돌려준 [BotCharacterShardGrant.unlocked]를 버리고
 * 화면이 `직전 조각 수 + 1 >= 필요 수`로 판정했는데, 그 사본은 **출석 보상이 조각을 넣은 순간
 * 낡는다**(출석은 같은 저장소에 직접 쓴다). 지급은 read-modify-write라 정확한데 **알림만 틀리는**,
 * 로그에도 안 남는 종류의 어긋남이다.
 *
 * [shards]는 적립 **후**의 조각 수다 — 진행도 문구가 `직전 + 1`을 쓰지 않게 하려고 함께 싣는다.
 * 획득까지 갔다면 조각은 [BotCollectionState.withClaimed]가 지우므로 0이고, 그때는 [unlocked]가
 * 참이라 진행도 문구를 쓰지 않는다.
 */
data class BotShardAdOutcome(
    val ad: AdRewardOutcome,
    val unlocked: Boolean = false,
    val shards: Int = 0,
)

/**
 * 5계층(App Service) — 광고 시청 1회를 조각 1개로 적립한다(백로그 #11, 킥오프 플랜 7장).
 *
 * [runBotCharacterUnlock]과 같은 이유로 저장소를 **read-modify-write** 한다 — 메모리에 들고
 * 있던 상태를 덮어쓰면 그 사이 출석 보상으로 들어온 캐릭터가 조용히 사라진다.
 *
 * ⚠️ 이 함수는 **광고를 보여주지 않는다.** 시청 성공(`AdRewardOutcome.RewardEarned`)을 확인한
 * 뒤에만 부르는 것이 호출부의 책임이다 — 프리미엄 쪽(`runPremiumAdGrantApplication`)이 시청
 * 결과를 상태 전이로 바꾸는 것과 같은 분업이다.
 *
 * [amount]는 한 번에 적립할 조각 수다 — 광고 시청은 1개씩이지만 출석 장기 보상은 여러 개를
 * 한꺼번에 줄 수 있다.
 *
 * @return 적립 후 상태와 이번에 획득까지 갔는지. 조각 경로가 아니거나 이미 가진 캐릭터면
 *   저장하지 않고 `null`을 돌려준다 — 호출부는 이 `null`로 "알릴 것이 없다"를 판정한다.
 */
fun runBotCharacterShardGrant(
    character: BotCharacter,
    store: BotCollectionStorePort,
    amount: Int = 1,
): BotCharacterShardGrant? {
    val current = store.load()
    val next = current.withAdShards(character, amount)
    if (next == current) return null
    store.save(next)
    return BotCharacterShardGrant(state = next, unlocked = next.isClaimed(character.id))
}
