package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot

internal fun UserPreferencesSnapshot.toKaTrainUxOptions(): KaTrainUxOptions =
    KaTrainUxOptions(
        showCoordinates = showCoordinates,
        showMoveNumbers = showMoveNumbers,
        showLastMoveRing = showLastMoveRing,
        showOwnershipOverlay = showOwnershipOverlay,
        isDirectPlayEnabled = isDirectPlayEnabled,
    )

/**
 * The "Eval" action button currently also drives the ownership-gradient overlay.
 * [onEvalGradientActivated] is the gradient-specific hook (today: requesting a fresh
 * score estimate) so the two concerns can be split into separate toggles later without
 * touching the on/off state transition itself.
 */
internal fun KaTrainUxOptions.applyEvalActivation(
    onEvalGradientActivated: () -> Unit,
): KaTrainUxOptions {
    val activated = !showOwnershipOverlay
    if (activated) {
        onEvalGradientActivated()
    }
    return copy(showOwnershipOverlay = activated)
}
