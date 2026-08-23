package com.worksoc.goaicoach.application.concurrency

import platform.Foundation.NSLock

internal actual fun sharedLock(): SharedLock = NsSharedLock()

private class NsSharedLock : SharedLock {
    private val lock = NSLock()

    override fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
