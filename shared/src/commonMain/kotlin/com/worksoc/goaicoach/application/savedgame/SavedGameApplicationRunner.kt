package com.worksoc.goaicoach.application.savedgame

import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreSnapshot

data class SavedSessionPromptRunRequest(
    val store: SavedGameStorePort,
    val applyPrompt: (SavedSessionPromptPlan) -> Unit,
)

fun runSavedSessionPromptApplication(
    request: SavedSessionPromptRunRequest,
) {
    request.applyPrompt(loadSavedSessionPromptPlan(request.store))
}

data class SavedGamePersistenceRunRequest(
    val savedSessionUiState: SavedSessionUiState,
    val isGameEnded: Boolean,
    val gameState: GameState,
    val playerSetup: PlayerSetup,
    val playLevel: PlayLevelSetting,
    val topMovesEnabled: Boolean,
    val scoreSnapshots: List<ScoreSnapshot>,
    val nowMillis: Long,
    val store: SavedGameStorePort,
    val finalScoreJudgement: FinalScoreJudgement? = null,
)

fun runSavedGamePersistenceApplication(
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
            scoreSnapshots = request.scoreSnapshots,
            nowMillis = request.nowMillis,
            finalScoreJudgement = request.finalScoreJudgement,
        ),
        store = request.store,
    )
}

data class SavedGameRestoreRunRequest(
    val snapshot: SavedGameSnapshot,
    val currentProfile: EngineProfile,
    val defaultPlayLevel: PlayLevelSetting,
    val isEngineBusy: Boolean,
    val isEngineReady: Boolean,
    val searchTimeSettings: SearchTimeSettings,
    val showMessage: (String) -> Unit,
    val applyRestore: (SavedGameRestorePlan) -> Unit,
)

sealed class SavedGameRestoreRunResult {
    data object Blocked : SavedGameRestoreRunResult()

    data class Restored(
        val gameState: GameState,
        val engineProfile: EngineProfile,
        val syncEngineAfterRestore: Boolean,
        val scoreSnapshots: List<ScoreSnapshot>,
    ) : SavedGameRestoreRunResult()
}

fun runSavedGameRestoreApplication(
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
                scoreSnapshots = restore.scoreSnapshots,
            )
        }
    }
