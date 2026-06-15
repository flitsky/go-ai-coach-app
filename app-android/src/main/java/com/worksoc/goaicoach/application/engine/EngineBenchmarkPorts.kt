package com.worksoc.goaicoach.application.engine

internal interface EngineBenchmarkStorePort {
    fun exists(): Boolean

    fun hasUsableProfile(
        samplesPerVisit: Int,
        timeCapMs: Long,
        measurementVersion: Int,
        visitsTargets: List<Int>,
    ): Boolean

    fun save(profile: EngineBenchmarkProfile)
    fun load(): EngineBenchmarkProfile?
    fun loadText(): String
    fun path(): String
}
