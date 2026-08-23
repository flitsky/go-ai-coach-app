package com.worksoc.goaicoach.application.startgame

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.EngineStartupWorkflowResult
import com.worksoc.goaicoach.application.engine.runEngineBackedNewGameWorkflowResult
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.runtimeEngineGameStartFailureLog
import com.worksoc.goaicoach.application.runtime.runtimeEngineGameStartRequestLog
import com.worksoc.goaicoach.application.runtime.runtimeEngineGameStartSuccessLog
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.engine.engineOperationRequest

data class StartEngineBackedGameRunRequest(
    val plan: StartConfiguredGamePlan.StartEngineGame,
    val engineClient: EngineSessionClient,
    val currentState: GameState,
    val sessionGeneration: Long,
    val runtimeContextProvider: () -> RuntimeLogContext,
    val runtimeEventLog: RuntimeEventLogPort,
    val diagnosticEventLog: DiagnosticEventLogPort,
    val applyRuntime: (RuntimePlayLevelSelection) -> Unit,
    val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    val resetLocalGame: (message: String, ruleset: Ruleset, boardSize: BoardSize) -> Unit,
    val currentScoreStateProvider: () -> GameSessionScoreState,
    val replaceScoreState: (GameSessionScoreState) -> Unit,
    val currentStateProvider: () -> GameState,
    val requestFollowUpAnalysis: (GameState) -> Unit,
    val nowMillis: () -> Long = { currentEpochMillis() },
)

fun runStartEngineBackedGameApplication(
    request: StartEngineBackedGameRunRequest,
) {
    val targetRuleset = request.plan.ruleset
    val runtime = request.plan.runtime
    request.applyRuntime(runtime)
    request.runtimeEventLog.append(
        runtimeEngineGameStartRequestLog(
            context = request.runtimeContextProvider(),
            ruleset = targetRuleset,
            runtime = runtime,
        ),
    )
    val operation = engineOperationRequest(
        kind = EngineOperationKind.EngineNewGame,
        state = request.currentState,
        sessionGeneration = request.sessionGeneration,
        timeoutPolicy = EngineTimeoutPolicy(label = "engine-new-game"),
        fallbackPolicy = EngineFallbackPolicy.LocalEngine,
    )
    request.launchEngineOperation(operation) {
        val startMillis = request.nowMillis()
        val result =
            runEngineIo {
                request.engineClient.runEngineBackedNewGameWorkflowResult(
                    effect = GameSessionEffect.StartEngineBackedGame(
                        currentState = request.currentStateProvider(),
                        profile = runtime.engineProfile,
                        boardSize = request.plan.boardSize,
                        ruleset = targetRuleset,
                    ),
                    operationRequest = operation,
                    diagnosticEventLog = request.diagnosticEventLog,
                )
            }
        val nextAnalysisState = when (result) {
            is EngineStartupWorkflowResult.Success -> {
                request.runtimeEventLog.append(
                    runtimeEngineGameStartSuccessLog(
                        context = request.runtimeContextProvider(),
                        elapsedMs = request.nowMillis() - startMillis,
                        message = result.result.message,
                    ),
                )
                request.resetLocalGame(result.result.message, targetRuleset, request.plan.boardSize)
                val resetState = request.currentStateProvider()
                // No moves have been played on resetState yet (handicap stones only, if any), so
                // there is no engine estimate to show for it. Only replace the snapshots when the
                // engine actually returned one - falling back to a local flood-fill territory
                // estimate here would misreport the whole empty board as one side's territory
                // (see the B+157.5 misdisplay).
                result.result.scoreSnapshot?.let { snapshot ->
                    request.replaceScoreState(
                        request.currentScoreStateProvider().replaceSnapshots(listOf(snapshot)),
                    )
                }
                resetState
            }

            is EngineStartupWorkflowResult.Failure -> {
                request.runtimeEventLog.append(
                    runtimeEngineGameStartFailureLog(
                        context = request.runtimeContextProvider(),
                        elapsedMs = request.nowMillis() - startMillis,
                        error = result.error,
                    ),
                )
                request.resetLocalGame(result.error.message ?: "New AI game failed.", targetRuleset, request.plan.boardSize)
                request.currentStateProvider()
            }
        }
        request.requestFollowUpAnalysis(nextAnalysisState)
    }
}
