package com.worksoc.goaicoach.shared

object BoardAreaScorer {
    fun score(
        state: GameState,
        komi: Double = DefaultKomi,
    ): FinalScoreResult {
        val ownership = areaOwnership(state)
        val blackArea = state.stones.count { it.value == StoneColor.Black } + ownership.blackTerritory
        val whiteArea = state.stones.count { it.value == StoneColor.White } + ownership.whiteTerritory
        val whiteAreaWithKomi = whiteArea + komi
        val diff = blackArea - whiteAreaWithKomi
        val winner = when {
            diff > 0.0 -> StoneColor.Black
            diff < 0.0 -> StoneColor.White
            else -> null
        }
        val margin = kotlin.math.abs(diff)
        val rawScore = when (winner) {
            StoneColor.Black -> "B+$margin"
            StoneColor.White -> "W+$margin"
            null -> "Draw"
        }

        return FinalScoreResult(
            status = EngineStatus.ready("Local area score complete."),
            rawScore = rawScore,
            winner = winner,
            margin = margin,
            blackArea = blackArea.toDouble(),
            whiteAreaWithKomi = whiteAreaWithKomi,
            komi = komi,
            summary = "Local Chinese area estimate: Black area $blackArea, White area $whiteArea + komi $komi. This scorer assumes dead stones have already been removed.",
        )
    }

    private fun areaOwnership(state: GameState): AreaOwnership {
        val visited = mutableSetOf<BoardCoordinate>()
        var blackTerritory = 0
        var whiteTerritory = 0

        for (row in 0 until state.boardSize.value) {
            for (column in 0 until state.boardSize.value) {
                val start = BoardCoordinate(row, column)
                if (start in visited || state.stoneAt(start) != null) {
                    continue
                }

                val region = collectEmptyRegion(state, start, visited)
                when (region.borderColors.singleOrNull()) {
                    StoneColor.Black -> blackTerritory += region.points.size
                    StoneColor.White -> whiteTerritory += region.points.size
                    null -> Unit
                }
            }
        }

        return AreaOwnership(blackTerritory = blackTerritory, whiteTerritory = whiteTerritory)
    }

    private fun collectEmptyRegion(
        state: GameState,
        start: BoardCoordinate,
        visited: MutableSet<BoardCoordinate>,
    ): EmptyRegion {
        val points = mutableSetOf<BoardCoordinate>()
        val borderColors = mutableSetOf<StoneColor>()
        val pending = mutableListOf(start)
        var index = 0

        while (index < pending.size) {
            val current = pending[index++]
            if (!visited.add(current)) {
                continue
            }
            points += current

            for (neighbor in current.neighbors(state.boardSize)) {
                when (val color = state.stoneAt(neighbor)) {
                    null -> if (neighbor !in visited) pending += neighbor
                    else -> borderColors += color
                }
            }
        }

        return EmptyRegion(points = points, borderColors = borderColors)
    }
}

private data class AreaOwnership(
    val blackTerritory: Int,
    val whiteTerritory: Int,
)

private data class EmptyRegion(
    val points: Set<BoardCoordinate>,
    val borderColors: Set<StoneColor>,
)
