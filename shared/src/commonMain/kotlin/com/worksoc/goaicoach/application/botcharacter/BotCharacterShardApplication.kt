package com.worksoc.goaicoach.application.botcharacter

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
 * 5계층(App Service) — 광고 시청 1회를 조각 1개로 적립한다(백로그 #11, 킥오프 플랜 7장).
 *
 * [runBotCharacterUnlock]과 같은 이유로 저장소를 **read-modify-write** 한다 — 메모리에 들고
 * 있던 상태를 덮어쓰면 그 사이 출석 보상으로 들어온 캐릭터가 조용히 사라진다.
 *
 * ⚠️ 이 함수는 **광고를 보여주지 않는다.** 시청 성공(`AdRewardOutcome.RewardEarned`)을 확인한
 * 뒤에만 부르는 것이 호출부의 책임이다 — 프리미엄 쪽(`runPremiumAdGrantApplication`)이 시청
 * 결과를 상태 전이로 바꾸는 것과 같은 분업이다.
 *
 * @return 적립 후 상태와 이번에 획득까지 갔는지. 조각 경로가 아니거나 이미 가진 캐릭터면
 *   저장하지 않고 `null`을 돌려준다.
 */
fun runBotCharacterShardGrant(
    character: BotCharacter,
    store: BotCollectionStorePort,
): BotCharacterShardGrant? {
    val current = store.load()
    val next = current.withAdShard(character)
    if (next == current) return null
    store.save(next)
    return BotCharacterShardGrant(state = next, unlocked = next.isClaimed(character.id))
}
