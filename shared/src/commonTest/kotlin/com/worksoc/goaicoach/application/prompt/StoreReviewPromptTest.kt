package com.worksoc.goaicoach.application.prompt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreReviewPromptTest {
    @Test
    fun firstCompletedGameDoesNotRequestReviewYet() {
        val eval = recordCompletedGameForStoreReview(StoreReviewProgress(), today = 20_000L)

        assertFalse(eval.shouldRequestReview)
        assertEquals(1, eval.nextProgress.completedGames)
        assertEquals(0, eval.nextProgress.promptCount)
        assertNull(eval.nextProgress.lastPromptDay)
    }

    @Test
    fun secondCompletedGameTriggersFirstReviewRequest() {
        val afterFirst = recordCompletedGameForStoreReview(StoreReviewProgress(), today = 20_000L).nextProgress
        val eval = recordCompletedGameForStoreReview(afterFirst, today = 20_000L)

        assertTrue(eval.shouldRequestReview)
        assertEquals(2, eval.nextProgress.completedGames)
        assertEquals(1, eval.nextProgress.promptCount)
        assertEquals(20_000L, eval.nextProgress.lastPromptDay)
    }

    @Test
    fun blocksRepeatRequestsDuringNinetyDayCooldown() {
        val asked = StoreReviewProgress(completedGames = 2, promptCount = 1, lastPromptDay = 20_000L)

        val duringCooldown = recordCompletedGameForStoreReview(asked, today = 20_000L + StoreReviewCooldownDays - 1L)
        assertFalse(duringCooldown.shouldRequestReview)
        assertEquals(3, duringCooldown.nextProgress.completedGames)
        assertEquals(1, duringCooldown.nextProgress.promptCount)
        assertEquals(20_000L, duringCooldown.nextProgress.lastPromptDay)

        val rewoundClock = recordCompletedGameForStoreReview(asked, today = 19_999L)
        assertFalse(rewoundClock.shouldRequestReview)
        assertEquals(1, rewoundClock.nextProgress.promptCount)
    }

    @Test
    fun allowsNextRequestAfterCooldownUntilMaxCapReached() {
        var progress = StoreReviewProgress(completedGames = 2, promptCount = 1, lastPromptDay = 20_000L)

        val secondPromptDay = 20_000L + StoreReviewCooldownDays
        val second = recordCompletedGameForStoreReview(progress, today = secondPromptDay)
        assertTrue(second.shouldRequestReview)
        assertEquals(2, second.nextProgress.promptCount)
        progress = second.nextProgress

        val thirdPromptDay = secondPromptDay + StoreReviewCooldownDays
        val third = recordCompletedGameForStoreReview(progress, today = thirdPromptDay)
        assertTrue(third.shouldRequestReview)
        assertEquals(3, third.nextProgress.promptCount)
        progress = third.nextProgress

        val fourthPromptDay = thirdPromptDay + StoreReviewCooldownDays
        val fourth = recordCompletedGameForStoreReview(progress, today = fourthPromptDay)
        assertFalse(fourth.shouldRequestReview)
        assertEquals(StoreReviewMaxPrompts, fourth.nextProgress.promptCount)
        assertEquals(thirdPromptDay, fourth.nextProgress.lastPromptDay)
    }
}
