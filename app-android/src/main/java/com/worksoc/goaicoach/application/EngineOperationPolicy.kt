package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.analysisFingerprint

internal sealed class EngineOperationGate {
    data object Allow : EngineOperationGate()
    data object NoOp : EngineOperationGate()
    data class Block(val message: String) : EngineOperationGate()
}

internal data class PositionScopedOperationToken(
    val kind: String,
    val positionFingerprint: String,
    val moveCount: Int,
)

internal enum class EngineOperationKind(
    val code: String,
) {
    TopMoves("top_moves"),
    ScoreEstimate("score_estimate"),
    AutoAiTurn("auto_ai_turn"),
    AutoAiEndgame("auto_ai_endgame"),
    HumanMoveSync("human_move_sync"),
    RestoredGameSync("restored_game_sync"),
    StartupBenchmark("startup_benchmark"),
    PositionCacheOptimization("position_cache_optimization"),
    RemotePositionAnalysis("remote_position_analysis"),
}

internal data class EngineTimeoutPolicy(
    val timeoutMillis: Long? = null,
    val label: String = if (timeoutMillis == null) "uncapped" else "cap:${timeoutMillis}ms",
) {
    init {
        require(timeoutMillis == null || timeoutMillis > 0) { "timeoutMillis must be positive when set" }
        require(label.isNotBlank()) { "label must not be blank" }
    }
}

internal enum class EngineFallbackPolicy(
    val label: String,
) {
    None("none"),
    LocalEngine("local-engine"),
    LocalRules("local-rules"),
    CachedAnalysis("cached-analysis"),
    IgnoreStaleResult("ignore-stale-result"),
}

/**
 * Common metadata for any engine-facing operation.
 *
 * Local process calls and future remote-server calls share the same failure
 * model: results can be late, fail, or belong to an older match generation.
 * This request object makes those assumptions explicit before we move more
 * operation runners out of UI code.
 */
internal data class EngineOperationRequest(
    val operationId: String,
    val kind: EngineOperationKind,
    val sessionGeneration: Long,
    val boardFingerprint: String,
    val moveCount: Int,
    val timeoutPolicy: EngineTimeoutPolicy,
    val fallbackPolicy: EngineFallbackPolicy,
    val backendId: String,
) {
    init {
        require(operationId.isNotBlank()) { "operationId must not be blank" }
        require(sessionGeneration >= 0) { "sessionGeneration must be zero or greater" }
        require(boardFingerprint.isNotBlank()) { "boardFingerprint must not be blank" }
        require(moveCount >= 0) { "moveCount must be zero or greater" }
        require(backendId.isNotBlank()) { "backendId must not be blank" }
    }
}

internal sealed class EngineOperationResultGuard {
    data object Apply : EngineOperationResultGuard()
    data class Discard(
        val reason: String,
    ) : EngineOperationResultGuard()
}

internal fun positionScopedOperationToken(
    kind: String,
    state: GameState,
): PositionScopedOperationToken =
    PositionScopedOperationToken(
        kind = kind,
        positionFingerprint = state.analysisFingerprint(),
        moveCount = state.moves.size,
    )

internal fun engineOperationRequest(
    kind: EngineOperationKind,
    state: GameState,
    sessionGeneration: Long,
    timeoutPolicy: EngineTimeoutPolicy = EngineTimeoutPolicy(),
    fallbackPolicy: EngineFallbackPolicy = EngineFallbackPolicy.IgnoreStaleResult,
    backendId: String = "local-engine",
    operationId: String = defaultEngineOperationId(kind, state, sessionGeneration),
): EngineOperationRequest =
    EngineOperationRequest(
        operationId = operationId,
        kind = kind,
        sessionGeneration = sessionGeneration,
        boardFingerprint = state.analysisFingerprint(),
        moveCount = state.moves.size,
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
): EngineOperationResultGuard {
    val currentFingerprint = currentState.analysisFingerprint()
    return if (currentFingerprint == token.positionFingerprint) {
        EngineOperationResultGuard.Apply
    } else {
        EngineOperationResultGuard.Discard(
            reason = "${token.kind} result is stale: requested move=${token.moveCount}, current move=${currentState.moves.size}.",
        )
    }
}

internal fun evaluateEngineOperationResultGuard(
    request: EngineOperationRequest,
    currentState: GameState,
    currentSessionGeneration: Long,
): EngineOperationResultGuard {
    if (currentSessionGeneration != request.sessionGeneration) {
        return EngineOperationResultGuard.Discard(
            reason = "${request.kind.code} result is stale: requested generation=${request.sessionGeneration}, current generation=$currentSessionGeneration.",
        )
    }
    return evaluatePositionScopedResultGuard(
        token = request.toPositionScopedOperationToken(),
        currentState = currentState,
    )
}

private fun defaultEngineOperationId(
    kind: EngineOperationKind,
    state: GameState,
    sessionGeneration: Long,
): String =
    "${kind.code}:g$sessionGeneration:m${state.moves.size}:${state.analysisFingerprint().take(12)}"

internal fun evaluateEngineBenchmarkGate(
    isEngineReady: Boolean,
    supportsDeviceBenchmark: Boolean,
    isEngineBusy: Boolean,
    isBenchmarkRunning: Boolean,
): EngineOperationGate =
    when {
        !isEngineReady ->
            EngineOperationGate.Block("Engine benchmark requires a ready local engine.")

        !supportsDeviceBenchmark ->
            EngineOperationGate.Block("Engine benchmark is available only for the local KataGo process engine.")

        isEngineBusy || isBenchmarkRunning ->
            EngineOperationGate.Block("Engine is busy. Run benchmark after the current response.")

        else -> EngineOperationGate.Allow
    }

internal fun evaluateSearchTimeChangeGate(isEngineBusy: Boolean): EngineOperationGate =
    if (isEngineBusy) {
        EngineOperationGate.Block("Engine is busy. Change search time after the current action.")
    } else {
        EngineOperationGate.Allow
    }

internal fun evaluateScoringRuleChangeGate(
    currentRuleset: Ruleset,
    nextRuleset: Ruleset,
    isEngineBusy: Boolean,
): EngineOperationGate =
    when {
        nextRuleset == currentRuleset -> EngineOperationGate.NoOp
        isEngineBusy -> EngineOperationGate.Block("Engine is busy. Change scoring rule after the current response.")
        else -> EngineOperationGate.Allow
    }
