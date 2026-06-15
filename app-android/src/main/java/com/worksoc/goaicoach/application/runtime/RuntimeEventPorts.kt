package com.worksoc.goaicoach.application.runtime

internal interface RuntimeEventLogPort {
    fun append(
        event: String,
        nowMillis: Long = System.currentTimeMillis(),
    )

    fun readText(): String
    fun clear()
}
