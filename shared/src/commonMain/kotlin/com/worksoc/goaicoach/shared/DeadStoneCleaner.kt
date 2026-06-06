package com.worksoc.goaicoach.shared

data class DeadStoneRemoval(
    val coordinate: BoardCoordinate,
    val color: StoneColor,
)

data class DeadStoneCleanupResult(
    val state: GameState,
    val removedStones: List<DeadStoneRemoval>,
) {
    val removedCount: Int
        get() = removedStones.size
}

object DeadStoneCleaner {
    fun apply(
        state: GameState,
        deadStoneCoordinates: Collection<BoardCoordinate>,
    ): DeadStoneCleanupResult {
        val removedStones = deadStoneCoordinates
            .distinct()
            .mapNotNull { coordinate ->
                state.stoneAt(coordinate)?.let { color ->
                    DeadStoneRemoval(coordinate = coordinate, color = color)
                }
            }

        if (removedStones.isEmpty()) {
            return DeadStoneCleanupResult(state = state, removedStones = emptyList())
        }

        val removedCoordinates = removedStones.map { it.coordinate }.toSet()
        val removedBlack = removedStones.count { it.color == StoneColor.Black }
        val removedWhite = removedStones.count { it.color == StoneColor.White }
        val cleanedState = state.copy(
            stones = state.stones - removedCoordinates,
            capturedByBlack = state.capturedByBlack + removedWhite,
            capturedByWhite = state.capturedByWhite + removedBlack,
            koPoint = null,
            koForbiddenFor = null,
        )

        return DeadStoneCleanupResult(
            state = cleanedState,
            removedStones = removedStones,
        )
    }
}
