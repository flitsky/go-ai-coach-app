package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.premium.AllowedVia
import com.worksoc.goaicoach.application.premium.FeatureAccess
import com.worksoc.goaicoach.application.premium.FeatureAccessPolicy
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.PremiumSource
import com.worksoc.goaicoach.application.premium.UnlockOption
import kotlin.test.assertEquals
import kotlin.test.Test

class FeatureAccessPolicyTest {
    /**
     * ⚠️ **이 테스트는 뒤집힌 것이다**(백로그 #66, 2026-09-03). 원래 이름은
     * `undoIsLockedWithClaimAdAndPurchaseOptionsWhenUnclaimedAndInactive`였고 무르기가
     * [UnlockOption.Claim]을 **받는다**고 못박고 있었다 — 그 초록이 결함을 계약으로 굳혀
     * *"테스트가 그렇다니 맞겠지"* 로 읽히며 두 스레드를 살아남게 했다.
     *
     * 무르기의 영구 해금은 **3일차 출석 보상**이다(`AttendanceRewardPolicy.UndoUnlimitedRewardTier`,
     * #55). 인게임 클레임 경로가 열려 있는 한 그 설계는 성립하지 않았다.
     */
    @Test
    fun undoIsLockedWithOnlyAdAndPurchaseOptionsWhenUnclaimedAndInactive() {
        val access = FeatureAccessPolicy.resolve(FeatureId.Undo, PremiumState(), nowMillis = 0L)

        assertEquals(
            FeatureAccess.Locked(setOf(UnlockOption.AdGrant, UnlockOption.Purchase)),
            access,
        )
    }

    /**
     * ⚠️ **지급 경로는 하나여야 한다** — 같은 엔타이틀먼트에 둘이 있으면 **쉬운 쪽이 이기고**
     * 어려운 쪽의 설계 의도가 조용히 사라진다(#66이 그 실례다).
     *
     * 기능별로 따로 두면 **기능이 하나 늘 때 새 기능만 빠뜨릴 수 있으므로** 전 기능을 훑는다.
     * 클레임 축을 되살릴 때는 이 테스트를 지우지 말고 **왜 그 기능만 예외인지**를 여기 적을 것.
     */
    @Test
    fun noFeatureOffersTheClaimUnlockOptionAnyMore() {
        for (featureId in FeatureId.entries) {
            val locked = FeatureAccessPolicy.resolve(featureId, PremiumState(), nowMillis = 0L)
                as? FeatureAccess.Locked ?: continue
            assertEquals(
                emptySet(),
                locked.unlockOptions.filter { option -> option == UnlockOption.Claim }.toSet(),
                "$featureId 가 아직 Claim으로 열린다 — 인게임 지급 팝업이 되살아나 3일차 출석 보상을 무력화한다(#66).",
            )
        }
    }

    @Test
    fun evalTopMovesAndMoveReviewAreLockedWithoutClaimOptionWhenInactive() {
        val expected = FeatureAccess.Locked(setOf(UnlockOption.AdGrant, UnlockOption.Purchase))

        assertEquals(expected, FeatureAccessPolicy.resolve(FeatureId.Eval, PremiumState(), nowMillis = 0L))
        assertEquals(expected, FeatureAccessPolicy.resolve(FeatureId.TopMoves, PremiumState(), nowMillis = 0L))
        assertEquals(expected, FeatureAccessPolicy.resolve(FeatureId.MoveReview, PremiumState(), nowMillis = 0L))
    }

    /**
     * **무르기가 열리는 길은 셋이고, 셋 다 살아 있어야 한다**(2026-09-03 사용자 정리, 백로그 #66).
     *
     * #66이 인게임 클레임 팝업을 없앤 뒤 *"무르기가 새로 잠긴 것 아니냐"* 가 나올 수 있는데,
     * 잠긴 것은 **지급 경로 하나**일 뿐이고 **사용 경로 셋은 그대로**라는 것이 이 테스트의 내용이다:
     * ⓐ 프리미엄이 지금 유효(구독/영구), ⓑ 광고 1시간 활성, ⓒ 3일차 출석 보상으로 영구 획득.
     *
     * ⚠️ **ⓐ·ⓑ는 `claimedFeatures`가 비어 있어도 성립해야 한다** — 1·2일차 사용자가 구독하거나
     * 광고를 보면 그 자리에서 써야 하기 때문이다. 그 보장은 [FeatureAccessPolicy.resolve]가 원장과
     * **별개로** 프리미엄 활성 여부를 본다는 것이지, 두 분기의 **순서**가 아니다 — 순서를 뒤집는
     * 변이로 확인했고 이 테스트는 그대로 통과한다(둘이 동시에 성립하는 경우가 없으므로 동치다).
     * 순서가 바꾸는 것은 **둘 다 참일 때 어느 [AllowedVia]가 이기는가**뿐이고, 그 값은 버튼 표시를
     * 가르는 데 쓰인다 — 무르기는 표시를 안 붙이므로 지금은 그 차이가 화면에 드러나지 않는다.
     */
    @Test
    fun undoWorksWhileAnyPremiumSourceIsActiveEvenWhenNeverClaimed() {
        val now = 1_000_000L

        val subscribed = FeatureAccessPolicy.resolve(
            FeatureId.Undo,
            PremiumState(source = PremiumSource.Purchase),
            nowMillis = now,
        )
        assertEquals(FeatureAccess.Allowed(AllowedVia.Purchase), subscribed, "구독/영구 프리미엄인데 무르기가 잠겼다.")

        val adGranted = FeatureAccessPolicy.resolve(
            FeatureId.Undo,
            PremiumState(source = PremiumSource.AdGrant, adGrantStartedAtMillis = now),
            nowMillis = now,
        )
        assertEquals(FeatureAccess.Allowed(AllowedVia.AdGrant), adGranted, "광고 1시간이 유효한데 무르기가 잠겼다.")
    }

