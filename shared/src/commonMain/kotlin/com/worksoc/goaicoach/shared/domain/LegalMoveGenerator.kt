package com.worksoc.goaicoach.shared.domain

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
                BoardRules.validate(state, Move.Play(player, coordinate)) == null
            }
            .toList()
    }

    fun legalPlayCount(
        state: GameState,
        player: StoneColor = state.nextPlayer,
    ): Int = legalPlayCoordinates(state, player).size

    /**
     * 좌표 하나만의 합법 여부. 착수 중(끌기 포함) 매 프레임 물어볼 수 있어야 해서
     * [legalPlayCoordinates]처럼 판 전체를 훑지 않는다 — [BoardRules.validate] 판정
     * 한 번뿐이다(리팩토링 백로그 #37, 상태 복사·예외 없이 값으로 판정한다).
     */
    fun isLegalPlay(
        state: GameState,
        coordinate: BoardCoordinate,
        player: StoneColor = state.nextPlayer,
    ): Boolean {
        require(player == state.nextPlayer) {
            "Legal play check expects ${state.nextPlayer.label}, got ${player.label}"
        }
        return BoardRules.validate(state, Move.Play(player, coordinate)) == null
    }
}
