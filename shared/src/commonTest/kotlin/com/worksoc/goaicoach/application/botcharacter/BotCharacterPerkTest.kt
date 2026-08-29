package com.worksoc.goaicoach.application.botcharacter

import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableSpendDecision
import com.worksoc.goaicoach.application.consumable.decideConsumableSpend
import com.worksoc.goaicoach.application.premium.AllowedVia
import com.worksoc.goaicoach.application.premium.FeatureAccess
import com.worksoc.goaicoach.application.premium.FeatureAccessPolicy
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 유료 캐릭터를 **직접 만들어 쓴다.** 2026-08-29에 5단계가 28일차 출석으로 옮겨가면서 카탈로그에
 * `Purchase` 캐릭터가 하나도 남지 않았는데, 특전 규칙 자체는 로스터 구성과 무관하게 성립해야
 * 한다 — 카탈로그에서 뽑아 쓰면 로스터가 바뀔 때마다 규칙 테스트가 같이 무너진다.
 */
private val purchasable: BotCharacter = BotCharacter(
    id = BotCharacterId("test_purchasable"),
    name = "테스트 유료 캐릭터",
    description = "특전 규칙 검증용",
    linkedPlayLevel = PlayLevelGroup.FastBeginner,
    tierWithinGroup = 5,
    unlockSource = BotUnlockSource.Purchase,
)

private val shardCharacter: BotCharacter =
    BotCharacterCatalog.all.first { it.unlockSource is BotUnlockSource.AdShards }

/** 카탈로그의 실제 캐릭터 — 상대 판정([matchOpponentCharacter])은 카탈로그를 거치므로 필요하다. */
private val rosterCharacter: BotCharacter = BotCharacterCatalog.all.first()

private fun aiSetupFacing(character: BotCharacter) = PlayerSetup(
    black = SidePlayerSetup(controller = SeatController.Human),
    white = SidePlayerSetup(controller = SeatController.Ai, playLevel = character.toPlayLevelSetting()!!),
)

class BotCharacterPerkTest {

    @Test
    fun perkNeedsBothPurchaseSourceAndOwnership() {
        assertFalse(isBotCharacterPerkActive(purchasable, BotCollectionState()), "구매 전에는 특전이 없다")
        assertTrue(isBotCharacterPerkActive(purchasable, BotCollectionState().withClaimed(purchasable.id)))
    }

    @Test
    fun ownedButNotPurchasedCharactersGiveNoPerk() {
        // 특전은 구매 유도 장치다 — 광고나 출석으로 얻은 캐릭터에는 붙지 않는다.
        val owned = BotCollectionState().withClaimed(shardCharacter.id)

        assertFalse(isBotCharacterPerkActive(shardCharacter, owned))
    }

    @Test
    fun humanOnlyMatchesHaveNoOpponentCharacter() {
        val humanOnly = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Human),
        )

        assertNull(matchOpponentCharacter(humanOnly))
    }

    @Test
    fun theOpponentIsTheAiSeatsCharacter() {
        assertEquals(rosterCharacter, matchOpponentCharacter(aiSetupFacing(rosterCharacter)))
    }

    @Test
    fun theCatalogCurrentlyHasNoPurchasableCharacterSoThePerkLiesDormant() {
        // 2026-08-29: 5단계가 28일차 출석으로 옮겨가면서 유료 전용 캐릭터가 사라졌다. 특전 배선은
        // 지우지 않고 두되(#18), 지금은 어떤 상대에게도 성립하지 않는다는 사실을 고정한다 —
        // 유료 상품을 다시 열 때 이 테스트가 먼저 깨져서 "특전이 살아났다"를 알려 준다.
        assertTrue(BotCharacterCatalog.all.none { it.unlockSource is BotUnlockSource.Purchase })

        val everythingOwned = BotCharacterCatalog.all
            .fold(BotCollectionState()) { state, character -> state.withClaimed(character.id) }
        assertTrue(
            BotCharacterCatalog.all.none { isBotCharacterPerkActive(it, everythingOwned) },
            "로스터 전체를 가져도 특전이 켜지면 안 된다",
        )
    }

    @Test
    fun perkOpensEvalAndTopMovesWithoutPremium() {
        val access = FeatureAccessPolicy.resolve(
            featureId = FeatureId.Eval,
            state = PremiumState(),
            nowMillis = 0L,
            characterPerkActive = true,
        )

        assertEquals(FeatureAccess.Allowed(AllowedVia.CharacterPerk), access)
    }

    @Test
    fun perkDoesNotOpenFeaturesOutsideTheConfirmedTwo() {
        // 사용자 확정 문구가 형세 보기와 추천 수만 지목했다 — 무르기까지 열리면 안 된다.
        val access = FeatureAccessPolicy.resolve(
            featureId = FeatureId.Undo,
            state = PremiumState(),
            nowMillis = 0L,
            characterPerkActive = true,
        )

        assertIs<FeatureAccess.Locked>(access)
    }

    @Test
    fun theDefaultKeepsEveryExistingCallSiteUnchanged() {
        // 대국 컨텍스트를 모르는 호출부(설정 화면·업셀 팝업)는 인자를 넘기지 않는다.
        assertIs<FeatureAccess.Locked>(
            FeatureAccessPolicy.resolve(FeatureId.Eval, PremiumState(), nowMillis = 0L),
        )
    }

    @Test
    fun perkNeverSpendsAOneShotTicket() {
        // 8.3절 3번: 특전은 차감이 없다. 이걸 놓치면 산 캐릭터와 두는 내내 표가 조용히 줄어든다.
        val stocked = ConsumableInventory().withGranted(ConsumableCatalog.EvalOnce.id, 3)

        val decision = decideConsumableSpend(
            item = ConsumableCatalog.EvalOnce,
            inventory = stocked,
            premiumState = PremiumState(),
            nowMillis = 0L,
            characterPerkActive = true,
        )

        assertEquals(ConsumableSpendDecision.AllowedWithoutSpending(AllowedVia.CharacterPerk), decision)
        assertEquals(3, stocked.countOf(ConsumableCatalog.EvalOnce.id), "재고가 그대로여야 한다")
    }

    @Test
    fun withoutThePerkTheTicketIsStillSpent() {
        val stocked = ConsumableInventory().withGranted(ConsumableCatalog.EvalOnce.id, 3)

        val decision = decideConsumableSpend(
            item = ConsumableCatalog.EvalOnce,
            inventory = stocked,
            premiumState = PremiumState(),
            nowMillis = 0L,
            characterPerkActive = false,
        )

        assertIs<ConsumableSpendDecision.Spent>(decision)
        assertEquals(2, decision.remaining)
    }
}
