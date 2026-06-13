package com.worksoc.goaicoach.shared

object LegalMoveGenerator {
    fun legalPlayCoordinates(
        state: GameState,
        player: StoneColor = state.nextPlayer,
    ): List<BoardCoordinate> {
        require(player == state.nextPlayer) {
            "Legal play generation expects ${state.nextPlayer.label}, got ${player.label}"
        }

        return state.boardSize
            .allCoordinates()
            .filter { coordinate ->
                runCatching { state.play(Move.Play(player, coordinate)) }.isSuccess
            }
            .toList()
    }

    fun legalPlayCount(
        state: GameState,
        player: StoneColor = state.nextPlayer,
    ): Int = legalPlayCoordinates(state, player).size
}
