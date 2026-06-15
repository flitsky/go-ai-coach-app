package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.InitialUserPreferencesPlan
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.SearchTimeSettings

internal data class GameSessionSettingsState(
    val playerSetup: PlayerSetup,
    val autoPlayDelaySetting: AutoPlayDelaySetting,
    val searchTimeSettings: SearchTimeSettings,
    val topMovesEnabled: Boolean,
) {
    val matchMode: MatchMode
        get() = playerSetup.matchMode()

    fun applyPlayerSetup(nextSetup: PlayerSetup): GameSessionSettingsState =
        copy(playerSetup = nextSetup)

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
        playerSetup = playerSetup,
        autoPlayDelaySetting = settings.autoPlayDelaySetting,
        searchTimeSettings = settings.searchTimeSettings.normalized(),
        topMovesEnabled = settings.topMovesEnabled,
    )
