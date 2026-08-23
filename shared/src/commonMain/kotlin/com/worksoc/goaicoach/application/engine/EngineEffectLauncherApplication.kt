package com.worksoc.goaicoach.application.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-service boundary for engine-side blocking work.
 *
 * Keeping the dispatcher choice here prevents the Compose UI entry point from
 * owning engine execution policy directly. Future variants can replace this
 * with an injected dispatcher or remote-engine executor. The dispatcher itself
 * is the one platform-specific bit ([engineIoDispatcher]).
 */
suspend fun <T> runEngineIo(block: suspend () -> T): T =
    withContext(engineIoDispatcher) {
        block()
    }

fun launchUiEffect(
    scope: CoroutineScope,
    block: suspend CoroutineScope.() -> Unit,
): Job =
    scope.launch(block = block)

fun launchAutoAiEffect(
    scope: CoroutineScope,
    block: suspend CoroutineScope.() -> Unit,
): Job =
    launchUiEffect(
        scope = scope,
        block = block,
    )
