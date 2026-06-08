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
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe

internal data class RuntimePlayLevelSelection(
    val playLevel: PlayLevelSetting,
    val engineProfile: EngineProfile,
    val analysisPreset: AnalysisPreset,
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
): RuntimePlayLevelSelection {
    val playLevel = selectPrimaryPlayLevel(
        setup = setup,
        nextPlayer = nextPlayer,
        defaultPlayLevel = defaultPlayLevel,
    )
    return RuntimePlayLevelSelection(
        playLevel = playLevel,
        engineProfile = playLevel.toEngineProfile(currentProfile),
        analysisPreset = playLevel.analysisPreset,
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
): SavedGameRestorePlan {
    val state = snapshot.gameState
    val runtime = selectRuntimePlayLevel(
        setup = snapshot.playerSetup,
        nextPlayer = state.nextPlayer,
        currentProfile = currentProfile,
        defaultPlayLevel = defaultPlayLevel,
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
