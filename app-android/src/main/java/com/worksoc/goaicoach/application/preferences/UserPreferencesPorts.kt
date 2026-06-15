package com.worksoc.goaicoach.application.preferences

internal interface UserPreferencesStorePort {
    fun save(snapshot: UserPreferencesSnapshot)
    fun load(): UserPreferencesSnapshot
}
