package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.policy.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot

data class GameSessionResetPlan(
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

fun buildNewLocalGameSessionPlan(
    message: String,
    ruleset: Ruleset,
    boardSize: BoardSize,
    handicapCount: Int = 0,
    komi: Double = com.worksoc.goaicoach.shared.domain.DefaultKomi,
): GameSessionResetPlan {
    val state = GameState.withHandicap(boardSize, ruleset, handicapCount, komi = komi)
    return GameSessionResetPlan(
        gameState = state,
        candidateText = "No analysis yet.",
        reviewAnalysis = MoveAnalysisSnapshot.empty(state),
        scoreText = "No score estimate yet.",
        // No moves have been played yet, so a flood-fill territory estimate is meaningless here:
        // with only handicap stones on the board, every empty region borders a single color and
        // the whole board gets counted as that color's territory (see B+157.5 misdisplay).
        scoreSnapshots = emptyList(),
        moveReviewText = "No move review yet.",
        lastMoveText = "None",
        endgameLog = "No endgame result recorded.",
        engineMessage = message,
    )
}
