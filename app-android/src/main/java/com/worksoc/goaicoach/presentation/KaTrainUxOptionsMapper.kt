package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot

internal fun UserPreferencesSnapshot.toKaTrainUxOptions(): KaTrainUxOptions =
    KaTrainUxOptions(
        showCoordinates = showCoordinates,
        showMoveNumbers = showMoveNumbers,
        showLastMoveRing = showLastMoveRing,
        showOwnershipOverlay = showOwnershipOverlay,
    )
