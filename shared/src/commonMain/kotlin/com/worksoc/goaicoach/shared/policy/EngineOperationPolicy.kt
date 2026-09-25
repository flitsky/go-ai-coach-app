package com.worksoc.goaicoach.shared.policy

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.analysisFingerprint

/**
 * 엔진 작업이 **막힌 이유**를 타입으로 부른다.
 *
 * ## ⚠️ 왜 문자열만으로는 안 되는가
 * [EngineOperationGate.Block.message]는 **진단용 영어**다 — 디버그 리포트와 런타임 로그가 읽는다.
 * 그 문장을 그대로 화면에 띄우면 한국어 앱이 영어로 말하게 되고, 문장을 손보는 순간 UI가 함께
 * 바뀐다. **사용자에게 보이는 말은 이 열거형을 보고 4개 언어에서 고른다**(`UiStrings`).
 *
 * ⚠️ **상수 이름을 저장에 쓰지 말 것.** 이 값은 화면에만 살고 저장소를 지나지 않는다 —
 * 지나게 만드는 순간 함정 1번(enum 상수 이름 = 저장 포맷)에 걸린다.
 */
enum class EngineOperationBlockReason {
    /** 엔진이 아직 뜨지 않았다. 시간이 지나면 풀린다 — 「기조 1ⓒ」의 *"아직"* 쪽이다. */
    EngineNotReady,

    /** 이 엔진 구현이 기기 벤치마크를 지원하지 않는다(원격·스텁). 기다려도 풀리지 않는다. */
    BenchmarkUnsupported,

    /** 엔진이 지금 다른 일을 하고 있다. 그 응답이 끝나면 풀린다. */
    EngineBusy,
}

sealed class EngineOperationGate {
    data object Allow : EngineOperationGate()
    data object NoOp : EngineOperationGate()

    /**
     * @property message 진단용 영어 문장(디버그 리포트·런타임 로그).
     * @property reason 화면이 번역해서 보여줄 **타입**. ⚠️ 기본값을 주지 말 것 — 새 차단 사유를
     *   더하는 사람이 조용히 빠뜨리면, 그 경우만 다시 침묵한다(2026-09-10에 벤치마크가 그랬다).
     */
    data class Block(
        val message: String,
        val reason: EngineOperationBlockReason,
    ) : EngineOperationGate()
}

data class PositionScopedOperationToken(
    val kind: String,
    val positionFingerprint: String,
    val moveCount: Int,
)

/**
 * ⚠️ `internal`이다(refactor backlog #97). 쓰는 곳은 이 파일과 `application.*`뿐이다. `internal`은 모듈 경계라서
 * 모듈 승격(#49) 때 이 열거형·[EngineFallbackPolicy]·[EngineOperationRequest]의 `internal` 멤버·`engineOperationRequest`를
 * `application`과 **다른 모듈**로 보내면 컴파일이 깨진다 — `:core:application`에 둔다.
 */
internal enum class EngineOperationKind(
    val code: String,
) {
    EngineStartup("engine_startup"),
    EngineNewGame("engine_new_game"),
    PositionAnalysis("position_analysis"),
    TopMoves("top_moves"),
    ScoreEstimate("score_estimate"),
    ScoringRuleSync("scoring_rule_sync"),
    AutoAiTurn("auto_ai_turn"),
    AutoAiEndgame("auto_ai_endgame"),
    HumanMoveSync("human_move_sync"),
    RestoredGameSync("restored_game_sync"),
    PostUndoSync("post_undo_sync"),
    EngineUndo("engine_undo"),
    StartupBenchmark("startup_benchmark"),
    PositionCacheOptimization("position_cache_optimization"),
    RemotePositionAnalysis("remote_position_analysis"),
}

