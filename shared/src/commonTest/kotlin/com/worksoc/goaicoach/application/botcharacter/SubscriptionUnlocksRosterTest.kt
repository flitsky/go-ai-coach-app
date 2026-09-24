package com.worksoc.goaicoach.application.botcharacter

import com.worksoc.goaicoach.application.premium.state.PremiumSource
import com.worksoc.goaicoach.application.premium.state.PremiumState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 구독이 로스터 전체를 여는 축(백로그 #157, `FEATURE_ACCESS_PRINCIPLES.md` 8.1의 구독 특전 ⓐ).
 *
 * ⚠️ 여기서 지키는 셋은 하나같이 **컴파일도 되고 다른 테스트도 초록인 채** 조용히 어긋난다.
 */
class SubscriptionUnlocksRosterTest {

    private val locked = BotCharacterCatalog.all.first { it.unlockSource != BotUnlockSource.Default }
    private val default = BotCharacterCatalog.all.first { it.unlockSource == BotUnlockSource.Default }

    @Test
    fun aSubscriberCanPickEveryCharacterWithoutClaimingAny() {
        val empty = BotCollectionState()

        assertTrue(
            BotCharacterCatalog.all.all { empty.isAvailable(it, subscriptionActive = true) },
            "구독 중인데 잠긴 캐릭터가 남아 있다 — 8.1이 약속한 특전 ⓐ가 배송되지 않는다.",
        )
    }

    @Test
    fun withoutASubscriptionNothingChanges() {
        val empty = BotCollectionState()

        assertTrue(empty.isAvailable(default))
        assertFalse(empty.isAvailable(locked), "구독이 없는데 잠긴 캐릭터가 열렸다.")
    }

    /**
     * ⚠️ **광고 1시간으로 로스터가 열리면 안 된다**(`FEATURE_ACCESS_PRINCIPLES.md` 8.2).
     *
     * 8.2가 중간안 *"보유한 모두에게 무제한"* 을 폐기한 이유가 정확히 이것이다 — 광고 몇 번이
     * 월 구독과 같은 것을 주면 구독을 살 이유가 사라진다.
     */
    @Test
    fun anAdGrantIsNotASubscriptionSoItNeverOpensTheRoster() {
        val adGranted = PremiumState.adGranted(nowMillis = 1_000L)

        assertTrue(adGranted.isActive(nowMillis = 1_000L), "광고 부여가 인게임 기능은 열어야 한다")
        assertFalse(
            adGranted.isSubscriptionActive(),
            "광고 1시간이 구독으로 취급된다 — 광고 몇 번이 로스터 전체를 연다(8.2가 폐기한 구멍).",
        )
    }

    @Test
    fun theDurableEntitlementIsWhatCountsAsASubscription() {
        assertTrue(PremiumState(source = PremiumSource.Purchase).isSubscriptionActive())
        assertFalse(PremiumState(source = PremiumSource.None).isSubscriptionActive())
    }

    /**
     * ⚠️ **구독으로 열린 것은 저장소에 남지 않는다** — 구독은 **살아 있는 상태**이지 획득 이력이
     * 아니다. `claimedBots`에 넣으면 **해지 뒤에도 캐릭터가 남아** 구독의 값이 새어 나간다(#157).
     */
    @Test
    fun theSubscriptionNeverWritesIntoTheClaimLedger() {
        val empty = BotCollectionState()

        // 판정만 바뀔 뿐, 상태 자체는 그대로여야 한다.
        assertTrue(empty.isAvailable(locked, subscriptionActive = true))
        assertFalse(
            empty.isClaimed(locked.id),
            "구독으로 열린 캐릭터가 획득 원장에 들어갔다 — 해지해도 남는다(#157).",
        )
        assertEquals(BotCollectionState(), empty, "판정이 컬렉션 상태를 바꿨다 — 순수해야 한다.")
    }

    /**
     * ⚠️ 해지하면 **도로 잠긴다.** 픽커가 *"구독으로 이용 중"* 을 붙이는 근거가 이 판정이고,
     * 표시가 없으면 해지 순간 말없이 잠겨 "내 캐릭터가 사라졌다"로 읽힌다.
     */
    @Test
    fun aCharacterOpenedOnlyByTheSubscriptionIsFlaggedAsSuch() {
        val empty = BotCollectionState()

        assertTrue(empty.isAvailableOnlyViaSubscription(locked, subscriptionActive = true))
        assertFalse(
            empty.isAvailableOnlyViaSubscription(default, subscriptionActive = true),
            "기본 제공 캐릭터까지 '구독으로 이용 중'이라고 말한다 — 해지해도 남는 자리다.",
        )
        assertFalse(
            BotCollectionState().withClaimed(locked.id)
                .isAvailableOnlyViaSubscription(locked, subscriptionActive = true),
            "이미 획득한 캐릭터를 '구독 덕'이라고 말한다 — 해지해도 남는데 빼앗길 것처럼 읽힌다.",
        )
        assertFalse(empty.isAvailableOnlyViaSubscription(locked, subscriptionActive = false))
    }

    /** ⚠️ 구독자는 강등되지 않는다 — 로스터가 다 열려 있는데 낮추면 사고도 상대가 약해진다. */
    @Test
    fun aSubscriberIsNeverClampedDownToAWeakerOpponent() {
        val top = BotCharacterCatalog.all
            .filter { it.unlockSource != BotUnlockSource.Default }
            .maxByOrNull { it.tierWithinGroup ?: 0 }!!
        val level = top.toPlayLevelSetting()!!

        assertTrue(
            clampToOwnedBotCharacter(level, BotCollectionState()) != null,
            "전제 확인: 구독이 없으면 강등된다",
        )
        assertEquals(
            null,
            clampToOwnedBotCharacter(level, BotCollectionState(), subscriptionActive = true),
            "구독자가 강등된다 — 사고도 상대가 약해진다(#157).",
        )
    }
}
