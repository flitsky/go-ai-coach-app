package com.worksoc.goaicoach.application.savedgame

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting

internal data class SavedGameSnapshot(
    val gameState: GameState,
    val playerSetup: PlayerSetup,
    val playLevel: PlayLevelSetting,
    val topMovesEnabled: Boolean,
    val savedAtMillis: Long,
) {
    val isResumable: Boolean =
        gameState.moves.isNotEmpty() && !gameState.hasConsecutivePasses() && !gameState.isBoardFull()
}
