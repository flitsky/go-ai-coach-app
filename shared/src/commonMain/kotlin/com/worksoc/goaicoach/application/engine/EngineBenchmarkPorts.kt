package com.worksoc.goaicoach.application.engine

interface EngineBenchmarkStorePort {
    fun exists(): Boolean

    fun save(profile: EngineBenchmarkProfile)
    fun load(): EngineBenchmarkProfile?
    fun loadText(): String
    fun path(): String
}
