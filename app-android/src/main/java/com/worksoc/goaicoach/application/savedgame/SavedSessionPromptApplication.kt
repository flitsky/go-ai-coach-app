package com.worksoc.goaicoach.application.savedgame

internal data class SavedSessionPromptPlan(
    val pendingSavedSession: SavedGameSnapshot?,
    val shouldShowResumePrompt: Boolean,
    val hasCheckedSavedSession: Boolean,
)

internal data class SavedSessionUiState(
    val pendingSavedSession: SavedGameSnapshot? = null,
    val shouldShowResumePrompt: Boolean = false,
    val hasCheckedSavedSession: Boolean = false,
) {
    fun applyPrompt(plan: SavedSessionPromptPlan): SavedSessionUiState =
        copy(
            pendingSavedSession = plan.pendingSavedSession,
            shouldShowResumePrompt = plan.shouldShowResumePrompt,
            hasCheckedSavedSession = plan.hasCheckedSavedSession,
        )

    fun dismiss(): SavedSessionUiState =
        applyPrompt(buildSavedSessionDismissPlan())
}

internal fun buildSavedSessionCheckPlan(
    savedSession: SavedGameSnapshot?,
): SavedSessionPromptPlan =
    SavedSessionPromptPlan(
        pendingSavedSession = savedSession,
        shouldShowResumePrompt = savedSession != null,
        hasCheckedSavedSession = true,
    )

internal fun buildSavedSessionDismissPlan(): SavedSessionPromptPlan =
    SavedSessionPromptPlan(
        pendingSavedSession = null,
        shouldShowResumePrompt = false,
        hasCheckedSavedSession = true,
    )
