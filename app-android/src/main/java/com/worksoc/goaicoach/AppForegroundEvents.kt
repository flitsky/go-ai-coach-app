package com.worksoc.goaicoach

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Emits once every time the app process moves from background to foreground —
 * this covers both a cold start's first launch and every later resume from
 * background through a single hook ([GoAiCoachApplication] wires this to
 * `ProcessLifecycleOwner`'s `onStart`), rather than separately hooking
 * `Application.onCreate` and `Activity.onResume`, which would double-fire on
 * cold start.
 *
 * Consumers (e.g. the attendance check-in system) collect [events]; this
 * object itself has no opinion on what a foreground event should trigger.
 */
object AppForegroundEvents {
    private val mutableEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = mutableEvents.asSharedFlow()

    internal fun notifyForegrounded() {
        mutableEvents.tryEmit(Unit)
    }
}
