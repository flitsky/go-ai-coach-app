package com.worksoc.goaicoach.application.botcharacter

import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun level(tier: Int) = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = tier)

private fun characterAt(tier: Int): BotCharacter = BotCharacterCatalog.forPlayLevel(level(tier))!!

class BotCharacterLevelClampTest {

    @Test
    fun defaultCharacterIsNeverClamped() {
        // 1단계는 Default라 아무것도 획득하지 않아도 쓸 수 있다 — 건드릴 이유가 없다.
        assertNull(clampToOwnedBotCharacter(level(1), BotCollectionState()))
    }

    @Test
    fun ownedCharacterIsNeverClamped() {
        val owned = BotCollectionState().withClaimed(characterAt(4).id)

        assertNull(clampToOwnedBotCharacter(level(4), owned))
    }

    @Test
    fun lockedCharacterFallsBackToTheDefaultWhenNothingElseIsOwned() {
        // #10 이전 드롭다운으로 4단계를 골라 둔 사용자가 업그레이드한 상황.
        val clamp = clampToOwnedBotCharacter(level(4), BotCollectionState())

        assertEquals(characterAt(4), clamp?.from)
        assertEquals(characterAt(1), clamp?.to)
        assertEquals(level(1), clamp?.playLevel)
    }

    @Test
    fun lockedCharacterFallsBackToTheHighestOwnedBelowIt() {
        // 출석으로 3단계만 얻은 사용자 — 2단계는 아직인데도 3단계로 내려가야 한다.
        val owned = BotCollectionState().withClaimed(characterAt(3).id)

        val clamp = clampToOwnedBotCharacter(level(5), owned)

        assertEquals(characterAt(5), clamp?.from)
        assertEquals(characterAt(3), clamp?.to)
    }

    @Test
    fun clampNeverRaisesTheDifficulty() {
        // 획득 집합은 연속이 아닐 수 있다. 3단계를 갖고 2단계를 요청하면, "획득한 최고 단계"를
        // 그대로 쓰면 3단계로 **올라가** 버린다 — 낮춘다는 계약이 깨지므로 1단계로 내려야 한다.
        val owned = BotCollectionState().withClaimed(characterAt(3).id)

        val clamp = clampToOwnedBotCharacter(level(2), owned)

        assertEquals(characterAt(2), clamp?.from)
        assertEquals(characterAt(1), clamp?.to)
    }

    @Test
    fun shardProgressAloneDoesNotCountAsOwnership() {
        // 조각을 모으는 중인 것과 획득한 것은 다르다.
        val inProgress = BotCollectionState().withAdShard(characterAt(2))

        val clamp = clampToOwnedBotCharacter(level(2), inProgress)

        assertEquals(characterAt(1), clamp?.to)
    }

    @Test
    fun levelsWithoutACharacterAreLeftAlone() {
        // 초급/중급/고급 그룹은 카탈로그에 캐릭터가 없다 — 획득 개념 자체가 없으므로 건드리지
        // 않는다(코드가 보존돼 있고 대국장 로드맵에서 되살아날 수 있다).
        val hidden = PlayLevelSetting(group = PlayLevelGroup.Beginner, level = 3)

        assertNull(clampToOwnedBotCharacter(hidden, BotCollectionState()))
    }
}
