package com.worksoc.goaicoach.shared.scoring

import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Ruleset
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
