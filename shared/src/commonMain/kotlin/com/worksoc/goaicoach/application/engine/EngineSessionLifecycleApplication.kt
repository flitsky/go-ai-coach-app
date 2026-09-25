package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.contract.GameSessionEffect
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleCallbacks
import com.worksoc.goaicoach.application.engine.operation.runEngineOperationInScope
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.policy.engineOperationRequest

internal sealed class EngineStartupWorkflowResult {
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

suspend fun EngineLifecycleClient.runEngineStartupApplication(
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

internal suspend fun EngineLifecycleClient.runEngineStartupEffect(
    effect: GameSessionEffect.StartEngineSession,
    operationRequest: EngineOperationRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): EngineStartupResult =
    runObservedEngineOperation(
        request = operationRequest,
        diagnosticEventLog = diagnosticEventLog,
    ) {
        startSession(
            profile = effect.profile,
            state = effect.state,
        )
    }

internal suspend fun EngineLifecycleClient.runEngineStartupWorkflowResult(
    effect: GameSessionEffect.StartEngineSession,
    operationRequest: EngineOperationRequest,
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

internal suspend fun EngineLifecycleClient.runEngineBackedNewGameEffect(
    effect: GameSessionEffect.StartEngineBackedGame,
    operationRequest: EngineOperationRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): EngineStartupResult =
    runObservedEngineOperation(
        request = operationRequest,
        diagnosticEventLog = diagnosticEventLog,
    ) {
        startNewGame(
            profile = effect.profile,
            boardSize = effect.boardSize,
            ruleset = effect.ruleset,
            handicapCount = effect.currentState.handicapCount,
        )
    }

internal suspend fun EngineLifecycleClient.runEngineBackedNewGameWorkflowResult(
    effect: GameSessionEffect.StartEngineBackedGame,
    operationRequest: EngineOperationRequest,
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
