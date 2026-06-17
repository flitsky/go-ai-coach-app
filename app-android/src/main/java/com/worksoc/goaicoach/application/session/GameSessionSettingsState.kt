package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.preferences.InitialUserPreferencesPlan
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.SearchTimeSettings

internal data class GameSessionSettingsState(
    val boardSize: BoardSize,
    val playerSetup: PlayerSetup,
    val autoPlayDelaySetting: AutoPlayDelaySetting,
    val searchTimeSettings: SearchTimeSettings,
    val topMovesEnabled: Boolean,
) {
    val matchMode: MatchMode
        get() = playerSetup.matchMode()

    fun applyPlayerSetup(nextSetup: PlayerSetup): GameSessionSettingsState =
        copy(playerSetup = nextSetup)

    fun applyBoardSize(size: BoardSize): GameSessionSettingsState =
        copy(boardSize = size)

    fun applySavedGameRestore(
        restoredSetup: PlayerSetup,
        restoredTopMovesEnabled: Boolean,
    ): GameSessionSettingsState =
        copy(
            playerSetup = restoredSetup,
            topMovesEnabled = restoredTopMovesEnabled,
        )

    fun applyAutoPlayDelay(setting: AutoPlayDelaySetting): GameSessionSettingsState =
        copy(autoPlayDelaySetting = setting)

    fun applySearchTimeSettings(settings: SearchTimeSettings): GameSessionSettingsState =
        copy(searchTimeSettings = settings.normalized())

    fun showTopMoves(): GameSessionSettingsState =
        copy(topMovesEnabled = true)

    fun hideTopMoves(): GameSessionSettingsState =
        copy(topMovesEnabled = false)
}

internal fun InitialUserPreferencesPlan.toGameSessionSettingsState(): GameSessionSettingsState =
    GameSessionSettingsState(
        boardSize = settings.boardSize,
        playerSetup = playerSetup,
        autoPlayDelaySetting = settings.autoPlayDelaySetting,
        searchTimeSettings = settings.searchTimeSettings.normalized(),
        topMovesEnabled = settings.topMovesEnabled,
    )
