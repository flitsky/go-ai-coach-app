package com.worksoc.goaicoach.application.runtime

interface RuntimeEventLogPort {
    fun append(
        event: String,
        nowMillis: Long = System.currentTimeMillis(),
    )

    fun readText(): String
    fun clear()
}
