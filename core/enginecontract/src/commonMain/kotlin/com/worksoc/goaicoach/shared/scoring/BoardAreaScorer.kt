package com.worksoc.goaicoach.shared.scoring

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult

/**
 * 면적계가 — 판 위의 돌 + 둘러싼 빈 점, 백은 덤과 접바둑 보정을 더한다.
 *
 * ## 접바둑 보정(refactor backlog #89)
 * 접바둑 돌 N개는 백이 한 수도 두기 전에 놓인 흑의 영역이라, 보정 없이는 같은 판이 집계가보다 흑에게
 * 정확히 N점 유리해진다(골든 5점 빈 판에서 B+80.5 대 B+75.5였다). 중국 규칙이 흑에게 접바둑 돌 수의
 * 절반(子)을 돌려주게 하는 것과 같은 N점이다.
 *
 * **얼마를 더할지는 여기서 정하지 않는다**(refactor backlog #106) — `Ruleset.handicapBonusRule`(면적계가
 * `Chinese`는 N)이 정하고, 엔진 명령(`kata-set-rules`)도 같은 값을 읽는다. 대국 중 AI·형세 그래프·추천 수의
 * 점수가 그 보정을 품으므로 종국 계가도 같은 값을 따라야 한다.
 */
object BoardAreaScorer {
    fun score(
        state: GameState,
        komi: Double = state.komi,
    ): FinalScoreResult {
        val ownership = BoardRegionAnalyzer.ownedEmptyPoints(state)
        val blackArea = state.stones.count { it.value == StoneColor.Black } + ownership.black
        val whiteArea = state.stones.count { it.value == StoneColor.White } + ownership.white
        val handicapBonus = state.whiteHandicapBonus()
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
                append(state.handicapBonusSummary(handicapBonus))
                append(". This scorer assumes dead stones have already been removed.")
            },
            whiteHandicapBonus = handicapBonus,
        )
    }
}
