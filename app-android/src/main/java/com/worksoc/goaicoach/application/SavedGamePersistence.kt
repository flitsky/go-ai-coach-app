package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting

internal sealed class SavedGamePersistencePlan {
    data object Skip : SavedGamePersistencePlan()
    data object Clear : SavedGamePersistencePlan()
    data class Save(val snapshot: SavedGameSnapshot) : SavedGamePersistencePlan()
}

internal fun planSavedGamePersistence(
    hasCheckedSavedSession: Boolean,
    shouldShowResumePrompt: Boolean,
    isGameEnded: Boolean,
    gameState: GameState,
    playerSetup: PlayerSetup,
    playLevel: PlayLevelSetting,
    topMovesEnabled: Boolean,
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
    )

    return if (isGameEnded || !snapshot.isResumable) {
        SavedGamePersistencePlan.Clear
    } else {
        SavedGamePersistencePlan.Save(snapshot)
    }
}
