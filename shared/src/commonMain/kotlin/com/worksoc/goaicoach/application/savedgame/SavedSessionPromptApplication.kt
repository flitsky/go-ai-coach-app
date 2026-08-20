package com.worksoc.goaicoach.application.savedgame

data class SavedSessionPromptPlan(
    val pendingSavedSession: SavedGameSnapshot?,
    val shouldShowResumePrompt: Boolean,
    val hasCheckedSavedSession: Boolean,
)

data class SavedSessionUiState(
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
        // A snapshot carrying a final-score judgement is a finished game, not a game to
        // resume — GoCoachApp restores its result popup directly instead of asking the
        // user whether to continue playing.
        shouldShowResumePrompt = savedSession != null && savedSession.finalScoreJudgement == null,
        hasCheckedSavedSession = true,
    )

fun loadSavedSessionPromptPlan(
    store: SavedGameStorePort,
): SavedSessionPromptPlan =
    buildSavedSessionCheckPlan(store.load())

internal fun buildSavedSessionDismissPlan(): SavedSessionPromptPlan =
    SavedSessionPromptPlan(
        pendingSavedSession = null,
        shouldShowResumePrompt = false,
        hasCheckedSavedSession = true,
    )
