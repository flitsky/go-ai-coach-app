package com.worksoc.goaicoach.application.preferences

interface UserPreferencesStorePort {
    fun save(snapshot: UserPreferencesSnapshot)
    fun load(): UserPreferencesSnapshot
}
