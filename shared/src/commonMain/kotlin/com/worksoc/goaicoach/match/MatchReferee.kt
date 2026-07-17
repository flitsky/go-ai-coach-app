package com.worksoc.goaicoach.match

import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move

/**
 * Match progression and referee boundary.
 *
 * This intentionally delegates rule execution to [GameState] today. Keeping the
 * entry point here lets human, AI, and future remote-user moves share the same
 * legality/endgame path without making UX code know where those checks live.
 */
object MatchReferee {
    fun play(
        state: GameState,
        move: Move,
    ): Result<GameState> =
        runCatching { state.play(move) }

    fun playOrThrow(
        state: GameState,
        move: Move,
    ): GameState =
        play(state, move).getOrThrow()

    fun shouldResolveEndgame(state: GameState): Boolean =
        state.hasConsecutivePasses() || state.isBoardFull()

    fun endgameReasonText(state: GameState): String? =
        when {
            state.hasConsecutivePasses() -> "Game ended after consecutive passes."
            state.isBoardFull() -> "Board is full."
            else -> null
        }

    fun localFinalScoreIfGameEndedByPasses(state: GameState): FinalScoreResult? =
        if (state.hasConsecutivePasses()) {
            BoardScorer.score(state)
        } else {
            null
        }
}
