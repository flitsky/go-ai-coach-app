package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings

internal data class UserPreferencesSnapshot(
    val boardSize: BoardSize = BoardSize.Nine,
    val playerSetup: PlayerSetup = PlayerSetup(),
    val ruleset: Ruleset = Ruleset.Japanese,
    val topMovesEnabled: Boolean = false,
    val showCoordinates: Boolean = true,
    val showMoveNumbers: Boolean = false,
    val showLastMoveRing: Boolean = true,
    val showOwnershipOverlay: Boolean = true,
    val autoPlayDelayMillis: Long = AutoPlayDelaySetting.Default.millis,
    val searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
)
