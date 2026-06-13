package com.worksoc.goaicoach.application

internal data class PromptVisibilityDecision(
    val showResumePrompt: Boolean,
    val showCacheOptimizationPrompt: Boolean,
)

internal fun decidePromptVisibility(
    hasCompletedEngineStartup: Boolean,
    isEngineBusy: Boolean,
    hasPendingSavedSession: Boolean,
    shouldShowResumePrompt: Boolean,
    hasCacheOptimizationPrompt: Boolean,
): PromptVisibilityDecision {
    val canShowPrompt = hasCompletedEngineStartup && !isEngineBusy
    val showResume = canShowPrompt && hasPendingSavedSession && shouldShowResumePrompt
    return PromptVisibilityDecision(
        showResumePrompt = showResume,
        showCacheOptimizationPrompt = canShowPrompt && hasCacheOptimizationPrompt && !showResume,
    )
}
