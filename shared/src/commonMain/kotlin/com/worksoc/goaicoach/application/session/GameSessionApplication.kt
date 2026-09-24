package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.contract.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.contract.selectRuntimePlayLevel
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.policy.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings

sealed class PlayerSetupChangePlan {
    data class ShowMessage(val message: String) : PlayerSetupChangePlan()
    data class Apply(
        val playerSetup: PlayerSetup,
        val runtime: RuntimePlayLevelSelection,
        val reviewAnalysis: MoveAnalysisSnapshot,
        val topMoveClearMessage: String,
    ) : PlayerSetupChangePlan()
}

fun buildPlayerSetupChangePlan(
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
