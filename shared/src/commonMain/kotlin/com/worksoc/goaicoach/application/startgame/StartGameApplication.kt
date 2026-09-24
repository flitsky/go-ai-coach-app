package com.worksoc.goaicoach.application.startgame

import com.worksoc.goaicoach.application.contract.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.contract.selectRuntimePlayLevel
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.domain.StoneColor

sealed class StartConfiguredGamePlan {
    data class ShowMessage(val message: String) : StartConfiguredGamePlan()
    data class ResetLocalGame(
        val message: String,
        val ruleset: Ruleset,
        val boardSize: BoardSize,
        val handicapCount: Int = 0,
        val komi: Double = com.worksoc.goaicoach.shared.domain.DefaultKomi,
    ) : StartConfiguredGamePlan()
    data class StartEngineGame(
        val ruleset: Ruleset,
        val boardSize: BoardSize,
        val runtime: RuntimePlayLevelSelection,
        val handicapCount: Int = 0,
        val komi: Double = com.worksoc.goaicoach.shared.domain.DefaultKomi,
    ) : StartConfiguredGamePlan()
}

fun buildStartConfiguredGamePlan(
    setup: PlayerSetup,
    boardSize: BoardSize,
    ruleset: Ruleset,
    nextPlayer: StoneColor,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    currentProfile: EngineProfile,
    defaultPlayLevel: PlayLevelSetting,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    handicapCount: Int = 0,
    komi: Double = com.worksoc.goaicoach.shared.domain.DefaultKomi,
): StartConfiguredGamePlan {
    val targetMode = setup.matchMode()
    if (!isEngineReady && targetMode != MatchMode.LocalTwoPlayer) {
        return StartConfiguredGamePlan.ResetLocalGame(
            message = "Player Setup includes AI, but engine is not ready.",
            ruleset = ruleset,
            boardSize = boardSize,
            handicapCount = handicapCount,
            komi = komi,
        )
    }
    if (isEngineBusy) {
        return StartConfiguredGamePlan.ShowMessage("Engine is busy. Start a new game after the current action.")
    }
    if (!isEngineReady && targetMode == MatchMode.LocalTwoPlayer) {
        return StartConfiguredGamePlan.ResetLocalGame(
            message = "Local two-player game. Engine analysis is not connected.",
            ruleset = ruleset,
            boardSize = boardSize,
            handicapCount = handicapCount,
            komi = komi,
        )
    }

    return StartConfiguredGamePlan.StartEngineGame(
        ruleset = ruleset,
        boardSize = boardSize,
        runtime = selectRuntimePlayLevel(
            setup = setup,
            nextPlayer = nextPlayer,
            currentProfile = currentProfile,
            defaultPlayLevel = defaultPlayLevel,
            searchTimeSettings = searchTimeSettings,
        ),
        handicapCount = handicapCount,
        komi = komi,
    )
}
