package com.worksoc.goaicoach.shared.scoring

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.neighbors

/**
 * 빈 점을 이어진 영역으로 나누고 **어느 빈 점이 누구 것인가**를 센다 — 두 계가기가 함께 쓰는 판정.
 *
 * ## 판정 규칙
 * 이어진 빈 영역이 **한 색의 돌에만** 닿으면 그 영역의 빈 점은 전부 그 색의 것이다.
 * 두 색에 다 닿는 영역(공배)과 어떤 돌에도 닿지 않는 영역(빈 판 전체)은 아무의 것도 아니다.
 * 사석은 이미 걷혀 있다고 전제한다 — 사석 판정은 `DeadStoneDetector`/`DeadStoneCleaner`의 일이다.
 *
 * ## 왜 한 곳에 있는가 (refactor backlog #38)
 * 이 플러드필은 [BoardAreaScorer]와 [BoardTerritoryScorer]에 `private`으로 한 벌씩 있었다.
 * 두 벌은 62줄씩이었고 다른 줄 6개는 타입·함수 이름 셋(`AreaOwnership`/`TerritoryOwnership`,
 * `EmptyRegion`/`EmptyTerritoryRegion`, `areaOwnership`/`territoryOwnership`)뿐이었다.
 *
 * 두 룰셋이 다른 것은 **소유한 빈 점에 무엇을 더하느냐**다 — 면적계가는 판 위의 돌과 접바둑
 * 보정을, 집계가는 사석을 더한다. 빈 점의 주인은 룰셋과 무관하게 같아야 하므로 판정을 한 벌만 둔다.
 * 다시 두 벌로 갈라지는 것은 `BoardScoringGoldenTest`의 일치 단언(골든 판과 무작위 종국 판에서
 * 두 계가기가 같은 소유를 낸다)이 막는다.
 */
internal object BoardRegionAnalyzer {
    fun ownedEmptyPoints(state: GameState): OwnedEmptyPoints {
        val visited = mutableSetOf<BoardCoordinate>()
        var black = 0
        var white = 0

        for (row in 0 until state.boardSize.value) {
            for (column in 0 until state.boardSize.value) {
                val start = BoardCoordinate(row, column)
                if (start in visited || state.stoneAt(start) != null) {
                    continue
                }

                val region = collectEmptyRegion(state, start, visited)
                when (region.borderColors.singleOrNull()) {
                    StoneColor.Black -> black += region.points.size
                    StoneColor.White -> white += region.points.size
                    null -> Unit
                }
            }
        }

        return OwnedEmptyPoints(black = black, white = white)
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

/** 한 색에만 닿은 빈 영역의 점 수를 색별로 더한 값. 공배는 어느 쪽에도 들어가지 않는다. */
internal data class OwnedEmptyPoints(
    val black: Int,
    val white: Int,
)

private data class EmptyRegion(
    val points: Set<BoardCoordinate>,
    val borderColors: Set<StoneColor>,
)
