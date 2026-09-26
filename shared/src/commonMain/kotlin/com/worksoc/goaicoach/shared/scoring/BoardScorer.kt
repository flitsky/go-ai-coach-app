package com.worksoc.goaicoach.shared.scoring

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.HandicapBonusRule
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult

object BoardScorer {
    fun score(
        state: GameState,
        komi: Double = state.komi,
    ): FinalScoreResult =
        when (state.ruleset) {
            Ruleset.Chinese -> BoardAreaScorer.score(state, komi)
            Ruleset.Japanese -> BoardTerritoryScorer.score(state, komi)
        }
}

/**
 * 이 판에서 백이 받는 접바둑 보정 — `Ruleset.handicapBonusRule` 한 곳이 정한다(refactor backlog #106).
 * 면적계가·집계가가 둘 다 이것을 읽고, 엔진 명령도 같은 값을 읽는다.
 */
internal fun GameState.whiteHandicapBonus(): Double =
    ruleset.handicapBonusRule.whiteHandicapBonus(handicapCount)

/** 계가 요약에 붙는 보정 조각. 보정이 0이면 빈 문자열이라 요약이 보정 없던 때와 같다. */
internal fun GameState.handicapBonusSummary(handicapBonus: Double): String =
    if (handicapBonus > 0.0) {
        " + handicap bonus ${handicapBonus.toInt()} (${ruleset.handicapBonusRule.summaryNote()})"
    } else {
        ""
    }

/** ⚠️ N의 문구는 지금 요약에 그대로 찍히는 글자다 — 바꾸지 마라. */
private fun HandicapBonusRule.summaryNote(): String =
    when (this) {
        HandicapBonusRule.N -> "White gets 1 point per handicap stone, as KataGo chinese whiteHandicapBonus=N"
        HandicapBonusRule.NMinusOne -> "White gets 1 point per handicap stone after the first, as KataGo whiteHandicapBonus=N-1"
        // 닿지 않는다 — Zero면 보정이 늘 0이라 [handicapBonusSummary]가 이 함수를 부르지 않는다. `when`을 닫으려고만 둔다.
        HandicapBonusRule.Zero -> "whiteHandicapBonus=0"
    }
