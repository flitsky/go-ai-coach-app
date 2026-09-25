package com.worksoc.goaicoach.application.contract

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings

data class RuntimePlayLevelSelection(
    val playLevel: PlayLevelSetting,
    val engineProfile: EngineProfile,
    val analysisPreset: AnalysisPreset,
    val searchTimeSettings: SearchTimeSettings,
)

internal fun selectPrimaryPlayLevel(
    setup: PlayerSetup,
    nextPlayer: StoneColor,
    defaultPlayLevel: PlayLevelSetting,
): PlayLevelSetting =
    setup.seatFor(nextPlayer).aiCharacter?.playLevel
        ?: setup.seats().mapNotNull { seat -> seat.aiCharacter?.playLevel }.firstOrNull()
        ?: defaultPlayLevel

fun selectRuntimePlayLevel(
    setup: PlayerSetup,
    nextPlayer: StoneColor,
    currentProfile: EngineProfile,
    defaultPlayLevel: PlayLevelSetting,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
): RuntimePlayLevelSelection {
    val playLevel = selectPrimaryPlayLevel(
        setup = setup,
        nextPlayer = nextPlayer,
        defaultPlayLevel = defaultPlayLevel,
    )
    return RuntimePlayLevelSelection(
        playLevel = playLevel,
        engineProfile = playLevel.toEngineProfile(currentProfile, searchTimeSettings),
        analysisPreset = playLevel.analysisPreset,
        searchTimeSettings = searchTimeSettings.normalized(),
    )
}
