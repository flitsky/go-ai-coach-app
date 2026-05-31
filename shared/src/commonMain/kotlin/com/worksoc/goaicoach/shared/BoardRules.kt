package com.worksoc.goaicoach.shared

object BoardRules {
    fun play(
        state: GameState,
        move: Move,
    ): GameState =
        when (move) {
            is Move.Play -> playStone(state, move)
            is Move.Pass -> pass(state, move)
            is Move.Resign -> resign(state, move)
        }

    private fun playStone(
        state: GameState,
        move: Move.Play,
    ): GameState {
        require(move.player == state.nextPlayer) {
            "Expected ${state.nextPlayer.label}, got ${move.player.label}"
        }
        require(move.coordinate.isInside(state.boardSize)) {
            "${move.coordinate} is outside ${state.boardSize.value}x${state.boardSize.value}"
        }
        require(state.stoneAt(move.coordinate) == null) {
            "${move.coordinate.label(state.boardSize)} is already occupied"
        }
        require(move.coordinate != state.koPoint || move.player != state.koForbiddenFor) {
            "Illegal ko recapture at ${move.coordinate.label(state.boardSize)}"
        }

        var nextStones = state.stones + (move.coordinate to move.player)
        val capturedStones = mutableSetOf<BoardCoordinate>()
        for (neighbor in move.coordinate.neighbors(state.boardSize)) {
            if (nextStones[neighbor] == move.player.opponent) {
                val group = nextStones.groupAt(neighbor, state.boardSize)
                if (group.liberties.isEmpty()) {
                    capturedStones += group.stones
                }
            }
        }
        nextStones = nextStones - capturedStones

        val ownGroup = nextStones.groupAt(move.coordinate, state.boardSize)
        require(ownGroup.liberties.isNotEmpty()) {
            "Suicide move is not allowed at ${move.coordinate.label(state.boardSize)}"
        }

        val nextKoPoint = nextKoPoint(capturedStones, ownGroup)

        return state.copy(
            nextPlayer = state.nextPlayer.opponent,
            stones = nextStones,
            moves = state.moves + move,
            capturedByBlack = state.capturedByBlack + if (move.player == StoneColor.Black) capturedStones.size else 0,
            capturedByWhite = state.capturedByWhite + if (move.player == StoneColor.White) capturedStones.size else 0,
            koPoint = nextKoPoint,
            koForbiddenFor = nextKoPoint?.let { move.player.opponent },
        )
    }

    private fun pass(
        state: GameState,
        move: Move.Pass,
    ): GameState {
        require(move.player == state.nextPlayer) {
            "Expected ${state.nextPlayer.label}, got ${move.player.label}"
        }
        return state.copy(
            nextPlayer = state.nextPlayer.opponent,
            moves = state.moves + move,
            koPoint = null,
            koForbiddenFor = null,
        )
    }

    private fun resign(
        state: GameState,
        move: Move.Resign,
    ): GameState {
        require(move.player == state.nextPlayer) {
            "Expected ${state.nextPlayer.label}, got ${move.player.label}"
        }
        return state.copy(moves = state.moves + move, koPoint = null, koForbiddenFor = null)
    }

    private fun nextKoPoint(
        capturedStones: Set<BoardCoordinate>,
        ownGroup: BoardGroup,
    ): BoardCoordinate? =
        capturedStones.singleOrNull()
            ?.takeIf { ownGroup.stones.size == 1 && ownGroup.liberties.size == 1 }
}

private data class BoardGroup(
    val stones: Set<BoardCoordinate>,
    val liberties: Set<BoardCoordinate>,
)

private fun Map<BoardCoordinate, StoneColor>.groupAt(
    start: BoardCoordinate,
    boardSize: BoardSize,
): BoardGroup {
    val color = requireNotNull(this[start]) {
        "Cannot collect a group from an empty point: ${start.label(boardSize)}"
    }
    val groupStones = mutableSetOf<BoardCoordinate>()
    val liberties = mutableSetOf<BoardCoordinate>()
    val pending = mutableListOf(start)
    var index = 0

    while (index < pending.size) {
        val current = pending[index++]
        if (!groupStones.add(current)) {
            continue
        }

        for (neighbor in current.neighbors(boardSize)) {
            when (this[neighbor]) {
                null -> liberties += neighbor
                color -> pending += neighbor
                else -> Unit
            }
        }
    }

    return BoardGroup(stones = groupStones, liberties = liberties)
}

internal fun BoardCoordinate.neighbors(boardSize: BoardSize): List<BoardCoordinate> =
    buildList {
        if (row > 0) {
            add(copy(row = row - 1))
        }
        if (row < boardSize.value - 1) {
            add(copy(row = row + 1))
        }
        if (column > 0) {
            add(copy(column = column - 1))
        }
        if (column < boardSize.value - 1) {
            add(copy(column = column + 1))
        }
    }
