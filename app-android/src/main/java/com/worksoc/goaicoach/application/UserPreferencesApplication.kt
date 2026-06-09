package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.persistence.UserPreferencesSnapshot
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset

internal data class InitialUserPreferencesPlan(
    val gameState: GameState,
    val playerSetup: PlayerSetup,
    val runtime: RuntimePlayLevelSelection,
    val topMovesEnabled: Boolean,
)

internal fun buildInitialUserPreferencesPlan(
    preferences: UserPreferencesSnapshot,
    defaultPlayLevel: PlayLevelSetting,
    currentProfile: EngineProfile,
): InitialUserPreferencesPlan {
    val state = GameState.empty(BoardSize.Nine, preferences.ruleset)
    return InitialUserPreferencesPlan(
        gameState = state,
        playerSetup = preferences.playerSetup,
        runtime = selectRuntimePlayLevel(
            setup = preferences.playerSetup,
            nextPlayer = state.nextPlayer,
            currentProfile = currentProfile,
            defaultPlayLevel = defaultPlayLevel,
        ),
        topMovesEnabled = preferences.topMovesEnabled,
    )
}

internal fun buildUserPreferencesSnapshot(
    playerSetup: PlayerSetup,
    ruleset: Ruleset,
    topMovesEnabled: Boolean,
    showCoordinates: Boolean,
    showMoveNumbers: Boolean,
    showLastMoveRing: Boolean,
    showOwnershipOverlay: Boolean,
): UserPreferencesSnapshot =
    UserPreferencesSnapshot(
        playerSetup = playerSetup,
        ruleset = ruleset,
        topMovesEnabled = topMovesEnabled,
        showCoordinates = showCoordinates,
        showMoveNumbers = showMoveNumbers,
        showLastMoveRing = showLastMoveRing,
        showOwnershipOverlay = showOwnershipOverlay,
    )
