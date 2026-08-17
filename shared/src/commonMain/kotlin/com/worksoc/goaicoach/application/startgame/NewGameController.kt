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

class NewGameController(
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
    private val cancelStaleOperations: () -> Unit,
    private val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    private val applyGameSessionResetPlan: (GameSessionResetPlan) -> Unit,
    private val applyRuntimePlayLevelSelection: (RuntimePlayLevelSelection) -> Unit,
    private val replaceScoreState: (GameSessionScoreState) -> Unit,
    private val requestFollowUpAnalysis: (GameState) -> Unit,
    private val onEngineMessage: (String) -> Unit,
) {
    fun resetLocalGame(
        message: String,
        ruleset: Ruleset,
        boardSize: BoardSize,
        handicapCount: Int = 0,
        komi: Double = com.worksoc.goaicoach.shared.DefaultKomi,
    ) {
        applyGameSessionResetPlan(buildNewLocalGameSessionPlan(message, ruleset, boardSize, handicapCount, komi))
    }

    fun startEngineBackedNewGame(plan: StartConfiguredGamePlan.StartEngineGame) {
        val targetState = GameState.withHandicap(
            boardSize = plan.boardSize,
            ruleset = plan.ruleset,
            handicapCount = plan.handicapCount,
            komi = plan.komi,
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
                resetLocalGame = { msg, ruleset, boardSize -> resetLocalGame(msg, ruleset, boardSize, plan.handicapCount, plan.komi) },
                currentScoreStateProvider = currentScoreState,
                replaceScoreState = replaceScoreState,
                currentStateProvider = { targetState },
                requestFollowUpAnalysis = requestFollowUpAnalysis,
            ),
        )
    }

    fun startConfiguredGame() {
        // 직전 대국(예: 방금 기권한 대국)을 정리하던 엔진 작업이 아직 activeOperations에 남아
        // 있으면, 그게 늦게 끝나는 동안 새 대국의 isEngineBusy가 계속 true로 잡혀 AI 턴 예약이
        // 조용히 취소되는 경쟁 상태가 생긴다 — 새 대국을 실제로 시작하기 전에 먼저 비운다.
        cancelStaleOperations()
        val gameState = currentGameState()
        val targetState = GameState.withHandicap(
            boardSize = currentBoardSize(),
            ruleset = gameState.ruleset,
            handicapCount = currentHandicapCount(),
            komi = gameState.komi,
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
                komi = targetState.komi,
            )
        ) {
            is StartConfiguredGamePlan.ShowMessage -> onEngineMessage(plan.message)
            is StartConfiguredGamePlan.ResetLocalGame -> resetLocalGame(plan.message, plan.ruleset, plan.boardSize, plan.handicapCount, plan.komi)
            is StartConfiguredGamePlan.StartEngineGame -> startEngineBackedNewGame(plan)
        }
    }
}
