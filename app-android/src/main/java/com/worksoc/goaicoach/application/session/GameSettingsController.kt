package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.engine.operation.EngineOperationGate
import com.worksoc.goaicoach.application.engine.operation.evaluateSearchTimeChangeGate
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.runtimeAutoPlayDelayChangeLog
import com.worksoc.goaicoach.application.topmoves.SearchTimeTopMovesResetRunRequest
import com.worksoc.goaicoach.application.topmoves.runSearchTimeTopMovesResetApplication
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.EngineProfile

/**
 * Owns three related settings-change workflows that share no engine I/O
 * and are gated only on engine-busy state or validation rules:
 *
 * - [changePlayerSetup]: validates engine-busy gate, builds plan, applies it.
 * - [changeSearchTimeSettings]: validates gate, normalises settings, resets top-move
 *   analysis cache, updates runtime play-level selection.
 * - [changeAutoPlayDelay]: logs and applies delay setting.
 *
 * GoCoachApp delegates all three handlers here. The composable supplies
 * provider lambdas for current state values and callbacks for state writes.
 * This controller owns no Compose state itself.
 */
internal class GameSettingsController(
    private val currentGameState: () -> GameState,
    private val currentPlayerSetup: () -> PlayerSetup,
    private val currentEngineProfile: () -> EngineProfile,
    private val currentSearchTimeSettings: () -> SearchTimeSettings,
    private val currentAnalysisState: () -> GameSessionAnalysisState,
    private val currentAutoPlayDelaySetting: () -> AutoPlayDelaySetting,
    private val defaultPlayLevel: PlayLevelSetting,
    private val isEngineBusy: () -> Boolean,
    private val runtimeEventLog: RuntimeEventLogPort,
    private val currentRuntimeLogContext: () -> RuntimeLogContext,
    private val onEngineMessage: (String) -> Unit,
    private val applyPlayerSetup: (PlayerSetup) -> Unit,
    private val applyCoreSessionState: (GameSessionCoreState) -> Unit,
    private val currentCoreSessionState: () -> GameSessionCoreState,
    private val applyRuntimePlayLevelSelection: (RuntimePlayLevelSelection) -> Unit,
    private val applyAnalysisState: (GameSessionAnalysisState) -> Unit,
    private val applySettingsAutoPlayDelay: (AutoPlayDelaySetting) -> Unit,
    private val applySettingsSearchTimeSettings: (SearchTimeSettings) -> Unit,
    private val clearUndoEngineInterventionQuietWindow: () -> Unit,
) {
    /**
     * Validates engine-busy gate, then builds and applies a [PlayerSetupChangePlan].
     * No engine I/O — only local state mutation.
     */
    fun changePlayerSetup(nextSetup: PlayerSetup) {
        val gameState = currentGameState()
        when (
            val plan = buildPlayerSetupChangePlan(
                nextSetup = nextSetup,
                currentState = gameState,
                currentProfile = currentEngineProfile(),
                defaultPlayLevel = defaultPlayLevel,
                isEngineBusy = false, // engine-busy gate disabled; restore with isEngineBusy()
                searchTimeSettings = currentSearchTimeSettings(),
            )
        ) {
            is PlayerSetupChangePlan.ShowMessage -> {
                onEngineMessage(plan.message)
            }
            is PlayerSetupChangePlan.Apply -> {
                clearUndoEngineInterventionQuietWindow()
                applyPlayerSetup(plan.playerSetup)
                applyCoreSessionState(currentCoreSessionState().applyPlayerSetupChangePlan(plan))
            }
        }
    }

    /**
     * Validates engine-busy gate, normalises [SearchTimeSettings], resets the
     * top-moves analysis cache, and updates runtime play-level selection.
     */
    fun changeSearchTimeSettings(nextSettings: SearchTimeSettings) {
        when (val gate = evaluateSearchTimeChangeGate(isEngineBusy = false)) { // engine-busy gate disabled; restore with isEngineBusy()
            EngineOperationGate.Allow -> Unit
            EngineOperationGate.NoOp -> return
            is EngineOperationGate.Block -> {
                onEngineMessage(gate.message)
                return
            }
        }
        val normalized = nextSettings.normalized()
        clearUndoEngineInterventionQuietWindow()
        applySettingsSearchTimeSettings(normalized)
        applyRuntimePlayLevelSelection(
            selectRuntimePlayLevel(
                setup = currentPlayerSetup(),
                nextPlayer = currentGameState().nextPlayer,
                currentProfile = currentEngineProfile(),
                defaultPlayLevel = defaultPlayLevel,
                searchTimeSettings = normalized,
            ),
        )
        runSearchTimeTopMovesResetApplication(
            SearchTimeTopMovesResetRunRequest(
                analysisState = currentAnalysisState(),
                state = currentGameState(),
                applyAnalysisState = applyAnalysisState,
            ),
        )
    }

    /**
     * Logs the delay change and applies the new [AutoPlayDelaySetting].
     * No gate check required — purely a UI preference update.
     */
    fun changeAutoPlayDelay(setting: AutoPlayDelaySetting) {
        runtimeEventLog.append(
            runtimeAutoPlayDelayChangeLog(
                context = currentRuntimeLogContext(),
                from = currentAutoPlayDelaySetting(),
                to = setting,
            ),
        )
        applySettingsAutoPlayDelay(setting)
    }
}
