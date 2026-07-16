package com.worksoc.goaicoach.engine.android

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
)

internal fun KataGoAnalysisContext.replayState(): GameState =
    GameStateReplayer.replay(
        boardSize = boardSize,
        ruleset = ruleset,
        moves = playedMoves,
        handicapCount = handicapCount,
    )
