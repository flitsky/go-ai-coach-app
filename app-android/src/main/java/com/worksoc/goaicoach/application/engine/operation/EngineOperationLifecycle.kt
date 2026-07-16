package com.worksoc.goaicoach.application.engine.operation

internal data class EngineOperationLifecycleState(
    val activeOperations: Map<String, EngineOperationRequest> = emptyMap(),
) {
    val isEngineBusy: Boolean
        get() = activeOperations.isNotEmpty()

    val isBlockingBusy: Boolean
        get() = activeOperations.values.any { it.kind.isBlocking }

    val activityIndicator: EngineActivityIndicator?
        get() = when {
            activeOperations.values.any { it.kind in PreparingOperationKinds } -> EngineActivityIndicator.Preparing
            activeOperations.values.any { it.kind == EngineOperationKind.AutoAiTurn } -> EngineActivityIndicator.Thinking
            activeOperations.values.any { it.kind == EngineOperationKind.TopMoves } -> EngineActivityIndicator.Recommending
            else -> null
        }
}

internal enum class EngineActivityIndicator(
    val baseText: String,
) {
    Preparing("Preparing"),
    Recommending("Recommending"),
    Thinking("Thinking"),
    ;

    fun animatedText(frame: Int): String =
        baseText + ActivityIndicatorDots[frame.mod(ActivityIndicatorDots.size)]
}

private val ActivityIndicatorDots = listOf("", " .", " ..", " ...")

private val PreparingOperationKinds = setOf(
    EngineOperationKind.EngineStartup,
    EngineOperationKind.EngineNewGame,
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

        is EngineOperationLifecycleTransition.Completed ->
            state.copy(activeOperations = state.activeOperations - transition.operationId)

        EngineOperationLifecycleTransition.Reset ->
            EngineOperationLifecycleState()
    }
