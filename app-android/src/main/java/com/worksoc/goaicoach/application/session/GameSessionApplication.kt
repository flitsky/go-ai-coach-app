package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor

internal data class RuntimePlayLevelSelection(
    val playLevel: PlayLevelSetting,
    val engineProfile: EngineProfile,
    val analysisPreset: AnalysisPreset,
    val searchTimeSettings: SearchTimeSettings,
)

internal sealed class PlayerSetupChangePlan {
    data class ShowMessage(val message: String) : PlayerSetupChangePlan()
    data class Apply(
        val playerSetup: PlayerSetup,
        val runtime: RuntimePlayLevelSelection,
        val reviewAnalysis: MoveAnalysisSnapshot,
        val topMoveClearMessage: String,
    ) : PlayerSetupChangePlan()
}

internal fun selectPrimaryPlayLevel(
    setup: PlayerSetup,
    nextPlayer: StoneColor,
    defaultPlayLevel: PlayLevelSetting,
): PlayLevelSetting =
    setup.seatFor(nextPlayer).aiCharacter?.playLevel
        ?: setup.seats().mapNotNull { seat -> seat.aiCharacter?.playLevel }.firstOrNull()
        ?: defaultPlayLevel

internal fun selectRuntimePlayLevel(
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

internal fun buildPlayerSetupChangePlan(
    nextSetup: PlayerSetup,
    currentState: GameState,
    currentProfile: EngineProfile,
    defaultPlayLevel: PlayLevelSetting,
    isEngineBusy: Boolean,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
): PlayerSetupChangePlan {
    if (isEngineBusy) {
        return PlayerSetupChangePlan.ShowMessage("Engine is busy. Change Player Setup after the current action.")
    }

    return PlayerSetupChangePlan.Apply(
        playerSetup = nextSetup,
        runtime = selectRuntimePlayLevel(
            setup = nextSetup,
            nextPlayer = currentState.nextPlayer,
            currentProfile = currentProfile,
            defaultPlayLevel = defaultPlayLevel,
            searchTimeSettings = searchTimeSettings,
        ),
        reviewAnalysis = MoveAnalysisSnapshot.empty(currentState),
        topMoveClearMessage = "Player Setup changed. Press New to restart with this setup, or continue from the current position.",
    )
}
