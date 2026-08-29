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
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val purchasable: BotCharacter =
    BotCharacterCatalog.all.first { it.unlockSource is BotUnlockSource.Purchase }

private val shardCharacter: BotCharacter =
    BotCharacterCatalog.all.first { it.unlockSource is BotUnlockSource.AdShards }

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
        assertEquals(purchasable, matchOpponentCharacter(aiSetupFacing(purchasable)))
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
