package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.GameState

internal typealias EngineFallbackPolicy = com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
internal typealias EngineOperationKind = com.worksoc.goaicoach.shared.engine.EngineOperationKind
internal typealias EngineOperationRequest = com.worksoc.goaicoach.shared.engine.EngineOperationRequest
internal typealias EngineTimeoutPolicy = com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
internal typealias PositionScopedOperationToken = com.worksoc.goaicoach.shared.engine.PositionScopedOperationToken

internal sealed class EngineOperationGate {
    data object Allow : EngineOperationGate()
    data object NoOp : EngineOperationGate()
    data class Block(val message: String) : EngineOperationGate()
}

internal sealed class EngineOperationResultGuard {
    data object Apply : EngineOperationResultGuard()
    data class Discard(
        val reason: String,
        val operation: String? = null,
        val operationId: String? = null,
        val sessionGeneration: Long? = null,
    ) : EngineOperationResultGuard()
}

internal sealed class EngineOperationApplyPlan {
    data object Apply : EngineOperationApplyPlan()
    data class Discard(val discard: EngineOperationResultGuard.Discard) : EngineOperationApplyPlan()
}

internal fun positionScopedOperationToken(
    kind: String,
    state: GameState,
): PositionScopedOperationToken =
    com.worksoc.goaicoach.shared.engine.positionScopedOperationToken(kind, state)

internal fun engineOperationRequest(
    kind: EngineOperationKind,
    state: GameState,
    sessionGeneration: Long,
    timeoutPolicy: EngineTimeoutPolicy = EngineTimeoutPolicy(),
    fallbackPolicy: EngineFallbackPolicy = EngineFallbackPolicy.IgnoreStaleResult,
    backendId: String = "local-engine",
): EngineOperationRequest =
    com.worksoc.goaicoach.shared.engine.engineOperationRequest(
        kind = kind,
        state = state,
        sessionGeneration = sessionGeneration,
        timeoutPolicy = timeoutPolicy,
        fallbackPolicy = fallbackPolicy,
        backendId = backendId,
    )

internal fun EngineOperationRequest.toPositionScopedOperationToken(): PositionScopedOperationToken =
    PositionScopedOperationToken(
        kind = kind.code,
        positionFingerprint = boardFingerprint,
        moveCount = moveCount,
    )

internal fun evaluatePositionScopedResultGuard(
    token: PositionScopedOperationToken,
    currentState: GameState,
): EngineOperationResultGuard =
    com.worksoc.goaicoach.shared.engine
        .evaluatePositionScopedResultGuard(token, currentState)
        .toApplicationGuard()

internal fun evaluateEngineOperationResultGuard(
    request: EngineOperationRequest,
    currentState: GameState,
    currentSessionGeneration: Long,
): EngineOperationResultGuard =
    com.worksoc.goaicoach.shared.engine.evaluateEngineOperationResultGuard(
        request = request,
        currentState = currentState,
        currentSessionGeneration = currentSessionGeneration,
    ).toApplicationGuard()

internal fun buildEngineOperationApplyPlan(
    request: EngineOperationRequest,
    currentState: GameState,
    currentSessionGeneration: Long,
): EngineOperationApplyPlan =
    com.worksoc.goaicoach.shared.engine.buildEngineOperationApplyPlan(
        request = request,
        currentState = currentState,
        currentSessionGeneration = currentSessionGeneration,
    ).toApplicationApplyPlan()

internal fun evaluateEngineBenchmarkGate(
    isEngineReady: Boolean,
    supportsDeviceBenchmark: Boolean,
    isEngineBusy: Boolean,
    isBenchmarkRunning: Boolean,
): EngineOperationGate =
    com.worksoc.goaicoach.shared.engine.evaluateEngineBenchmarkGate(
        isEngineReady = isEngineReady,
        supportsDeviceBenchmark = supportsDeviceBenchmark,
        isEngineBusy = isEngineBusy,
        isBenchmarkRunning = isBenchmarkRunning,
    ).toApplicationGate()

internal fun evaluateSearchTimeChangeGate(
    isEngineBusy: Boolean,
): EngineOperationGate =
    com.worksoc.goaicoach.shared.engine
        .evaluateSearchTimeChangeGate(isEngineBusy)
        .toApplicationGate()

internal fun evaluateScoringRuleChangeGate(
    currentRuleset: com.worksoc.goaicoach.shared.Ruleset,
    nextRuleset: com.worksoc.goaicoach.shared.Ruleset,
    isEngineBusy: Boolean,
): EngineOperationGate =
    com.worksoc.goaicoach.shared.engine.evaluateScoringRuleChangeGate(
        currentRuleset = currentRuleset,
        nextRuleset = nextRuleset,
        isEngineBusy = isEngineBusy,
    ).toApplicationGate()

private fun com.worksoc.goaicoach.shared.engine.EngineOperationGate.toApplicationGate(): EngineOperationGate =
    when (this) {
        com.worksoc.goaicoach.shared.engine.EngineOperationGate.Allow -> EngineOperationGate.Allow
        com.worksoc.goaicoach.shared.engine.EngineOperationGate.NoOp -> EngineOperationGate.NoOp
        is com.worksoc.goaicoach.shared.engine.EngineOperationGate.Block ->
            EngineOperationGate.Block(message)
    }

private fun com.worksoc.goaicoach.shared.engine.EngineOperationResultGuard.toApplicationGuard(): EngineOperationResultGuard =
    when (this) {
        com.worksoc.goaicoach.shared.engine.EngineOperationResultGuard.Apply ->
            EngineOperationResultGuard.Apply

        is com.worksoc.goaicoach.shared.engine.EngineOperationResultGuard.Discard ->
            EngineOperationResultGuard.Discard(
                reason = reason,
                operation = operation,
                operationId = operationId,
                sessionGeneration = sessionGeneration,
            )
    }

private fun com.worksoc.goaicoach.shared.engine.EngineOperationApplyPlan.toApplicationApplyPlan(): EngineOperationApplyPlan =
    when (this) {
        com.worksoc.goaicoach.shared.engine.EngineOperationApplyPlan.Apply ->
            EngineOperationApplyPlan.Apply

        is com.worksoc.goaicoach.shared.engine.EngineOperationApplyPlan.Discard ->
            EngineOperationApplyPlan.Discard(discard.toApplicationDiscard())
    }

private fun com.worksoc.goaicoach.shared.engine.EngineOperationResultGuard.Discard.toApplicationDiscard():
    EngineOperationResultGuard.Discard =
    EngineOperationResultGuard.Discard(
        reason = reason,
        operation = operation,
        operationId = operationId,
        sessionGeneration = sessionGeneration,
    )
