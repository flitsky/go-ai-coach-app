package com.worksoc.goaicoach.application.engine.operation

internal data class EngineOperationLifecycleState(
    val activeOperations: Map<String, EngineOperationRequest> = emptyMap(),
    // "이번 차례 대기가 (결과와 무관하게) 끝났다"는 확정적 신호로 UI 쪽에서 관찰하기 위한 값.
    // AI 착수(AutoAiTurn), 사람 착수 뒤 동기화(HumanMoveSync — 양패스 이후 계가 처리도 이
    // 종류로 실행된다), AI 쪽 종국 처리(AutoAiEndgame)가 성공/실패/폐기(discard) 중 무엇으로
    // 끝나든 매번 증가한다. activityIndicator 자체를 관찰하면 실패 직후 즉시 재시도가 붙는
    // 경우 Compose 리컴포지션이 Thinking→null→Thinking 전이를 건너뛸 수 있어(같은 프레임 내
    // 연속 변경) 신뢰할 수 없다.
    val engineTurnWaitCompletionSeq: Int = 0,
) {
    // 세 함수 모두 currentSessionGeneration을 받아 그 세대의 작업만 센다 — 예를 들어 기권
    // 직후 사용자가 곧바로 새 대국을 시작하면, 기권을 엔진에 동기화하던 이전 세대의
    // human_move_sync가 아직 끝나지 않은 채로 activeOperations에 남아 있을 수 있다.
    // activeOperations.isNotEmpty()처럼 세대를 무시하고 세면, 그 작업이 뒤늦게 끝날 때까지
    // 새 대국의 isEngineBusy가 계속 true로 잡혀 AI 턴 예약이 조용히 취소되는 경쟁 상태가
    // 생긴다(실제 발생 사례로 확인됨).
    fun isEngineBusy(currentSessionGeneration: Long): Boolean =
        activeOperations.values.any { it.sessionGeneration == currentSessionGeneration }

    fun isBlockingBusy(currentSessionGeneration: Long): Boolean =
        activeOperations.values.any { it.sessionGeneration == currentSessionGeneration && it.kind.isBlocking }

    fun activityIndicator(currentSessionGeneration: Long): EngineActivityIndicator? {
        val current = activeOperations.values.filter { it.sessionGeneration == currentSessionGeneration }
        return when {
            current.any { it.kind in PreparingOperationKinds } -> EngineActivityIndicator.Preparing
            current.any { it.kind == EngineOperationKind.AutoAiTurn } -> EngineActivityIndicator.Thinking
            current.any { it.kind == EngineOperationKind.TopMoves } -> EngineActivityIndicator.Recommending
            current.any { it.kind == EngineOperationKind.PositionCacheOptimization } -> EngineActivityIndicator.Optimizing
            else -> null
        }
    }
}

/** 표시 문구는 언어별로 다르므로 UI 레이어(`UiStrings`)에서 라벨링한다 — 여기서는 상태값만 든다. */
enum class EngineActivityIndicator {
    Preparing,
    Recommending,
    Thinking,
    Optimizing,
}

private val PreparingOperationKinds = setOf(
    EngineOperationKind.EngineStartup,
    EngineOperationKind.EngineNewGame,
)

// 엔진 응답 지연 와치독 팝업이 "이번 차례 대기"로 취급하는 작업 종류. AI 착수(AutoAiTurn) 외에도
// 사람의 착수(양패스로 계가에 들어가는 경우 포함) 뒤 엔진 동기화(HumanMoveSync)와 AI 쪽 종국
// 처리(AutoAiEndgame)도 같은 대기의 일부이므로 여기서 끝나야 팝업이 닫힌다.
private val TurnWaitOperationKinds = setOf(
    EngineOperationKind.AutoAiTurn,
    EngineOperationKind.HumanMoveSync,
    EngineOperationKind.AutoAiEndgame,
)

internal val EngineOperationKind.isBlocking: Boolean
    get() = when (this) {
        EngineOperationKind.TopMoves,
        EngineOperationKind.ScoreEstimate,
        EngineOperationKind.PositionCacheOptimization -> false
        else -> true
    }

internal sealed class EngineOperationLifecycleTransition {
    data class Started(
        val request: EngineOperationRequest,
    ) : EngineOperationLifecycleTransition()

    data class Completed(
        val operationId: String,
    ) : EngineOperationLifecycleTransition()

    data object Reset : EngineOperationLifecycleTransition()
}

/**
 * Single reducer for UI-visible engine busy transitions.
 *
 * Keeping operation ids in the lifecycle model lets local and future remote
 * engine calls overlap without one late completion incorrectly hiding another
 * active engine operation.
 */
internal fun applyEngineOperationLifecycleTransition(
    state: EngineOperationLifecycleState,
    transition: EngineOperationLifecycleTransition,
): EngineOperationLifecycleState =
    when (transition) {
        is EngineOperationLifecycleTransition.Started ->
            state.copy(activeOperations = state.activeOperations + (transition.request.operationId to transition.request))

        is EngineOperationLifecycleTransition.Completed -> {
            val completedKind = state.activeOperations[transition.operationId]?.kind
            state.copy(
                activeOperations = state.activeOperations - transition.operationId,
                engineTurnWaitCompletionSeq = if (completedKind in TurnWaitOperationKinds) {
                    state.engineTurnWaitCompletionSeq + 1
                } else {
                    state.engineTurnWaitCompletionSeq
                },
            )
        }

        EngineOperationLifecycleTransition.Reset ->
            EngineOperationLifecycleState()
    }
