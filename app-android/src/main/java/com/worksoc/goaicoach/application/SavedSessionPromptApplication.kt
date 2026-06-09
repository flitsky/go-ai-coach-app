package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.persistence.SavedGameSnapshot

internal data class SavedSessionPromptPlan(
    val pendingSavedSession: SavedGameSnapshot?,
    val shouldShowResumePrompt: Boolean,
    val hasCheckedSavedSession: Boolean,
)

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
