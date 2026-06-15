package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.application.session.GameSessionSettingsState
import com.worksoc.goaicoach.shared.Ruleset

internal data class UserPreferencesAutosaveRequest(
    val settingsState: GameSessionSettingsState,
    val ruleset: Ruleset,
    val showCoordinates: Boolean,
    val showMoveNumbers: Boolean,
    val showLastMoveRing: Boolean,
    val showOwnershipOverlay: Boolean,
)

internal fun buildUserPreferencesAutosaveSnapshot(
    request: UserPreferencesAutosaveRequest,
): UserPreferencesSnapshot =
    buildUserPreferencesSnapshot(
        settingsState = request.settingsState,
        ruleset = request.ruleset,
        showCoordinates = request.showCoordinates,
        showMoveNumbers = request.showMoveNumbers,
        showLastMoveRing = request.showLastMoveRing,
        showOwnershipOverlay = request.showOwnershipOverlay,
    )

internal fun runUserPreferencesAutosave(
    request: UserPreferencesAutosaveRequest,
    store: UserPreferencesStorePort,
) {
    store.save(buildUserPreferencesAutosaveSnapshot(request))
}
