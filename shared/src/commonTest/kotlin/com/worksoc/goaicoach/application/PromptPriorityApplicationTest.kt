package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.prompt.decidePromptVisibility
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class PromptPriorityApplicationTest {
    @Test
    fun blocksAllPromptsUntilEngineStartupCompletesAndEngineIsIdle() {
        val beforeStartup = decidePromptVisibility(
            hasCompletedEngineStartup = false,
            isEngineBusy = false,
            hasPendingSavedSession = true,
            shouldShowResumePrompt = true,
            hasCacheOptimizationPrompt = true,
        )
        val whileBusy = decidePromptVisibility(
            hasCompletedEngineStartup = true,
            isEngineBusy = true,
            hasPendingSavedSession = true,
            shouldShowResumePrompt = true,
            hasCacheOptimizationPrompt = true,
        )

        assertFalse(beforeStartup.showResumePrompt)
        assertFalse(beforeStartup.showCacheOptimizationPrompt)
        assertFalse(whileBusy.showResumePrompt)
        assertFalse(whileBusy.showCacheOptimizationPrompt)
    }

    @Test
    fun resumePromptTakesPriorityOverCacheOptimizationPrompt() {
        val decision = decidePromptVisibility(
            hasCompletedEngineStartup = true,
            isEngineBusy = false,
            hasPendingSavedSession = true,
            shouldShowResumePrompt = true,
            hasCacheOptimizationPrompt = true,
        )

        assertTrue(decision.showResumePrompt)
        assertFalse(decision.showCacheOptimizationPrompt)
    }

    @Test
    fun cacheOptimizationPromptShowsWhenResumePromptIsNotVisible() {
        val decision = decidePromptVisibility(
            hasCompletedEngineStartup = true,
            isEngineBusy = false,
            hasPendingSavedSession = true,
            shouldShowResumePrompt = false,
            hasCacheOptimizationPrompt = true,
        )

        assertFalse(decision.showResumePrompt)
        assertTrue(decision.showCacheOptimizationPrompt)
    }
}
