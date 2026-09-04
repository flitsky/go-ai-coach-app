package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.application.premium.PremiumSource
import com.worksoc.goaicoach.application.premium.PremiumState
import com.worksoc.goaicoach.application.premium.PremiumStateStorePort
import com.worksoc.goaicoach.application.premium.runPremiumFeatureClaim
import com.worksoc.goaicoach.application.premium.saveMergingClaimedFeatures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private class FakePremiumStateStore(initial: PremiumState = PremiumState()) : PremiumStateStorePort {
    var stored: PremiumState = initial
        private set
    var saveCount: Int = 0
        private set

    override fun save(state: PremiumState) {
        stored = state
        saveCount++
    }

    override fun load(): PremiumState = stored
}

class PremiumFeatureClaimApplicationTest {
    @Test
    fun claimAddsFeatureToLedgerAndPersistsIt() {
        val store = FakePremiumStateStore()

        val next = runPremiumFeatureClaim(FeatureId.Undo, store)

        assertNotNull(next)
        assertEquals(setOf(FeatureId.Undo), next.claimedFeatures)
        assertEquals(setOf(FeatureId.Undo), store.stored.claimedFeatures)
        assertEquals(1, store.saveCount)
    }

    @Test
    fun claimingAlreadyClaimedFeatureDoesNotSaveAgain() {
        val store = FakePremiumStateStore(PremiumState(claimedFeatures = setOf(FeatureId.Undo)))

        val next = runPremiumFeatureClaim(FeatureId.Undo, store)

        assertNull(next)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun claimKeepsExistingActivationSourceIntact() {
        val store = FakePremiumStateStore(PremiumState.purchased())

        val next = runPremiumFeatureClaim(FeatureId.Undo, store)

        assertNotNull(next)
        assertEquals(PremiumSource.Purchase, next.source)
        assertEquals(setOf(FeatureId.Undo), next.claimedFeatures)
    }

    @Test
    fun savingNewSourceMergesClaimsAlreadyInTheStore() {
        // 화면 밖(출석 보상 Claim)에서 먼저 들어온 클레임을 화면 쪽 저장이 지우면 안 된다.
        val store = FakePremiumStateStore(PremiumState(claimedFeatures = setOf(FeatureId.Undo)))

        val saved = store.saveMergingClaimedFeatures(PremiumState.purchased())

        assertEquals(PremiumSource.Purchase, saved.source)
        assertEquals(setOf(FeatureId.Undo), saved.claimedFeatures)
        assertEquals(setOf(FeatureId.Undo), store.stored.claimedFeatures)
    }

    @Test
    fun savingMergesBothSidesOfTheLedger() {
        val store = FakePremiumStateStore(PremiumState(claimedFeatures = setOf(FeatureId.Undo)))

        val saved = store.saveMergingClaimedFeatures(
            PremiumState(claimedFeatures = setOf(FeatureId.Eval)),
        )

        assertEquals(setOf(FeatureId.Undo, FeatureId.Eval), saved.claimedFeatures)
    }
}
