package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.EngineFallbackPolicy
import com.worksoc.goaicoach.application.EngineOperationKind
import com.worksoc.goaicoach.application.EngineOperationRequest
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.EngineStartupResult
import com.worksoc.goaicoach.application.EngineTimeoutPolicy
import com.worksoc.goaicoach.application.engineOperationRequest
import com.worksoc.goaicoach.application.session.GameSessionEffect

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.shared.EngineStatus

internal sealed class EngineStartupWorkflowResult {
    data class Success(val result: EngineStartupResult) : EngineStartupWorkflowResult()
    data class Failure(val error: Throwable) : EngineStartupWorkflowResult()
}

internal suspend fun EngineSessionClient.runEngineStartupEffect(
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

internal suspend fun EngineSessionClient.runEngineStartupWorkflowResult(
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

internal suspend fun EngineSessionClient.runEngineBackedNewGameEffect(
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
        )
    }

internal suspend fun EngineSessionClient.runEngineBackedNewGameWorkflowResult(
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

internal suspend fun EngineSessionClient.runEngineUndoEffect(
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

internal sealed class EngineUndoWorkflowResult {
    data class Success(val status: EngineStatus) : EngineUndoWorkflowResult()
    data class Failure(val error: Throwable) : EngineUndoWorkflowResult()
}

internal suspend fun EngineSessionClient.runEngineUndoWorkflowResult(
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
