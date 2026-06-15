package com.worksoc.goaicoach.application.startgame

import com.worksoc.goaicoach.application.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.selectRuntimePlayLevel
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor

internal data class GameSessionResetPlan(
    val gameState: GameState,
    val candidateText: String,
    val reviewAnalysis: MoveAnalysisSnapshot,
    val scoreText: String,
    val scoreSnapshots: List<ScoreSnapshot>,
    val moveReviewText: String,
    val lastMoveText: String,
    val endgameLog: String,
    val engineMessage: String,
)

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

internal fun buildNewLocalGameSessionPlan(
    message: String,
    ruleset: Ruleset,
): GameSessionResetPlan {
    val state = GameState.empty(BoardSize.Nine, ruleset)
    return GameSessionResetPlan(
        gameState = state,
        candidateText = "No analysis yet.",
        reviewAnalysis = MoveAnalysisSnapshot.empty(state),
        scoreText = "No score estimate yet.",
        scoreSnapshots = listOf(localScoreSnapshot(state)),
        moveReviewText = "No move review yet.",
        lastMoveText = "None",
        endgameLog = "No endgame result recorded.",
        engineMessage = message,
    )
}

internal fun buildStartConfiguredGamePlan(
    setup: PlayerSetup,
    ruleset: Ruleset,
    nextPlayer: StoneColor,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    currentProfile: EngineProfile,
    defaultPlayLevel: PlayLevelSetting,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
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
            searchTimeSettings = searchTimeSettings,
        ),
    )
}
