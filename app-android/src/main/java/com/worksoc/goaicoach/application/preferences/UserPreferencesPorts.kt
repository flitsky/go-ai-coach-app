package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.persistence.UserPreferencesSnapshot

internal interface UserPreferencesStorePort {
    fun save(snapshot: UserPreferencesSnapshot)
    fun load(): UserPreferencesSnapshot
}
