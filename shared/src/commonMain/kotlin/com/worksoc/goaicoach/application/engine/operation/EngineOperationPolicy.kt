package com.worksoc.goaicoach.application.engine.operation

import com.worksoc.goaicoach.shared.GameState

typealias EngineFallbackPolicy = com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
typealias EngineOperationKind = com.worksoc.goaicoach.shared.engine.EngineOperationKind
typealias EngineOperationRequest = com.worksoc.goaicoach.shared.engine.EngineOperationRequest
typealias EngineTimeoutPolicy = com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
internal typealias PositionScopedOperationToken = com.worksoc.goaicoach.shared.engine.PositionScopedOperationToken

sealed class EngineOperationGate {
    data object Allow : EngineOperationGate()
    data object NoOp : EngineOperationGate()
    data class Block(val message: String) : EngineOperationGate()
}

sealed class EngineOperationResultGuard {
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

fun engineOperationRequest(
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

fun evaluateEngineOperationResultGuard(
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

fun evaluateEngineBenchmarkGate(
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

fun evaluateSearchTimeChangeGate(
    isEngineBusy: Boolean,
): EngineOperationGate =
    com.worksoc.goaicoach.shared.engine
        .evaluateSearchTimeChangeGate(isEngineBusy)
        .toApplicationGate()

fun evaluateScoringRuleChangeGate(
    currentRuleset: com.worksoc.goaicoach.shared.Ruleset,
    nextRuleset: com.worksoc.goaicoach.shared.Ruleset,
    isEngineBusy: Boolean,
): EngineOperationGate =
    com.worksoc.goaicoach.shared.engine.evaluateScoringRuleChangeGate(
        currentRuleset = currentRuleset,
        nextRuleset = nextRuleset,
        isEngineBusy = isEngineBusy,
    ).toApplicationGate()
