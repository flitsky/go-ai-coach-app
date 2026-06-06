package com.worksoc.goaicoach.shared

object DeadStoneDetector {
    fun capturableDeadStones(state: GameState): List<BoardCoordinate> {
        val visited = mutableSetOf<BoardCoordinate>()
        val deadStones = mutableListOf<BoardCoordinate>()

        for (row in 0 until state.boardSize.value) {
            for (column in 0 until state.boardSize.value) {
                val coordinate = BoardCoordinate(row, column)
                if (coordinate in visited || state.stoneAt(coordinate) == null) {
                    continue
                }

                val group = collectGroup(state, coordinate, visited)
                val capturePoint = group.liberties.singleOrNull() ?: continue
                val capturer = group.color.opponent
                val captureState = runCatching {
                    state.copy(nextPlayer = capturer)
                        .play(Move.Play(capturer, capturePoint))
                }.getOrNull() ?: continue

                if (group.stones.all { captureState.stoneAt(it) == null }) {
                    deadStones += group.stones
                }
            }
        }

        return deadStones.distinct()
    }

    private fun collectGroup(
        state: GameState,
        start: BoardCoordinate,
        visited: MutableSet<BoardCoordinate>,
    ): CapturableGroup {
        val color = requireNotNull(state.stoneAt(start))
        val stones = mutableSetOf<BoardCoordinate>()
        val liberties = mutableSetOf<BoardCoordinate>()
        val pending = mutableListOf(start)
        var index = 0

        while (index < pending.size) {
            val current = pending[index++]
            if (!visited.add(current)) {
                continue
            }
            stones += current

            for (neighbor in current.neighbors(state.boardSize)) {
                when (state.stoneAt(neighbor)) {
                    null -> liberties += neighbor
                    color -> if (neighbor !in visited) pending += neighbor
                    else -> Unit
                }
            }
        }

        return CapturableGroup(
            color = color,
            stones = stones.toList(),
            liberties = liberties,
        )
    }
}

private data class CapturableGroup(
    val color: StoneColor,
    val stones: List<BoardCoordinate>,
    val liberties: Set<BoardCoordinate>,
)
