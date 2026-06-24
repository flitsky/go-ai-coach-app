package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.application.session.*
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings

internal data class GameSettings(
    val boardSize: BoardSize,
    val ruleset: Ruleset,
    val topMovesEnabled: Boolean,
    val autoPlayDelaySetting: AutoPlayDelaySetting,
    val searchTimeSettings: SearchTimeSettings,
)

internal data class InitialUserPreferencesPlan(
    val gameState: GameState,
    val playerSetup: PlayerSetup,
    val runtime: RuntimePlayLevelSelection,
    val settings: GameSettings,
) {
    val topMovesEnabled: Boolean
        get() = settings.topMovesEnabled

    val autoPlayDelaySetting: AutoPlayDelaySetting
        get() = settings.autoPlayDelaySetting
}

internal fun buildInitialUserPreferencesPlan(
    preferences: UserPreferencesSnapshot,
    defaultPlayLevel: PlayLevelSetting,
    currentProfile: EngineProfile,
): InitialUserPreferencesPlan {
    val settings = preferences.toGameSettings()
    val state = GameState.empty(preferences.boardSize, settings.ruleset)
    return InitialUserPreferencesPlan(
        gameState = state,
        playerSetup = preferences.playerSetup,
        runtime = selectRuntimePlayLevel(
            setup = preferences.playerSetup,
            nextPlayer = state.nextPlayer,
            currentProfile = currentProfile,
            defaultPlayLevel = defaultPlayLevel,
            searchTimeSettings = settings.searchTimeSettings,
        ),
        settings = settings,
    )
}

internal fun buildUserPreferencesSnapshot(
    playerSetup: PlayerSetup,
    boardSize: BoardSize,
    ruleset: Ruleset,
    topMovesEnabled: Boolean,
    showCoordinates: Boolean,
    showMoveNumbers: Boolean,
    showLastMoveRing: Boolean,
    showOwnershipOverlay: Boolean,
    autoPlayDelaySetting: AutoPlayDelaySetting,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    isDirectPlayEnabled: Boolean = true,
): UserPreferencesSnapshot =
    buildGameSettings(
        boardSize = boardSize,
        ruleset = ruleset,
        topMovesEnabled = topMovesEnabled,
        autoPlayDelaySetting = autoPlayDelaySetting,
        searchTimeSettings = searchTimeSettings,
    ).toUserPreferencesSnapshot(
        playerSetup = playerSetup,
        showCoordinates = showCoordinates,
        showMoveNumbers = showMoveNumbers,
        showLastMoveRing = showLastMoveRing,
        showOwnershipOverlay = showOwnershipOverlay,
        isDirectPlayEnabled = isDirectPlayEnabled,
    )

internal fun buildUserPreferencesSnapshot(
    settingsState: GameSessionSettingsState,
    ruleset: Ruleset,
    showCoordinates: Boolean,
    showMoveNumbers: Boolean,
    showLastMoveRing: Boolean,
    showOwnershipOverlay: Boolean,
    isDirectPlayEnabled: Boolean = true,
): UserPreferencesSnapshot =
    buildUserPreferencesSnapshot(
        playerSetup = settingsState.playerSetup,
        boardSize = settingsState.boardSize,
        ruleset = ruleset,
        topMovesEnabled = settingsState.topMovesEnabled,
        showCoordinates = showCoordinates,
        showMoveNumbers = showMoveNumbers,
        showLastMoveRing = showLastMoveRing,
        showOwnershipOverlay = showOwnershipOverlay,
        autoPlayDelaySetting = settingsState.autoPlayDelaySetting,
        searchTimeSettings = settingsState.searchTimeSettings,
        isDirectPlayEnabled = isDirectPlayEnabled,
    )

internal fun buildGameSettings(
    boardSize: BoardSize,
    ruleset: Ruleset,
    topMovesEnabled: Boolean,
    autoPlayDelaySetting: AutoPlayDelaySetting,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
): GameSettings =
    GameSettings(
        boardSize = boardSize,
        ruleset = ruleset,
        topMovesEnabled = topMovesEnabled,
        autoPlayDelaySetting = autoPlayDelaySetting,
        searchTimeSettings = searchTimeSettings.normalized(),
    )

private fun UserPreferencesSnapshot.toGameSettings(): GameSettings =
    buildGameSettings(
        boardSize = boardSize,
        ruleset = ruleset,
        topMovesEnabled = topMovesEnabled,
        autoPlayDelaySetting = AutoPlayDelaySetting.fromMillis(autoPlayDelayMillis),
        searchTimeSettings = searchTimeSettings,
    )

private fun GameSettings.toUserPreferencesSnapshot(
    playerSetup: PlayerSetup,
    showCoordinates: Boolean,
    showMoveNumbers: Boolean,
    showLastMoveRing: Boolean,
    showOwnershipOverlay: Boolean,
    isDirectPlayEnabled: Boolean,
): UserPreferencesSnapshot =
    UserPreferencesSnapshot(
        boardSize = boardSize,
        playerSetup = playerSetup,
        ruleset = ruleset,
        topMovesEnabled = topMovesEnabled,
        showCoordinates = showCoordinates,
        showMoveNumbers = showMoveNumbers,
        showLastMoveRing = showLastMoveRing,
        showOwnershipOverlay = showOwnershipOverlay,
        autoPlayDelayMillis = autoPlayDelaySetting.millis,
        searchTimeSettings = searchTimeSettings,
        isDirectPlayEnabled = isDirectPlayEnabled,
    )
