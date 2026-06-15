package com.worksoc.goaicoach.application.savedgame

internal interface SavedGameStorePort {
    fun save(snapshot: SavedGameSnapshot)
    fun load(): SavedGameSnapshot?
    fun clear()
}
