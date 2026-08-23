package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.time.currentEpochMillis

fun interface EngineClock {
    fun currentTimeMillis(): Long
}

object SystemEngineClock : EngineClock {
    override fun currentTimeMillis(): Long = currentEpochMillis()
}
