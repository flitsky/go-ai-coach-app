package com.worksoc.goaicoach.shared.domain

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

    /**
     * [setup]으로 시작한 판에 [moves]를 차례로 둔다(refactor backlog #22) — 저장분을 되살리는 코덱이 네 값을
     * 따로 넘기다 하나를 빠뜨리면(#1의 덤) 기본 인자가 조용히 채웠다. 이 경로는 빠뜨릴 칸이 없다.
     */
    fun replay(setup: GameSetup, moves: List<Move>): GameState =
        replay(
            boardSize = setup.boardSize,
            ruleset = setup.ruleset,
            moves = moves,
            handicapCount = setup.handicapCount,
            komi = setup.komi,
        )
}

fun GameState.replayWithoutLastMoves(count: Int): GameState {
    require(count >= 0) { "count must be zero or greater" }
    return GameStateReplayer.replay(
        setup = setup,
        moves = moves.dropLast(count.coerceAtMost(moves.size)),
    )
}
