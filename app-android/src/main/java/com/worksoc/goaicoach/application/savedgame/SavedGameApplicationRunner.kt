package com.worksoc.goaicoach.application.savedgame

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings

internal data class SavedSessionPromptRunRequest(
    val store: SavedGameStorePort,
    val applyPrompt: (SavedSessionPromptPlan) -> Unit,
)

internal fun runSavedSessionPromptApplication(
    request: SavedSessionPromptRunRequest,
) {
    request.applyPrompt(loadSavedSessionPromptPlan(request.store))
}

internal data class SavedGamePersistenceRunRequest(
    val savedSessionUiState: SavedSessionUiState,
    val isGameEnded: Boolean,
    val gameState: GameState,
    val playerSetup: PlayerSetup,
    val playLevel: PlayLevelSetting,
    val topMovesEnabled: Boolean,
    val nowMillis: Long,
    val store: SavedGameStorePort,
)

internal fun runSavedGamePersistenceApplication(
    request: SavedGamePersistenceRunRequest,
) {
    runSavedGamePersistence(
        request = SavedGamePersistenceRequest(
            savedSessionUiState = request.savedSessionUiState,
            isGameEnded = request.isGameEnded,
            gameState = request.gameState,
            playerSetup = request.playerSetup,
            playLevel = request.playLevel,
            topMovesEnabled = request.topMovesEnabled,
            nowMillis = request.nowMillis,
        ),
        store = request.store,
    )
}

internal data class SavedGameRestoreRunRequest(
    val snapshot: SavedGameSnapshot,
    val currentProfile: EngineProfile,
    val defaultPlayLevel: PlayLevelSetting,
    val isEngineBusy: Boolean,
    val isEngineReady: Boolean,
    val searchTimeSettings: SearchTimeSettings,
    val showMessage: (String) -> Unit,
    val applyRestore: (SavedGameRestorePlan) -> Unit,
)

internal sealed class SavedGameRestoreRunResult {
    data object Blocked : SavedGameRestoreRunResult()

    data class Restored(
        val gameState: GameState,
        val engineProfile: EngineProfile,
        val syncEngineAfterRestore: Boolean,
    ) : SavedGameRestoreRunResult()
}

internal fun runSavedGameRestoreApplication(
    request: SavedGameRestoreRunRequest,
): SavedGameRestoreRunResult =
    when (
        val plan = buildSavedGameRestoreRequestPlan(
            snapshot = request.snapshot,
            currentProfile = request.currentProfile,
            defaultPlayLevel = request.defaultPlayLevel,
            isEngineBusy = request.isEngineBusy,
            isEngineReady = request.isEngineReady,
            searchTimeSettings = request.searchTimeSettings,
        )
    ) {
        is SavedGameRestoreRequestPlan.ShowMessage -> {
            request.showMessage(plan.message)
            SavedGameRestoreRunResult.Blocked
        }

        is SavedGameRestoreRequestPlan.Restore -> {
            val restore = plan.restore
            request.applyRestore(restore)
            SavedGameRestoreRunResult.Restored(
                gameState = restore.gameState,
                engineProfile = restore.runtime.engineProfile,
                syncEngineAfterRestore = plan.syncEngineAfterRestore,
            )
        }
    }
