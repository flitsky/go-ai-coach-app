package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
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

internal interface DebugReportMirrorPort {
    fun save(report: String)
}

internal interface ClipboardPort {
    fun setText(label: String, text: String)
}

internal interface UserNoticePort {
    fun showShort(message: String)
}
