package com.worksoc.goaicoach.application.engine.operation

internal data class EngineOperationLifecycleCallbacks(
    val onStarted: (EngineOperationRequest) -> Unit = {},
    val onCompleted: (EngineOperationRequest) -> Unit = {},
)

/**
 * Application-level execution envelope for engine-facing work.
 *
 * Slow/timeout diagnostics stay inside effect runners via
 * [runObservedEngineOperation]. This scope owns UI/application lifecycle
 * signalling only, so existing runners can be composed without duplicate
 * diagnostic events.
 */
internal class EngineOperationScope(
    val request: EngineOperationRequest,
    private val callbacks: EngineOperationLifecycleCallbacks = EngineOperationLifecycleCallbacks(),
) {
    suspend fun <T> run(block: suspend () -> T): T {
        callbacks.onStarted(request)
        try {
            return block()
        } finally {
            callbacks.onCompleted(request)
        }
    }
}

internal suspend fun <T> runEngineOperationInScope(
    request: EngineOperationRequest,
    callbacks: EngineOperationLifecycleCallbacks = EngineOperationLifecycleCallbacks(),
    block: suspend () -> T,
): T =
    EngineOperationScope(
        request = request,
        callbacks = callbacks,
    ).run(block)
