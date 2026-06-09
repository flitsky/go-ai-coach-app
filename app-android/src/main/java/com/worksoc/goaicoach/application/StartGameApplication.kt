package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor

internal sealed class StartConfiguredGamePlan {
    data class ShowMessage(val message: String) : StartConfiguredGamePlan()
    data class ResetLocalGame(
        val message: String,
        val ruleset: Ruleset,
    ) : StartConfiguredGamePlan()
    data class StartEngineGame(
        val ruleset: Ruleset,
        val runtime: RuntimePlayLevelSelection,
    ) : StartConfiguredGamePlan()
}

internal fun buildStartConfiguredGamePlan(
    setup: PlayerSetup,
    ruleset: Ruleset,
    nextPlayer: StoneColor,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    currentProfile: EngineProfile,
    defaultPlayLevel: PlayLevelSetting,
): StartConfiguredGamePlan {
    val targetMode = setup.matchMode()
    if (!isEngineReady && targetMode != MatchMode.LocalTwoPlayer) {
        return StartConfiguredGamePlan.ResetLocalGame(
            message = "Player Setup includes AI, but engine is not ready.",
            ruleset = ruleset,
        )
    }
    if (isEngineBusy) {
        return StartConfiguredGamePlan.ShowMessage("Engine is busy. Start a new game after the current action.")
    }
    if (!isEngineReady && targetMode == MatchMode.LocalTwoPlayer) {
        return StartConfiguredGamePlan.ResetLocalGame(
            message = "Local two-player game. Engine analysis is not connected.",
            ruleset = ruleset,
        )
    }

    return StartConfiguredGamePlan.StartEngineGame(
        ruleset = ruleset,
        runtime = selectRuntimePlayLevel(
            setup = setup,
            nextPlayer = nextPlayer,
            currentProfile = currentProfile,
            defaultPlayLevel = defaultPlayLevel,
        ),
    )
}
