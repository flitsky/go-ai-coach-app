package com.worksoc.goaicoach.application

internal data class EngineOperationLifecycleState(
    val activeOperationIds: Set<String> = emptySet(),
) {
    val isEngineBusy: Boolean
        get() = activeOperationIds.isNotEmpty()
}

internal sealed class EngineOperationLifecycleTransition {
    data class Started(
        val operationId: String,
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
            state.copy(activeOperationIds = state.activeOperationIds + transition.operationId)

        is EngineOperationLifecycleTransition.Completed ->
            state.copy(activeOperationIds = state.activeOperationIds - transition.operationId)

        EngineOperationLifecycleTransition.Reset ->
            EngineOperationLifecycleState()
    }
