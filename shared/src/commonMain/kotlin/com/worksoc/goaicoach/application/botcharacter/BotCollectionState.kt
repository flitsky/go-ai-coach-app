package com.worksoc.goaicoach.application.botcharacter

/**
 * 6계층(Session & Continuity) — 봇 캐릭터 수집 상태.
 *
 * [com.worksoc.goaicoach.application.premium.PremiumState.claimedFeatures]와 자료구조는 닮았지만
 * 도메인이 다르다(기능 토글 원장 vs 캐릭터 수집)는 7장 지침에 따라 **별도 타입 + 별도 Port**로
 * 분리했다 — 킥오프 플랜 3장의 "신규 3개 기능은 서로 다른 Port로 분리한다"와도 같은 결론이다.
 *
 * [claimedBots]에는 카탈로그([BotCharacterCatalog])에 없는 id가 들어 있을 수도 있다(더 높은 앱
 * 버전에서 수집한 뒤 다운그레이드한 경우) — 그런 id도 버리지 않고 그대로 보존한다.
 */
data class BotCollectionState(
    val claimedBots: Set<BotCharacterId> = emptySet(),
) {
    fun isClaimed(id: BotCharacterId): Boolean = id in claimedBots

    fun withClaimed(id: BotCharacterId): BotCollectionState =
        if (isClaimed(id)) this else copy(claimedBots = claimedBots + id)

    /**
     * 지금 이 캐릭터를 골라서 대국할 수 있는지 판정한다. **[isClaimed]와 갈라진다** —
     * [BotUnlockSource.Default]인 캐릭터는 획득 기록 없이도 쓸 수 있기 때문이다(#8 KDoc이
     * 예견했던 분기 지점이 #16에서 실제로 생겼다).
     *
     * 그 덕에 **아무것도 획득하지 않은 사용자도 고를 수 있는 캐릭터가 최소 하나 있다**(1단계
     * 첫돌이). #8 시점에 있던 "빈 상태" 부담이 사라졌으므로, 캐릭터 픽커(#10)가 다뤄야 할 것은
     * 빈 목록이 아니라 **잠긴 4종을 각각 어떤 사유로 잠겼는지 구분해 보여주는 것**이다 —
     * 획득 경로가 출석/광고 조각/유료 셋으로 갈리기 때문이다(7장 표).
     *
     * 월 구독처럼 개별 획득 없이 전체를 여는 소스가 나중에 붙으면 이 함수에 분기를 더한다.
     */
    fun isAvailable(character: BotCharacter): Boolean =
        character.unlockSource == BotUnlockSource.Default || isClaimed(character.id)
}
