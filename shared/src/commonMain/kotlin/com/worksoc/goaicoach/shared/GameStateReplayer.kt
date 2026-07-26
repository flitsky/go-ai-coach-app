package com.worksoc.goaicoach.shared

object GameStateReplayer {
    fun replay(
        boardSize: BoardSize,
        ruleset: Ruleset,
        moves: List<Move>,
        handicapCount: Int = 0,
        firstPlayer: StoneColor = StoneColor.Black,
        komi: Double = DefaultKomi,
    ): GameState {
        var state = if (handicapCount > 0) {
            GameState.withHandicap(boardSize, ruleset, handicapCount, komi = komi)
        } else {
            GameState.empty(
                boardSize = boardSize,
                ruleset = ruleset,
                nextPlayer = firstPlayer,
                komi = komi,
            )
        }
        for (move in moves) {
            state = state.play(move)
        }
        return state
    }
}

fun GameState.replayWithoutLastMoves(count: Int): GameState {
    require(count >= 0) { "count must be zero or greater" }
    return GameStateReplayer.replay(
        boardSize = boardSize,
        ruleset = ruleset,
        moves = moves.dropLast(count.coerceAtMost(moves.size)),
        handicapCount = handicapCount,
        komi = komi,
    )
}
