package com.worksoc.goaicoach.application.botcharacter

import com.worksoc.goaicoach.shared.policy.PlayLevelGroup
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
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
    fun lockedCharacterFallsBackToTheFreeCharacterJustBelowIt() {
        // #10 이전 드롭다운으로 4단계(꼬북 3단)를 골라 둔 사용자가 업그레이드한 상황. 열 명이 된 뒤(#301)에는
        // 판다까지 떨어지지 않고 **세기의 차례에서 바로 아래의 무료 상대**(삼삼 3급 — 9단계)로 내려간다. 반상 1급은 잠겨 있다.
        val clamp = clampToOwnedBotCharacter(level(4), BotCollectionState())

        assertEquals(characterAt(4), clamp?.from)
        assertEquals(characterAt(9), clamp?.to)
        assertEquals(level(9), clamp?.playLevel)
    }

    @Test
    fun lockedCharacterFallsBackToTheHighestAvailableBelowIt() {
        // 출석으로 3단계(반상 1급)를 얻은 사용자가 5단계(천원 7단)를 가리키면 — 그 사이의 무료 화점(5단)이 가장 높다.
        val owned = BotCollectionState().withClaimed(characterAt(3).id)

        val clamp = clampToOwnedBotCharacter(level(5), owned)

        assertEquals(characterAt(5), clamp?.from)
        assertEquals(characterAt(10), clamp?.to)
    }

    @Test
    fun clampNeverRaisesTheDifficulty() {
        // 획득 집합은 연속이 아닐 수 있다. 3단계(반상 1급)를 갖고 2단계(돌뫼 9급)를 요청하면, "획득한 최고 단계"를
        // 그대로 쓰면 반상으로 **올라가** 버린다 — 낮춘다는 계약이 깨지므로 돌뫼 아래의 반수선(12급 — 7단계)으로 내려야 한다.
        // ⚠️ 「아래」는 단계 번호가 아니라 세기의 차례다 — 번호로 견주면 7단계가 2단계보다 「위」로 보인다(#301).
        val owned = BotCollectionState().withClaimed(characterAt(3).id)

        val clamp = clampToOwnedBotCharacter(level(2), owned)

        assertEquals(characterAt(2), clamp?.from)
        assertEquals(characterAt(7), clamp?.to)
    }

    @Test
    fun shardProgressAloneDoesNotCountAsOwnership() {
        // 조각을 모으는 중인 것과 획득한 것은 다르다 — 돌뫼(9급)는 아직 잠겨 그 아래의 반수선(12급)으로.
        val inProgress = BotCollectionState().withAdShard(characterAt(2))

        val clamp = clampToOwnedBotCharacter(level(2), inProgress)

        assertEquals(characterAt(7), clamp?.to)
    }

    @Test
    fun theWeakestCharacterIsFreeSoEveryLockedOneHasSomewhereToGo() {
        // 가장 약한 초롱이(6단계)와 판다(1단계)는 무료다 — 어떤 잠긴 상대도 갈 곳이 있다.
        for (tier in 1..PlayLevelGroup.FastBeginner.maxLevel) {
            val clamp = clampToOwnedBotCharacter(level(tier), BotCollectionState()) ?: continue
            assertEquals(true, BotCollectionState().isAvailable(clamp.to), "tier $tier → ${clamp.to.id.raw}")
        }
    }

    @Test
    fun withOnlyTheFirstFiveOfferedTheClampNeverLandsOnAHiddenCharacter() {
        // #301의 새 다섯을 가린 출시(사용자 2026-11-06 — #303 먼저): 보이는 것은 처음 다섯뿐이다. 잠긴 꼬북(3단)은 가린 무료 삼삼(3급)이 아니라
        // 예전처럼 판다로 내려간다.
        val firstFive = BotCharacterCatalog.firstFive
        val clamp = clampToOwnedBotCharacter(level(4), BotCollectionState(), roster = firstFive)

        assertEquals(characterAt(1), clamp?.to)
    }

    @Test
    fun aHiddenCharacterLeftInTheSavedSeatMovesToAnOfferedOne() {
        // 디버그 빌드에서 삼삼(3급 — 9단계, 무료)을 골라 둔 기기에 가린 출시를 덮어 깔면 — 무료여도 보이지 않으니 보이는 것 가운데로 내린다.
        // 삼삼 아래에서 보이는 것은 돌뫼(9급, 잠김)와 판다 — 판다로.
        val clamp = clampToOwnedBotCharacter(level(9), BotCollectionState(), roster = BotCharacterCatalog.firstFive)

        assertEquals(characterAt(9), clamp?.from)
        assertEquals(characterAt(1), clamp?.to)
        // 구독자도 내려간다 — 보이지 않는 캐릭터는 구독으로 열리는 캐릭터가 아니다. 구독자에게는 돌뫼(9급)가 열려 있어 그쪽이 가장 높다.
        assertEquals(characterAt(2), clampToOwnedBotCharacter(level(9), BotCollectionState(), subscriptionActive = true, roster = BotCharacterCatalog.firstFive)?.to)
    }

    @Test
    fun levelsWithoutACharacterAreLeftAlone() {
        // 초급/중급/고급 그룹은 카탈로그에 캐릭터가 없다 — 획득 개념 자체가 없으므로 건드리지
        // 않는다(코드가 보존돼 있고 대국장 로드맵에서 되살아날 수 있다).
        val hidden = PlayLevelSetting(group = PlayLevelGroup.Beginner, level = 3)

        assertNull(clampToOwnedBotCharacter(hidden, BotCollectionState()))
    }
}
