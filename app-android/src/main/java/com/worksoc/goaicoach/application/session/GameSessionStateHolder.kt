package com.worksoc.goaicoach.application.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Platform-independent owner of [GameSessionControllerState].
 *
 * This class deliberately depends on neither Compose nor Android so the whole
 * session state tree lives off the UI runtime. The Android UI mirrors [state]
 * into a Compose snapshot for recomposition, while orchestration code that needs
 * read-after-write semantics reads [current] (the synchronous source of truth).
 *
 * Keeping ownership here is the seam that lets the same orchestration back an
 * iOS/desktop UI later: only the thin mirror at the UI edge is platform-specific.
 */
internal class GameSessionStateHolder(initial: GameSessionControllerState) {
    private val _state = MutableStateFlow(initial)

    /** Observable state tree. UI layers collect this for recomposition. */
    val state: StateFlow<GameSessionControllerState> = _state.asStateFlow()

    /** Synchronous current value. Use for read-after-write within a callback. */
    val current: GameSessionControllerState
        get() = _state.value

    /**
     * Replaces the whole controller state atomically.
     *
     * [transform] must be pure: under contention it can be invoked more than
     * once (compare-and-set retry), so it must not carry side effects.
     */
    fun update(transform: (GameSessionControllerState) -> GameSessionControllerState) {
        _state.update(transform)
    }

    /** Atomically updates only the [GameSessionCoreState] slice, preserving the rest. */
    fun updateCore(transform: (GameSessionCoreState) -> GameSessionCoreState) {
        _state.update { current -> current.withCore(transform(current.core)) }
    }
}
