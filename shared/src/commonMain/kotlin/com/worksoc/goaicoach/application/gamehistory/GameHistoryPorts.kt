package com.worksoc.goaicoach.application.gamehistory

interface GameHistoryStorePort {
    fun appendCompletedGame(entry: GameHistoryEntry)
    fun loadAll(): List<GameHistoryEntry>
}
