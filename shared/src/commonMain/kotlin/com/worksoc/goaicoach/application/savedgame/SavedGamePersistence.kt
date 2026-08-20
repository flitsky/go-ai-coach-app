package com.worksoc.goaicoach.application.savedgame

import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.ScoreSnapshot

sealed class SavedGamePersistencePlan {
    data object Skip : SavedGamePersistencePlan()
    data object Clear : SavedGamePersistencePlan()
    data class Save(val snapshot: SavedGameSnapshot) : SavedGamePersistencePlan()
}

fun planSavedGamePersistence(
    savedSessionUiState: SavedSessionUiState,
    isGameEnded: Boolean,
    gameState: GameState,
    playerSetup: PlayerSetup,
    playLevel: PlayLevelSetting,
    topMovesEnabled: Boolean,
    scoreSnapshots: List<ScoreSnapshot>,
    nowMillis: Long,
    finalScoreJudgement: FinalScoreJudgement? = null,
): SavedGamePersistencePlan =
    planSavedGamePersistence(
        hasCheckedSavedSession = savedSessionUiState.hasCheckedSavedSession,
        shouldShowResumePrompt = savedSessionUiState.shouldShowResumePrompt,
        isGameEnded = isGameEnded,
        gameState = gameState,
        playerSetup = playerSetup,
        playLevel = playLevel,
        topMovesEnabled = topMovesEnabled,
        scoreSnapshots = scoreSnapshots,
        nowMillis = nowMillis,
        finalScoreJudgement = finalScoreJudgement,
    )

fun planSavedGamePersistence(
    hasCheckedSavedSession: Boolean,
    shouldShowResumePrompt: Boolean,
    isGameEnded: Boolean,
    gameState: GameState,
    playerSetup: PlayerSetup,
    playLevel: PlayLevelSetting,
    topMovesEnabled: Boolean,
    scoreSnapshots: List<ScoreSnapshot>,
    nowMillis: Long,
    finalScoreJudgement: FinalScoreJudgement? = null,
): SavedGamePersistencePlan {
    if (!hasCheckedSavedSession || shouldShowResumePrompt) {
        return SavedGamePersistencePlan.Skip
    }

    val snapshot = SavedGameSnapshot(
        gameState = gameState,
        playerSetup = playerSetup,
        playLevel = playLevel,
        topMovesEnabled = topMovesEnabled,
        savedAtMillis = nowMillis,
        scoreSnapshots = scoreSnapshots,
        finalScoreJudgement = finalScoreJudgement,
    )

    if (isGameEnded) {
        // A finished game has no resumable moves left, but the final-judgement popup
        // (screenState.finalScoreJudgement) still needs to survive the OS killing this
        // process while backgrounded — see SavedGameStore.load()/save() relaxing the
        // resumable-only gate for snapshots carrying a judgement.
        return if (finalScoreJudgement != null) {
            SavedGamePersistencePlan.Save(snapshot)
        } else {
            SavedGamePersistencePlan.Clear
        }
    }

    if (!snapshot.isResumable) {
        return SavedGamePersistencePlan.Skip
    }

    return SavedGamePersistencePlan.Save(snapshot)
}
