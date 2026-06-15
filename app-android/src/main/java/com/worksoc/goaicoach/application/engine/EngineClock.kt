package com.worksoc.goaicoach.application.engine

internal fun interface EngineClock {
    fun currentTimeMillis(): Long
}

internal object SystemEngineClock : EngineClock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
