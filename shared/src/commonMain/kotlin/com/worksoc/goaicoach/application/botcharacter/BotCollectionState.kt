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
    /**
     * 광고 조각 진행도(#11). [BotUnlockSource.AdShards] 캐릭터만 여기 쌓이며, 필요 수를 채우는
     * 순간 [claimedBots]로 옮겨가고 이 맵에서는 지워진다 — 이미 가진 캐릭터의 진행도는 뜻이 없다.
     *
     * ⚠️ 프리미엄의 **시간제 활성화**(`PremiumState.adGrantStartedAtMillis`)와 절대 섞지 않는다.
     * 그쪽은 1시간이 지나면 꺼지지만 이쪽은 다 모으면 **영구 소유**다 — 같은 "광고 시청"에서
     * 출발해도 결과의 수명이 정반대다(7장).
     */
    val adShards: Map<BotCharacterId, Int> = emptyMap(),
) {
    fun isClaimed(id: BotCharacterId): Boolean = id in claimedBots

    /** 이 캐릭터에 지금까지 모인 조각 수. 이미 획득했거나 조각 경로가 아니면 0. */
    fun shardsFor(id: BotCharacterId): Int = adShards[id] ?: 0

    fun withClaimed(id: BotCharacterId): BotCollectionState =
        if (isClaimed(id)) this else copy(claimedBots = claimedBots + id, adShards = adShards - id)

    /**
     * 광고 1회 시청분의 조각을 적립한다. 필요 수를 채우면 그 자리에서 **영구 획득**으로 넘어간다.
     *
     * 조각 경로가 아닌 캐릭터([BotUnlockSource.AdShards]가 아님)나 이미 획득한 캐릭터는 그대로
     * 돌려준다 — 호출부가 실수로 불러도 상태가 오염되지 않게 하는 방어다.
     */
    fun withAdShard(character: BotCharacter): BotCollectionState {
        val source = character.unlockSource as? BotUnlockSource.AdShards ?: return this
        if (isClaimed(character.id)) return this
        val next = shardsFor(character.id) + 1
        return if (next >= source.required) {
            withClaimed(character.id)
        } else {
            copy(adShards = adShards + (character.id to next))
        }
    }

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
