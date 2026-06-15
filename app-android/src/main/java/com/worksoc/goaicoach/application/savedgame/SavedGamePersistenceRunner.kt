package com.worksoc.goaicoach.application.savedgame

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting

internal data class SavedGamePersistenceRequest(
    val savedSessionUiState: SavedSessionUiState,
    val isGameEnded: Boolean,
    val gameState: GameState,
    val playerSetup: PlayerSetup,
    val playLevel: PlayLevelSetting,
    val topMovesEnabled: Boolean,
    val nowMillis: Long,
)

internal fun buildSavedGamePersistencePlan(
    request: SavedGamePersistenceRequest,
): SavedGamePersistencePlan =
    planSavedGamePersistence(
        savedSessionUiState = request.savedSessionUiState,
        isGameEnded = request.isGameEnded,
        gameState = request.gameState,
        playerSetup = request.playerSetup,
        playLevel = request.playLevel,
        topMovesEnabled = request.topMovesEnabled,
        nowMillis = request.nowMillis,
    )

internal fun runSavedGamePersistence(
    request: SavedGamePersistenceRequest,
    store: SavedGameStorePort,
) {
    when (val plan = buildSavedGamePersistencePlan(request)) {
        SavedGamePersistencePlan.Skip -> Unit
        SavedGamePersistencePlan.Clear -> store.clear()
        is SavedGamePersistencePlan.Save -> store.save(plan.snapshot)
    }
}
