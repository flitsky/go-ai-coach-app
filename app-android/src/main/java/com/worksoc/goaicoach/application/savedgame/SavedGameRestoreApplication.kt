package com.worksoc.goaicoach.application.savedgame

import com.worksoc.goaicoach.application.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.localScoreSnapshot
import com.worksoc.goaicoach.application.selectRuntimePlayLevel
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.describe

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
