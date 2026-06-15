package com.worksoc.goaicoach.application.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App-service boundary for engine-side blocking work.
 *
 * Keeping the dispatcher choice here prevents the Compose UI entry point from
 * owning engine execution policy directly. Future variants can replace this
 * with an injected dispatcher or remote-engine executor.
 */
internal suspend fun <T> runEngineIo(block: suspend () -> T): T =
    withContext(Dispatchers.IO) {
        block()
    }
