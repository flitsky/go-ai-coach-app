package com.worksoc.goaicoach.application.botcharacter

/**
 * 5계층(App Service) — 봇 캐릭터 한 종을 영구 획득 처리한다. 획득 경로(출석 보상 #13, 광고 시청
 * #11)와 무관하게 이 함수 하나를 거치게 해, 수집 기록이 남는 규칙이 두 벌로 갈라지지 않게 한다.
 *
 * `runPremiumFeatureClaim`과 같은 이유로 저장소를 **read-modify-write** 한다 — 메모리에 들고
 * 있던 상태를 덮어쓰면 그 사이 다른 경로로 획득한 캐릭터가 조용히 사라진다.
 *
 * @return 이번 호출로 새로 획득했다면 저장된 새 상태, 이미 갖고 있어 저장이 필요 없었다면 `null`.
 */
fun runBotCharacterUnlock(id: BotCharacterId, store: BotCollectionStorePort): BotCollectionState? {
    val current = store.load()
    if (current.isClaimed(id)) return null
    val next = current.withClaimed(id)
    store.save(next)
    return next
}
