package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleCallbacks
import com.worksoc.goaicoach.application.engine.operation.runEngineOperationInScope
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.engine.engineOperationRequest

sealed class EngineStartupWorkflowResult {
    data class Success(val result: EngineStartupResult) : EngineStartupWorkflowResult()
    data class Failure(val error: Throwable) : EngineStartupWorkflowResult()
}

data class EngineStartupRunRequest(
    val state: GameState,
    val profile: EngineProfile,
    val sessionGeneration: Long,
    /**
     * ⚠️ **값이 아니라 공급자다**(백로그 #101 ③단계). 이 요청은 엔진이 아직 준비되지 않은
     * 채로 만들어져 `await()` 안에서 기다릴 수 있는데, 이 문구는 **실패했을 때** 비로소 쓰인다.
     * 값으로 붙잡아 두면 하필 그것이 필요한 순간(스텁 폴백)에 *"준비 중"* 만 남는다.
     */
    val engineDiagnostic: () -> String,
    val diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
    val lifecycleCallbacks: EngineOperationLifecycleCallbacks = EngineOperationLifecycleCallbacks(),
)

suspend fun EngineSessionClient.runEngineStartupApplication(
    request: EngineStartupRunRequest,
): EngineStartupDisplayPlan {
    val operation = engineOperationRequest(
        kind = EngineOperationKind.EngineStartup,
        state = request.state,
        sessionGeneration = request.sessionGeneration,
        timeoutPolicy = EngineTimeoutPolicy(label = "engine-startup"),
        fallbackPolicy = EngineFallbackPolicy.None,
        backendId = capabilities.backend.label,
    )
    return runEngineOperationInScope(
        request = operation,
        callbacks = request.lifecycleCallbacks,
    ) {
        val result = runEngineIo {
            runEngineStartupWorkflowResult(
                effect = GameSessionEffect.StartEngineSession(
                    state = request.state,
                    profile = request.profile,
                ),
                operationRequest = operation,
                diagnosticEventLog = request.diagnosticEventLog,
            )
        }
        buildEngineStartupDisplayPlan(
            state = request.state,
            result = result,
            engineDiagnostic = request.engineDiagnostic(),
        )
    }
}

suspend fun EngineSessionClient.runEngineStartupEffect(
    effect: GameSessionEffect.StartEngineSession,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): EngineStartupResult =
    runObservedEngineOperation(
        request = operationRequest ?: engineOperationRequest(
            kind = EngineOperationKind.EngineStartup,
            state = effect.state,
            sessionGeneration = 0L,
            timeoutPolicy = EngineTimeoutPolicy(label = "engine-startup"),
            fallbackPolicy = EngineFallbackPolicy.None,
            backendId = capabilities.backend.label,
        ),
        diagnosticEventLog = diagnosticEventLog,
    ) {
        startSession(
            profile = effect.profile,
            state = effect.state,
        )
    }

suspend fun EngineSessionClient.runEngineStartupWorkflowResult(
    effect: GameSessionEffect.StartEngineSession,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): EngineStartupWorkflowResult =
    runCatching {
        runEngineStartupEffect(
            effect = effect,
            operationRequest = operationRequest,
            diagnosticEventLog = diagnosticEventLog,
        )
    }.fold(
        onSuccess = { result -> EngineStartupWorkflowResult.Success(result) },
        onFailure = { error -> EngineStartupWorkflowResult.Failure(error) },
    )

suspend fun EngineSessionClient.runEngineBackedNewGameEffect(
    effect: GameSessionEffect.StartEngineBackedGame,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): EngineStartupResult =
    runObservedEngineOperation(
        request = operationRequest ?: engineOperationRequest(
            kind = EngineOperationKind.EngineNewGame,
            state = effect.currentState,
            sessionGeneration = 0L,
            timeoutPolicy = EngineTimeoutPolicy(label = "engine-new-game"),
            fallbackPolicy = EngineFallbackPolicy.LocalEngine,
            backendId = capabilities.backend.label,
        ),
        diagnosticEventLog = diagnosticEventLog,
    ) {
        startNewGame(
            profile = effect.profile,
            boardSize = effect.boardSize,
            ruleset = effect.ruleset,
            handicapCount = effect.currentState.handicapCount,
        )
    }

suspend fun EngineSessionClient.runEngineBackedNewGameWorkflowResult(
    effect: GameSessionEffect.StartEngineBackedGame,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): EngineStartupWorkflowResult =
    runCatching {
        runEngineBackedNewGameEffect(
            effect = effect,
            operationRequest = operationRequest,
            diagnosticEventLog = diagnosticEventLog,
        )
    }.fold(
        onSuccess = { result -> EngineStartupWorkflowResult.Success(result) },
        onFailure = { error -> EngineStartupWorkflowResult.Failure(error) },
    )

suspend fun EngineSessionClient.runEngineUndoEffect(
    effect: GameSessionEffect.UndoEngineMoves,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): EngineStatus =
    runObservedEngineOperation(
        request = operationRequest ?: engineOperationRequest(
            kind = EngineOperationKind.EngineUndo,
            state = effect.state,
            sessionGeneration = 0L,
            timeoutPolicy = EngineTimeoutPolicy(label = "engine-undo"),
            fallbackPolicy = EngineFallbackPolicy.LocalEngine,
            backendId = capabilities.backend.label,
        ),
        diagnosticEventLog = diagnosticEventLog,
    ) {
        var lastStatus: EngineStatus? = null
        repeat(effect.undoCount) {
            lastStatus = undoMove()
        }
        requireNotNull(lastStatus) { "undoCount must be positive" }
    }

sealed class EngineUndoWorkflowResult {
    data class Success(val status: EngineStatus) : EngineUndoWorkflowResult()
    data class Failure(val error: Throwable) : EngineUndoWorkflowResult()
}

suspend fun EngineSessionClient.runEngineUndoWorkflowResult(
    effect: GameSessionEffect.UndoEngineMoves,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): EngineUndoWorkflowResult =
    runCatching {
        runEngineUndoEffect(
            effect = effect,
            operationRequest = operationRequest,
            diagnosticEventLog = diagnosticEventLog,
        )
    }.fold(
        onSuccess = { status -> EngineUndoWorkflowResult.Success(status) },
        onFailure = { error -> EngineUndoWorkflowResult.Failure(error) },
    )
