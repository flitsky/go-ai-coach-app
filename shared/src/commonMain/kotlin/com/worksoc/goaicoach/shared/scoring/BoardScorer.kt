package com.worksoc.goaicoach.shared

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
