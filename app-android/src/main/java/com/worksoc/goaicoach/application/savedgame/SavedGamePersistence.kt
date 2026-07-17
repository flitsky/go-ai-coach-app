package com.worksoc.goaicoach.application.savedgame

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.ScoreSnapshot

internal sealed class SavedGamePersistencePlan {
    data object Skip : SavedGamePersistencePlan()
    data object Clear : SavedGamePersistencePlan()
    data class Save(val snapshot: SavedGameSnapshot) : SavedGamePersistencePlan()
}

internal fun planSavedGamePersistence(
    savedSessionUiState: SavedSessionUiState,
    isGameEnded: Boolean,
    gameState: GameState,
    playerSetup: PlayerSetup,
    playLevel: PlayLevelSetting,
    topMovesEnabled: Boolean,
    scoreSnapshots: List<ScoreSnapshot>,
    nowMillis: Long,
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
    )

internal fun planSavedGamePersistence(
    hasCheckedSavedSession: Boolean,
    shouldShowResumePrompt: Boolean,
    isGameEnded: Boolean,
    gameState: GameState,
    playerSetup: PlayerSetup,
    playLevel: PlayLevelSetting,
    topMovesEnabled: Boolean,
    scoreSnapshots: List<ScoreSnapshot>,
    nowMillis: Long,
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
    )

    if (isGameEnded) {
        return SavedGamePersistencePlan.Clear
    }

    if (!snapshot.isResumable) {
        return SavedGamePersistencePlan.Skip
    }

    return SavedGamePersistencePlan.Save(snapshot)
}
