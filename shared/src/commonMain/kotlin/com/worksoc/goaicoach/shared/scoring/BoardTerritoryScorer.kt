package com.worksoc.goaicoach.shared.scoring

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult

object BoardTerritoryScorer {
    fun score(
        state: GameState,
        komi: Double = state.komi,
    ): FinalScoreResult {
        val territory = BoardRegionAnalyzer.ownedEmptyPoints(state)
        val blackScore = territory.black + state.capturedBy(StoneColor.Black)
        val whiteScore = territory.white + state.capturedBy(StoneColor.White)
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
            summary = "Local Japanese/Korean territory estimate: Black territory ${territory.black} + prisoners ${state.capturedBy(StoneColor.Black)}, White territory ${territory.white} + prisoners ${state.capturedBy(StoneColor.White)} + komi $komi. This scorer assumes dead stones have already been removed.",
        )
    }
}
