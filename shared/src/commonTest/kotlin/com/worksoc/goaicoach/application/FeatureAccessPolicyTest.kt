package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.premium.AllowedVia
import com.worksoc.goaicoach.application.premium.FeatureAccess
import com.worksoc.goaicoach.application.premium.FeatureAccessPolicy
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.UnlockOption
import kotlin.test.assertEquals
import kotlin.test.Test

class FeatureAccessPolicyTest {
    @Test
    fun undoIsLockedWithClaimAdAndPurchaseOptionsWhenUnclaimedAndInactive() {
        val access = FeatureAccessPolicy.resolve(FeatureId.Undo, PremiumState(), nowMillis = 0L)

        assertEquals(
            FeatureAccess.Locked(setOf(UnlockOption.AdGrant, UnlockOption.Purchase, UnlockOption.Claim)),
            access,
        )
    }

    @Test
    fun evalTopMovesAndMoveReviewAreLockedWithoutClaimOptionWhenInactive() {
        val expected = FeatureAccess.Locked(setOf(UnlockOption.AdGrant, UnlockOption.Purchase))

        assertEquals(expected, FeatureAccessPolicy.resolve(FeatureId.Eval, PremiumState(), nowMillis = 0L))
        assertEquals(expected, FeatureAccessPolicy.resolve(FeatureId.TopMoves, PremiumState(), nowMillis = 0L))
        assertEquals(expected, FeatureAccessPolicy.resolve(FeatureId.MoveReview, PremiumState(), nowMillis = 0L))
    }

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
