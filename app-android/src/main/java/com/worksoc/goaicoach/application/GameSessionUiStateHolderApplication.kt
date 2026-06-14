package com.worksoc.goaicoach.application

internal class GameSessionUiStateHolder(
    private val currentCoreState: () -> GameSessionCoreState,
    private val applyCoreState: (GameSessionCoreState) -> Unit,
) {
    fun applyScoreEstimateDisplayPlan(score: ScoreEstimateDisplayPlan) {
        applyCoreState(currentCoreState().applyScoreEstimateDisplayPlan(score))
    }

    fun applyScoreEstimateFailureDisplayPlan(failure: ScoreEstimateFailureDisplayPlan) {
        applyCoreState(currentCoreState().applyScoreEstimateFailureDisplayPlan(failure))
    }

    fun applyFinalScoreDisplayPlan(final: FinalScoreDisplayPlan) {
        applyCoreState(currentCoreState().applyFinalScoreDisplayPlan(final))
    }

    fun applyEndgameFailureDisplayPlan(failure: EndgameFailureDisplayPlan) {
        applyCoreState(currentCoreState().applyEndgameFailureDisplayPlan(failure))
    }

    fun applyUndoLocalStatePlan(undo: UndoLocalStatePlan) {
        applyCoreState(currentCoreState().applyUndoLocalStatePlan(undo))
    }
}
