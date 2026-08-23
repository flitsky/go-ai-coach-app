package com.worksoc.goaicoach.application.runtime

import com.worksoc.goaicoach.application.time.currentEpochMillis

interface RuntimeEventLogPort {
    fun append(
        event: String,
        nowMillis: Long = currentEpochMillis(),
    )

    fun readText(): String
    fun clear()
}
