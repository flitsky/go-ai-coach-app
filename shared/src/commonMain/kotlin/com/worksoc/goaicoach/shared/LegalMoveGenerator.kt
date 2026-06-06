package com.worksoc.goaicoach.shared

object LegalMoveGenerator {
    fun legalPlayCoordinates(
        state: GameState,
        player: StoneColor = state.nextPlayer,
    ): List<BoardCoordinate> {
        require(player == state.nextPlayer) {
            "Legal play generation expects ${state.nextPlayer.label}, got ${player.label}"
        }

        return buildList {
            for (row in 0 until state.boardSize.value) {
                for (column in 0 until state.boardSize.value) {
                    val coordinate = BoardCoordinate(row, column)
                    if (runCatching { state.play(Move.Play(player, coordinate)) }.isSuccess) {
                        add(coordinate)
                    }
                }
            }
        }
    }

    fun legalPlayCount(
        state: GameState,
        player: StoneColor = state.nextPlayer,
    ): Int = legalPlayCoordinates(state, player).size
}
