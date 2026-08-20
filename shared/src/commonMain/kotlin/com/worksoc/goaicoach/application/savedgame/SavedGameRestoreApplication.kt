package com.worksoc.goaicoach.application.savedgame

import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.session.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.session.selectRuntimePlayLevel
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.describe

data class SavedGameRestorePlan(
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

sealed class SavedGameRestoreRequestPlan {
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
        scoreSnapshots = snapshot.scoreSnapshots.takeIf { it.isNotEmpty() } ?: listOf(localScoreSnapshot(state)),
        moveReviewText = "Move review restored after app restart; pre-move analysis cache will rebuild.",
        lastMoveText = state.moves.lastOrNull()?.describe(state.boardSize) ?: "None",
        endgameLog = "No endgame result recorded after restore.",
        engineMessage = "Previous game restored at move ${state.moves.size}.",
    )
}

/**
 * A snapshot saved right after a game ended (see [planSavedGamePersistence]) carries the
 * [com.worksoc.goaicoach.application.score.FinalScoreJudgement] the result popup needs, but
 * none of the live in-progress fields (engine sync, candidate text) that a resumed game needs.
 * Returns null when the snapshot has no judgement, i.e. it is an in-progress-game snapshot.
 */
fun buildEndedGameRestoreDisplayPlan(
    snapshot: SavedGameSnapshot,
): FinalScoreDisplayPlan? {
    val judgement = snapshot.finalScoreJudgement ?: return null
    return FinalScoreDisplayPlan(
        gameState = snapshot.gameState,
        scoreText = "Score estimate not current.",
        scoreEstimate = null,
        scoreSnapshots = snapshot.scoreSnapshots.takeIf { it.isNotEmpty() }
            ?: listOf(localScoreSnapshot(snapshot.gameState)),
        endgameLog = "Previous game result restored after app restart.",
        engineMessage = "Previous game result restored after app restart.",
        candidateText = "Restored previous game result. Analysis cache will rebuild.",
        judgement = judgement,
    )
}

fun buildSavedGameRestoreRequestPlan(
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