    /**
     * 그리고 광고 1시간이 **지나면** 다시 잠겨야 한다 — 그렇지 않으면 광고 한 번이 영구 해금이 되어
     * 3일차 출석 보상이 또 무의미해진다(#66이 닫은 것과 같은 종류의 구멍이다).
     */
    @Test
    fun undoLocksAgainOnceTheAdGrantHasExpired() {
        val started = 1_000_000L
        val expired = started + PremiumState.AdGrantDurationMillis + 1L

        val access = FeatureAccessPolicy.resolve(
            FeatureId.Undo,
            PremiumState(source = PremiumSource.AdGrant, adGrantStartedAtMillis = started),
            nowMillis = expired,
        )

        assertEquals(FeatureAccess.Locked(setOf(UnlockOption.AdGrant, UnlockOption.Purchase)), access)
    }

    /**
     * ⚠️ **#66이 닫은 것은 '지급' 경로이고 '판정' 경로는 그대로다.** 이 테스트가 그 경계를 지킨다 —
     * 이미 무르기를 획득한 사용자(3일차 출석 보상, 또는 구버전 `isUndoClaimed` 마이그레이션으로
     * 들어온 사용자)는 계속 무료여야 한다. **정리하다가 이 분기를 함께 지우면 기존 보유자가 전원
     * 잠긴다** — 인게임 팝업이 사라진 지금 그 회귀는 **자력 복구가 불가능하다.**
     */
    @Test
    fun undoIsAllowedViaClaimedWhenInClaimedFeaturesButOtherwiseInactive() {
        val state = PremiumState(claimedFeatures = setOf(FeatureId.Undo))

        val access = FeatureAccessPolicy.resolve(FeatureId.Undo, state, nowMillis = 0L)

        assertEquals(FeatureAccess.Allowed(AllowedVia.Claimed), access)
    }

    @Test
    fun claimingUndoDoesNotUnlockOtherFeatures() {
        val state = PremiumState(claimedFeatures = setOf(FeatureId.Undo))

        val access = FeatureAccessPolicy.resolve(FeatureId.Eval, state, nowMillis = 0L)

        assertEquals(FeatureAccess.Locked(setOf(UnlockOption.AdGrant, UnlockOption.Purchase)), access)
    }

    @Test
    fun anyFeatureIsAllowedViaPurchaseWhenSourceIsPurchase() {
        val state = PremiumState.purchased()

        FeatureId.entries.forEach { featureId ->
            assertEquals(
                FeatureAccess.Allowed(AllowedVia.Purchase),
                FeatureAccessPolicy.resolve(featureId, state, nowMillis = 0L),
            )
        }
    }

    @Test
    fun anyFeatureIsAllowedViaAdGrantWhileWithinTheGrantWindow() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(nowMillis = grantedAt)

        FeatureId.entries.forEach { featureId ->
            assertEquals(
                FeatureAccess.Allowed(AllowedVia.AdGrant),
                FeatureAccessPolicy.resolve(featureId, state, nowMillis = grantedAt + 60_000L),
            )
        }
    }

    @Test
    fun undoFallsBackToClaimedWhenAdGrantExpiresButFeatureWasClaimed() {
        val grantedAt = 1_000_000L
        val state = PremiumState.adGranted(nowMillis = grantedAt).copy(claimedFeatures = setOf(FeatureId.Undo))
        val afterExpiry = grantedAt + PremiumState.AdGrantDurationMillis

        val access = FeatureAccessPolicy.resolve(FeatureId.Undo, state, nowMillis = afterExpiry)

        assertEquals(FeatureAccess.Allowed(AllowedVia.Claimed), access)
    }
}
