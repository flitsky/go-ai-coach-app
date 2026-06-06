package com.worksoc.goaicoach.shared

object BoardTerritoryScorer {
    fun score(
        state: GameState,
        komi: Double = DefaultKomi,
    ): FinalScoreResult {
        val territory = territoryOwnership(state)
        val blackScore = territory.blackTerritory + state.capturedBy(StoneColor.Black)
        val whiteScore = territory.whiteTerritory + state.capturedBy(StoneColor.White)
        val whiteScoreWithKomi = whiteScore + komi
        val diff = blackScore - whiteScoreWithKomi
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
            status = EngineStatus.ready("Local territory score complete."),
            rawScore = rawScore,
            winner = winner,
            margin = margin,
            blackArea = blackScore.toDouble(),
            whiteAreaWithKomi = whiteScoreWithKomi,
            komi = komi,
            summary = "Local Japanese/Korean territory estimate: Black territory ${territory.blackTerritory} + prisoners ${state.capturedBy(StoneColor.Black)}, White territory ${territory.whiteTerritory} + prisoners ${state.capturedBy(StoneColor.White)} + komi $komi. This scorer assumes dead stones have already been removed.",
        )
    }

    private fun territoryOwnership(state: GameState): TerritoryOwnership {
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

        return TerritoryOwnership(blackTerritory = blackTerritory, whiteTerritory = whiteTerritory)
    }

    private fun collectEmptyRegion(
        state: GameState,
        start: BoardCoordinate,
        visited: MutableSet<BoardCoordinate>,
    ): EmptyTerritoryRegion {
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

        return EmptyTerritoryRegion(points = points, borderColors = borderColors)
    }
}

private data class TerritoryOwnership(
    val blackTerritory: Int,
    val whiteTerritory: Int,
)

private data class EmptyTerritoryRegion(
    val points: Set<BoardCoordinate>,
    val borderColors: Set<StoneColor>,
)
