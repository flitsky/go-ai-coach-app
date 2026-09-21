package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.GameStateReplayer
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor

internal data class KataGoAnalysisContext(
    val boardSize: BoardSize,
    val ruleset: Ruleset,
    val nextPlayer: StoneColor,
    val playedMoves: List<Move>,
    val handicapCount: Int,
    val initialStones: Map<BoardCoordinate, StoneColor> = emptyMap(),
)

internal fun KataGoAnalysisContext.replayState(): GameState =
    if (initialStones.isNotEmpty()) {
        val initial = GameState(
            boardSize = boardSize,
            ruleset = ruleset,
            nextPlayer = nextPlayer,
            stones = initialStones,
            moves = emptyList(),
            handicapCount = handicapCount,
        )
        playedMoves.fold(initial) { state, move -> state.play(move) }
    } else {
        GameStateReplayer.replay(
            boardSize = boardSize,
            ruleset = ruleset,
            moves = playedMoves,
            handicapCount = handicapCount,
        )
    }
