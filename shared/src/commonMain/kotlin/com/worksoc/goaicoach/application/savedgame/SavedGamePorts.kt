package com.worksoc.goaicoach.application.savedgame

interface SavedGameStorePort {
    fun save(snapshot: SavedGameSnapshot)
    fun load(): SavedGameSnapshot?
    fun clear()
    fun readRawJson(): String?
}
