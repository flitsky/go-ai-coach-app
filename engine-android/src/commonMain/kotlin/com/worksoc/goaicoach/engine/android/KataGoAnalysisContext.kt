package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.GameStateReplayer
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor

internal data class KataGoAnalysisContext(
    val boardSize: BoardSize,
    val ruleset: Ruleset,
    /** **지금** 둘 차례 — [playedMoves]를 다 둔 뒤. */
    val nextPlayer: StoneColor,
    val playedMoves: List<Move>,
    val handicapCount: Int,
    val initialStones: Map<BoardCoordinate, StoneColor> = emptyMap(),
    /**
     * 시작판에서 **첫 수를 둘** 차례 — KataGo JSON 쿼리의 `initialPlayer`와 같은 값이다(refactor backlog #91).
     * [playedMoves]의 착수·패스가 홀수 개면 [nextPlayer]와 다르다. [replayState]는 [initialStones]가 있을 때 이 값으로
     * 수순을 쌓기 시작하고, 없을 때는 보지 않는다(그때 시작 차례는 [handicapCount]가 정한다).
     */
    val initialPlayer: StoneColor,
)

internal fun KataGoAnalysisContext.replayState(): GameState =
    if (initialStones.isNotEmpty()) {
        val initial = GameState(
            boardSize = boardSize,
            ruleset = ruleset,
            // ⚠️ [nextPlayer](지금 차례)로 되돌리지 마라 — 홀수 수 뒤에 첫 수의 색과 어긋나
            // `BoardRules`가 "Expected White, got Black"으로 던진다(refactor backlog #91).
            nextPlayer = initialPlayer,
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
