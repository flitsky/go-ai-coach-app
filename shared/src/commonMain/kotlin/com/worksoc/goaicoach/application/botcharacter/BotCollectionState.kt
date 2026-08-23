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
     * 지금 이 캐릭터를 골라서 대국할 수 있는지 판정한다. Phase 1에서는 **모든 획득 경로가 명시적
     * 획득을 요구**하므로 결과적으로 [isClaimed]와 같지만, 캐릭터를 받아 호출하는 형태를 유지한다 —
     * 나중에 월 구독처럼 개별 획득 없이 전체를 열어주는 소스가 붙으면 판정이 갈라지는 지점이
     * 여기이기 때문이다(7장의 범위 밖 항목).
     *
     * ⚠️ 아직 아무것도 획득하지 않은 사용자는 고를 수 있는 캐릭터가 **하나도 없다**(첫 캐릭터는
     * 출석 1일차 보상으로 들어온다 — 4.2절). 캐릭터 픽커를 만드는 쪽(#10)이 이 빈 상태를 반드시
     * 처리해야 한다.
     */
    fun isAvailable(character: BotCharacter): Boolean = isClaimed(character.id)
}
