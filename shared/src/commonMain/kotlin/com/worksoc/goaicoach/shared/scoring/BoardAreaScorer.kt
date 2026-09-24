package com.worksoc.goaicoach.shared.scoring

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.neighbors

object BoardAreaScorer {
    fun score(
        state: GameState,
        komi: Double = state.komi,
    ): FinalScoreResult {
        val ownership = areaOwnership(state)
        val blackArea = state.stones.count { it.value == StoneColor.Black } + ownership.blackTerritory
        val whiteArea = state.stones.count { it.value == StoneColor.White } + ownership.whiteTerritory
        val handicapBonus = whiteHandicapBonus(state.handicapCount)
        val whiteAreaWithKomi = whiteArea + komi + handicapBonus
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
            summary = buildString {
                append("Local Chinese area estimate: Black area $blackArea, White area $whiteArea + komi $komi")
                if (handicapBonus > 0.0) {
                    append(" + handicap bonus ${state.handicapCount} (White gets 1 point per handicap stone, as KataGo chinese whiteHandicapBonus=N)")
                }
                append(". This scorer assumes dead stones have already been removed.")
            },
            whiteHandicapBonus = handicapBonus,
        )
    }

    /**
     * 면적계가 접바둑에서 백이 받는 보정 — **접바둑 돌 하나에 1점**(refactor backlog #89).
     *
     * ## 왜 더하는가
     * 면적계가는 판 위의 돌을 센다. 접바둑 돌 N개는 백이 한 수도 두기 전에 놓인 흑의 영역이라,
     * 보정 없이는 같은 판이 집계가보다 흑에게 정확히 N점 유리해진다(골든 5점 빈 판에서 B+80.5 대
     * B+75.5였다). 중국 규칙이 흑에게 접바둑 돌 수의 절반(子)을 돌려주게 하는 것과 같은 N점이고,
     * 앱이 엔진에 보내는 `kata-set-rules chinese`가 KataGo에서 `whiteHandicapBonus:"N"`이다 —
     * 대국 중 AI·형세 그래프·추천 수의 점수가 전부 이 보정을 품고 있으므로 종국 계가도 따라야 한다.
     *
     * ## 1 이하는 0
     * 접바둑 돌 1개는 접바둑이 아니다(KataGo도 1 이하를 0으로 센다). 앱 설정은 0 또는 2 이상만
     * 고를 수 있지만 규칙은 여기서 직접 막는다.
     *
     * ⚠️ 집계가(`BoardTerritoryScorer`)는 보정하지 않는다 — KataGo `japanese`의
     * `whiteHandicapBonus:"0"`과 같고, 한국식 접바둑 관례(덤 0·0.5, 추가 보정 없음)와도 같다.
     */
    fun whiteHandicapBonus(handicapCount: Int): Double =
        if (handicapCount >= 2) handicapCount.toDouble() else 0.0

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
