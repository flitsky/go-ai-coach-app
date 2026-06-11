package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe

internal data class RuntimePlayLevelSelection(
    val playLevel: PlayLevelSetting,
    val engineProfile: EngineProfile,
    val analysisPreset: AnalysisPreset,
    val searchTimeSettings: SearchTimeSettings,
)

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

internal data class SavedGameRestorePlan(
    val gameState: GameState,
    val playerSetup: PlayerSetup,
    val runtime: RuntimePlayLevelSelection,
    val topMovesEnabled: Boolean,
    val candidateText: String,
    val reviewAnalysis: MoveAnalysisSnapshot,
    val scoreText: String,
    val scoreSnapshots: List<ScoreSnapshot>,
    val moveReviewText: String,
    val lastMoveText: String,
    val endgameLog: String,
    val engineMessage: String,
)

internal sealed class SavedGameRestoreRequestPlan {
    data class ShowMessage(val message: String) : SavedGameRestoreRequestPlan()
    data class Restore(
        val restore: SavedGameRestorePlan,
        val syncEngineAfterRestore: Boolean,
    ) : SavedGameRestoreRequestPlan()
}

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
    setup.sideFor(nextPlayer)
        .takeIf { side -> side.controller == SeatController.Ai }
        ?.playLevel
        ?: setup.black.takeIf { side -> side.controller == SeatController.Ai }?.playLevel
        ?: setup.white.takeIf { side -> side.controller == SeatController.Ai }?.playLevel
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

internal fun buildSavedGameRestorePlan(
    snapshot: SavedGameSnapshot,
    currentProfile: EngineProfile,
    defaultPlayLevel: PlayLevelSetting,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
): SavedGameRestorePlan {
    val state = snapshot.gameState
    val runtime = selectRuntimePlayLevel(
        setup = snapshot.playerSetup,
        nextPlayer = state.nextPlayer,
        currentProfile = currentProfile,
        defaultPlayLevel = defaultPlayLevel,
        searchTimeSettings = searchTimeSettings,
    )
    return SavedGameRestorePlan(
        gameState = state,
        playerSetup = snapshot.playerSetup,
        runtime = runtime,
        topMovesEnabled = snapshot.topMovesEnabled,
        candidateText = "Restored previous game. Analysis cache will rebuild.",
        reviewAnalysis = MoveAnalysisSnapshot.empty(state),
        scoreText = "Score estimate not current.",
        scoreSnapshots = listOf(localScoreSnapshot(state)),
        moveReviewText = "Move review restored after app restart; pre-move analysis cache will rebuild.",
        lastMoveText = state.moves.lastOrNull()?.describe(state.boardSize) ?: "None",
        endgameLog = "No endgame result recorded after restore.",
        engineMessage = "Previous game restored at move ${state.moves.size}.",
    )
}

internal fun buildSavedGameRestoreRequestPlan(
    snapshot: SavedGameSnapshot,
    currentProfile: EngineProfile,
    defaultPlayLevel: PlayLevelSetting,
    isEngineBusy: Boolean,
    isEngineReady: Boolean,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
): SavedGameRestoreRequestPlan {
    if (isEngineBusy) {
        return SavedGameRestoreRequestPlan.ShowMessage(
            "Engine is busy. Restore the saved game after the current action.",
        )
    }

    return SavedGameRestoreRequestPlan.Restore(
        restore = buildSavedGameRestorePlan(
            snapshot = snapshot,
            currentProfile = currentProfile,
            defaultPlayLevel = defaultPlayLevel,
            searchTimeSettings = searchTimeSettings,
        ),
        syncEngineAfterRestore = isEngineReady,
    )
}
