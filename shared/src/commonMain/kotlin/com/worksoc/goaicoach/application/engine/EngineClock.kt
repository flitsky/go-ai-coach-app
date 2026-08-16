package com.worksoc.goaicoach.application.engine

fun interface EngineClock {
    fun currentTimeMillis(): Long
}

object SystemEngineClock : EngineClock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
