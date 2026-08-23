package com.worksoc.goaicoach.application.concurrency

internal actual fun sharedLock(): SharedLock = MonitorSharedLock()

private class MonitorSharedLock : SharedLock {
    private val monitor = Any()

    override fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
