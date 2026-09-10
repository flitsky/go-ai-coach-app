package com.worksoc.goaicoach.application.engine.operation

import com.worksoc.goaicoach.shared.GameState

typealias EngineFallbackPolicy = com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
typealias EngineOperationKind = com.worksoc.goaicoach.shared.engine.EngineOperationKind
typealias EngineOperationRequest = com.worksoc.goaicoach.shared.engine.EngineOperationRequest
typealias EngineTimeoutPolicy = com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
internal typealias PositionScopedOperationToken = com.worksoc.goaicoach.shared.engine.PositionScopedOperationToken

typealias EngineOperationBlockReason = com.worksoc.goaicoach.shared.engine.EngineOperationBlockReason

sealed class EngineOperationGate {
    data object Allow : EngineOperationGate()
    data object NoOp : EngineOperationGate()

    /**
     * @property message 진단용 영어 문장. @property reason 화면이 번역할 타입.
     * ⚠️ 둘의 역할이 다르다 — 자세한 사유는 `shared.engine.EngineOperationBlockReason`의 KDoc.
     */
    data class Block(
        val message: String,
        val reason: EngineOperationBlockReason,
    ) : EngineOperationGate()
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