data class EngineTimeoutPolicy(
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
 *
 * ⚠️ **타입은 public, 만들기와 [kind]·[fallbackPolicy]는 `internal`이다**(refactor backlog #97).
 * app-android가 만드는 public 컨트롤러들이 `launchEngineOperation: (EngineOperationRequest, …) -> Unit`을
 * 받으므로 타입은 보여야 한다. 하지만 app-android는 이 값을 받아 넘기기만 하고 만들거나 종류를 읽지 않으니,
 * 두 열거형([EngineOperationKind]·[EngineFallbackPolicy])은 `:shared` 밖으로 새지 않는다.
 * `@ConsistentCopyVisibility`는 `copy()`도 생성자와 같은 `internal`로 둔다.
 */
@ConsistentCopyVisibility
data class EngineOperationRequest internal constructor(
    val operationId: String,
    internal val kind: EngineOperationKind,
    val sessionGeneration: Long,
    val boardFingerprint: String,
    val moveCount: Int,
    val timeoutPolicy: EngineTimeoutPolicy,
    internal val fallbackPolicy: EngineFallbackPolicy,
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

sealed class EngineOperationResultGuard {
    data object Apply : EngineOperationResultGuard()
    data class Discard(
        val reason: String,
        val operation: String? = null,
        val operationId: String? = null,
        val sessionGeneration: Long? = null,
    ) : EngineOperationResultGuard()
}

sealed class EngineOperationApplyPlan {
    data object Apply : EngineOperationApplyPlan()
    data class Discard(val discard: EngineOperationResultGuard.Discard) : EngineOperationApplyPlan()
}

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

fun EngineOperationRequest.toPositionScopedOperationToken(): PositionScopedOperationToken =
    PositionScopedOperationToken(
        kind = kind.code,
        positionFingerprint = boardFingerprint,
        moveCount = moveCount,
    )

fun evaluatePositionScopedResultGuard(
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

fun evaluateEngineOperationResultGuard(
    request: EngineOperationRequest,
    currentState: GameState,
    currentSessionGeneration: Long,
): EngineOperationResultGuard {
    if (currentSessionGeneration != request.sessionGeneration) {
        return EngineOperationResultGuard.Discard(
            reason = "${request.kind.code} result is stale: requested generation=${request.sessionGeneration}, current generation=$currentSessionGeneration.",
            operation = request.kind.code,
            operationId = request.operationId,
            sessionGeneration = request.sessionGeneration,
        )
    }
    return when (
        val guard = evaluatePositionScopedResultGuard(
            token = request.toPositionScopedOperationToken(),
            currentState = currentState,
        )
    ) {
        EngineOperationResultGuard.Apply -> EngineOperationResultGuard.Apply
        is EngineOperationResultGuard.Discard -> guard.copy(
            operation = request.kind.code,
            operationId = request.operationId,
            sessionGeneration = request.sessionGeneration,
        )
    }
}

private fun defaultEngineOperationId(
    kind: EngineOperationKind,
    state: GameState,
    sessionGeneration: Long,
): String =
    "${kind.code}:g$sessionGeneration:m${state.moves.size}:${state.analysisFingerprint().take(12)}"

fun buildEngineOperationApplyPlan(
    request: EngineOperationRequest,
    currentState: GameState,
    currentSessionGeneration: Long,
): EngineOperationApplyPlan =
    when (
        val guard = evaluateEngineOperationResultGuard(
            request = request,
            currentState = currentState,
            currentSessionGeneration = currentSessionGeneration,
        )
    ) {
        EngineOperationResultGuard.Apply -> EngineOperationApplyPlan.Apply
        is EngineOperationResultGuard.Discard -> EngineOperationApplyPlan.Discard(guard)
    }

internal fun evaluateEngineBenchmarkGate(
    isEngineReady: Boolean,
    supportsDeviceBenchmark: Boolean,
    isEngineBusy: Boolean,
    isBenchmarkRunning: Boolean,
): EngineOperationGate =
    when {
        !isEngineReady ->
            EngineOperationGate.Block(
                message = "Engine benchmark requires a ready local engine.",
                reason = EngineOperationBlockReason.EngineNotReady,
            )

        !supportsDeviceBenchmark ->
            EngineOperationGate.Block(
                message = "Engine benchmark is available only for the local KataGo process engine.",
                reason = EngineOperationBlockReason.BenchmarkUnsupported,
            )

        isEngineBusy || isBenchmarkRunning ->
            EngineOperationGate.Block(
                message = "Engine is busy. Run benchmark after the current response.",
                reason = EngineOperationBlockReason.EngineBusy,
            )

        else -> EngineOperationGate.Allow
    }

fun evaluateScoringRuleChangeGate(
    currentRuleset: Ruleset,
    nextRuleset: Ruleset,
    isEngineBusy: Boolean,
): EngineOperationGate =
    when {
        nextRuleset == currentRuleset -> EngineOperationGate.NoOp
        isEngineBusy -> EngineOperationGate.Block(
            message = "Engine is busy. Change scoring rule after the current response.",
            reason = EngineOperationBlockReason.EngineBusy,
        )
        else -> EngineOperationGate.Allow
    }
