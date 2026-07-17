package com.worksoc.goaicoach.application.savedgame

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.score.RestoredGameSyncRunRequest
import com.worksoc.goaicoach.application.score.ScoreSyncCompletionApplyPlan
import com.worksoc.goaicoach.application.score.runRestoredGameSyncApplication
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy

internal class SavedSessionController(
    private val engineClient: EngineSessionClient,
    private val diagnosticEventLog: DiagnosticEventLogPort,
    private val defaultPlayLevel: PlayLevelSetting,
    private val isEngineBusy: () -> Boolean,
    private val isEngineReady: () -> Boolean,
    private val currentSearchTimeSettings: () -> SearchTimeSettings,
    private val currentEngineProfile: () -> EngineProfile,
    private val currentSessionGeneration: () -> Long,
    private val timeoutPolicy: (EngineProfile) -> EngineTimeoutPolicy,
    private val currentGameState: () -> GameState,
    private val onEngineMessage: (String) -> Unit,
    private val applySavedGameRestorePlan: (SavedGameRestorePlan) -> Unit,
    private val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    private val applyScoreSyncCompletion: (ScoreSyncCompletionApplyPlan) -> GameState?,
    private val requestFollowUpAnalysis: (GameState) -> Unit,
) {
    fun restore(snapshot: SavedGameSnapshot) {
        val result = runSavedGameRestoreApplication(
            SavedGameRestoreRunRequest(
                snapshot = snapshot,
                currentProfile = currentEngineProfile(),
                defaultPlayLevel = defaultPlayLevel,
                isEngineBusy = isEngineBusy(),
                isEngineReady = isEngineReady(),
                searchTimeSettings = currentSearchTimeSettings(),
                showMessage = onEngineMessage,
                applyRestore = applySavedGameRestorePlan,
            ),
        )
        if (result !is SavedGameRestoreRunResult.Restored || !result.syncEngineAfterRestore) {
            return
        }

        runRestoredGameSyncApplication(
            RestoredGameSyncRunRequest(
                engineClient = engineClient,
                state = result.gameState,
                profile = result.engineProfile,
                sessionGeneration = currentSessionGeneration(),
                timeoutPolicy = timeoutPolicy(result.engineProfile),
                diagnosticEventLog = diagnosticEventLog,
                currentState = currentGameState,
                currentSessionGeneration = currentSessionGeneration,
                runEngineOperation = launchEngineOperation,
                applyCompletion = applyScoreSyncCompletion,
                requestFollowUpAnalysis = requestFollowUpAnalysis,
                scoreSnapshots = result.scoreSnapshots,
            ),
        )
    }
}
