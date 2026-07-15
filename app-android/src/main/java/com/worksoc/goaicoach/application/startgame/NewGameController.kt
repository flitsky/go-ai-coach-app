package com.worksoc.goaicoach.application.startgame

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.RuntimePlayLevelSelection
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest

internal class NewGameController(
    private val engineClient: EngineSessionClient,
    private val diagnosticEventLog: DiagnosticEventLogPort,
    private val runtimeEventLog: RuntimeEventLogPort,
    private val defaultPlayLevel: PlayLevelSetting,
    private val isEngineReady: () -> Boolean,
    private val isEngineBusy: () -> Boolean,
    private val currentGameState: () -> GameState,
    private val currentPlayerSetup: () -> PlayerSetup,
    private val currentEngineProfile: () -> EngineProfile,
    private val currentSearchTimeSettings: () -> SearchTimeSettings,
    private val currentBoardSize: () -> BoardSize,
    private val currentHandicapCount: () -> Int,
    private val currentSessionGeneration: () -> Long,
    private val currentScoreState: () -> GameSessionScoreState,
    private val currentRuntimeLogContext: () -> RuntimeLogContext,
    private val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    private val applyGameSessionResetPlan: (GameSessionResetPlan) -> Unit,
    private val applyRuntimePlayLevelSelection: (RuntimePlayLevelSelection) -> Unit,
    private val replaceScoreState: (GameSessionScoreState) -> Unit,
    private val requestFollowUpAnalysis: (GameState) -> Unit,
    private val onEngineMessage: (String) -> Unit,
) {
    fun resetLocalGame(message: String, ruleset: Ruleset, boardSize: BoardSize, handicapCount: Int = 0) {
        applyGameSessionResetPlan(buildNewLocalGameSessionPlan(message, ruleset, boardSize, handicapCount))
    }

    fun startEngineBackedNewGame(plan: StartConfiguredGamePlan.StartEngineGame) {
        val targetState = GameState.withHandicap(
            boardSize = plan.boardSize,
            ruleset = plan.ruleset,
            handicapCount = plan.handicapCount,
        )
        runStartEngineBackedGameApplication(
            StartEngineBackedGameRunRequest(
                plan = plan,
                engineClient = engineClient,
                currentState = targetState,
                sessionGeneration = currentSessionGeneration(),
                runtimeContextProvider = currentRuntimeLogContext,
                runtimeEventLog = runtimeEventLog,
                diagnosticEventLog = diagnosticEventLog,
                applyRuntime = applyRuntimePlayLevelSelection,
                launchEngineOperation = launchEngineOperation,
                resetLocalGame = { msg, ruleset, boardSize -> resetLocalGame(msg, ruleset, boardSize, plan.handicapCount) },
                currentScoreStateProvider = currentScoreState,
                replaceScoreState = replaceScoreState,
                currentStateProvider = { targetState },
                requestFollowUpAnalysis = requestFollowUpAnalysis,
            ),
        )
    }

    fun startConfiguredGame() {
        val gameState = currentGameState()
        val targetState = GameState.withHandicap(
            boardSize = currentBoardSize(),
            ruleset = gameState.ruleset,
            handicapCount = currentHandicapCount(),
        )
        when (
            val plan = buildStartConfiguredGamePlan(
                setup = currentPlayerSetup(),
                boardSize = targetState.boardSize,
                ruleset = targetState.ruleset,
                nextPlayer = targetState.nextPlayer,
                isEngineReady = isEngineReady(),
                isEngineBusy = isEngineBusy(),
                currentProfile = currentEngineProfile(),
                defaultPlayLevel = defaultPlayLevel,
                searchTimeSettings = currentSearchTimeSettings(),
                handicapCount = targetState.handicapCount,
            )
        ) {
            is StartConfiguredGamePlan.ShowMessage -> onEngineMessage(plan.message)
            is StartConfiguredGamePlan.ResetLocalGame -> resetLocalGame(plan.message, plan.ruleset, plan.boardSize, plan.handicapCount)
            is StartConfiguredGamePlan.StartEngineGame -> startEngineBackedNewGame(plan)
        }
    }
}
