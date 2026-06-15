package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.persistence.UserPreferencesSnapshot
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent
import com.worksoc.goaicoach.shared.diagnostic.DiagnosticEventExternalExportPayload

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

internal interface RuntimeEventLogPort {
    fun append(
        event: String,
        nowMillis: Long = System.currentTimeMillis(),
    )

    fun readText(): String
    fun clear()
}

internal interface DiagnosticEventLogPort {
    fun append(
        event: DiagnosticEvent,
        nowMillis: Long = System.currentTimeMillis(),
    )

    fun readText(): String
    fun clear()
}

internal interface DiagnosticEventExternalSinkPort {
    fun send(payload: DiagnosticEventExternalExportPayload): Result<Unit>
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
