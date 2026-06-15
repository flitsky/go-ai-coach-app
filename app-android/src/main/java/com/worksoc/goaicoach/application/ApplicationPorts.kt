package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.persistence.UserPreferencesSnapshot

internal interface SavedGameStorePort {
    fun save(snapshot: SavedGameSnapshot)
    fun load(): SavedGameSnapshot?
    fun clear()
}

internal interface UserPreferencesStorePort {
    fun save(snapshot: UserPreferencesSnapshot)
    fun load(): UserPreferencesSnapshot
}

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

internal interface DebugReportMirrorPort {
    fun save(report: String)
}

internal interface ClipboardPort {
    fun setText(label: String, text: String)
}

internal interface UserNoticePort {
    fun showShort(message: String)
}
