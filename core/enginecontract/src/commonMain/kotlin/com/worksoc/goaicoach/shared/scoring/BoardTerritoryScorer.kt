package com.worksoc.goaicoach.shared.scoring

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult

/**
 * 집계가 — 둘러싼 빈 점 + 따낸 돌, 백은 덤을 더한다.
 *
 * 접바둑 보정도 면적계가와 같은 자리(`Ruleset.handicapBonusRule`)에서 읽는다(refactor backlog #106) — 계가기
 * 종류가 아니라 룰셋의 값이 정한다. 집계가 `Japanese`는 0이라(돌을 세지 않으니 보정할 것이 없다 — KataGo
 * `japanese`의 `whiteHandicapBonus:"0"`, 한국식 접바둑 관례와 같다) 결과는 보정을 읽기 전과 같다.
 */
object BoardTerritoryScorer {
    fun score(
        state: GameState,
        komi: Double = state.komi,
    ): FinalScoreResult {
        val territory = BoardRegionAnalyzer.ownedEmptyPoints(state)
        val blackScore = territory.black + state.capturedBy(StoneColor.Black)
        val whiteScore = territory.white + state.capturedBy(StoneColor.White)
        val handicapBonus = state.whiteHandicapBonus()
        val whiteScoreWithKomi = whiteScore + komi + handicapBonus
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
            summary = "Local Japanese/Korean territory estimate: Black territory ${territory.black} + prisoners ${state.capturedBy(StoneColor.Black)}, White territory ${territory.white} + prisoners ${state.capturedBy(StoneColor.White)} + komi $komi" +
                state.handicapBonusSummary(handicapBonus) +
                ". This scorer assumes dead stones have already been removed.",
            whiteHandicapBonus = handicapBonus,
        )
    }
}
