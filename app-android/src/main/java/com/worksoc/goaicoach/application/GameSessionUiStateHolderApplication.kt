package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.topmoves.TopMoveAnalysisFailureDisplayPlan

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

    fun applyTopMoveAnalysisFailureDisplayPlan(failure: TopMoveAnalysisFailureDisplayPlan) {
        applyCoreState(currentCoreState().applyTopMoveAnalysisFailureDisplayPlan(failure))
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

    fun applyHumanEngineSyncFailurePlan(failure: HumanEngineSyncFailurePlan) {
        applyCoreState(currentCoreState().applyHumanEngineSyncFailurePlan(failure))
    }

    fun applyHumanMoveLocalResult(result: HumanMoveLocalResult) {
        applyCoreState(currentCoreState().applyHumanMoveLocalResult(result))
    }

    fun applyAutoAiTurnDisplayPlan(display: AutoAiTurnDisplayPlan): AutoAiTurnFollowUpPlan {
        applyCoreState(currentCoreState().applyAutoAiTurnDisplayPlan(display))
        return buildAutoAiTurnFollowUpPlan(display)
    }

    fun applyAutoAiTurnFailureDisplayPlan(failure: AutoAiTurnFailureDisplayPlan) {
        applyCoreState(currentCoreState().applyAutoAiTurnFailureDisplayPlan(failure))
    }
}
