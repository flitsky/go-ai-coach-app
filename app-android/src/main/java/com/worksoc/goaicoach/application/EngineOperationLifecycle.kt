package com.worksoc.goaicoach.application

internal enum class EngineOperationLifecycleTransition {
    Started,
    Completed,
}

/**
 * Single reducer for UI-visible engine busy transitions.
 *
 * It is intentionally small today. Keeping the transition behind an
 * application-layer reducer gives us one place to evolve toward operation-id
 * scoped busy stacks or concurrent operation counters when remote engine
 * drivers arrive.
 */
internal fun applyEngineOperationLifecycleTransition(
    isEngineBusy: Boolean,
    transition: EngineOperationLifecycleTransition,
): Boolean =
    when (transition) {
        EngineOperationLifecycleTransition.Started -> true
        EngineOperationLifecycleTransition.Completed -> false
    }
