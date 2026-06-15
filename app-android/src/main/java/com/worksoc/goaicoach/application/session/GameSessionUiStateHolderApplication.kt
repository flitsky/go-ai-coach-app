package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.engine.EngineBenchmarkDisplayPlan
import com.worksoc.goaicoach.application.engine.EngineStartupDisplayPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnFailureDisplayPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnFollowUpPlan
import com.worksoc.goaicoach.application.autoai.buildAutoAiTurnFollowUpPlan
import com.worksoc.goaicoach.application.humanmove.HumanEngineSyncFailurePlan
import com.worksoc.goaicoach.application.humanmove.HumanMoveLocalResult
import com.worksoc.goaicoach.application.score.EndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.score.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.score.ScoreEstimateFailureDisplayPlan
import com.worksoc.goaicoach.application.topmoves.TopMoveAnalysisFailureDisplayPlan
import com.worksoc.goaicoach.application.undo.UndoLocalStatePlan

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

    fun applyEngineStartupDisplayPlan(startup: EngineStartupDisplayPlan) {
        applyCoreState(currentCoreState().applyEngineStartupDisplayPlan(startup))
    }

    fun applyEngineBenchmarkDisplayPlan(benchmark: EngineBenchmarkDisplayPlan) {
        applyCoreState(currentCoreState().applyEngineBenchmarkDisplayPlan(benchmark))
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
