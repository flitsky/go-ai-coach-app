package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor

data class ScoreEstimateDisplayPlan(
    val scoreText: String,
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
    val engineMessage: String,
)

data class ScoreEstimateStateResult(
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
)

data class ScoreEstimateFailureDisplayPlan(
    val engineMessage: String,
)

data class FinalScoreDisplayPlan(
    val gameState: GameState,
    val scoreText: String,
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
    val endgameLog: String,
    val engineMessage: String,
    val candidateText: String,
    val endgameTimingSummary: String? = null,
    val judgement: FinalScoreJudgement? = null,
)

data class FinalScoreStateResult(
    val gameState: GameState,
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
    val endgameLog: String,
    val endgameTimingSummary: String? = null,
    val judgement: FinalScoreJudgement? = null,
)

data class FinalScoreJudgement(
    val winner: StoneColor?,
    val margin: Double?,
    val ruleset: Ruleset,
    val isEstimatedDisplay: Boolean,
    val removedBlack: Int,
    val removedWhite: Int,
    val blackArea: Double?,
    val whiteAreaWithKomi: Double?,
    val capturedByBlack: Int,
    val capturedByWhite: Int,
    val komi: Double?,
)

data class EndgameFailureDisplayPlan(
    val endgameLog: String,
    val engineMessage: String,
    val candidateText: String,
)

sealed class ScoreEstimateRequestPlan {
    data class ShowMessage(val message: String) : ScoreEstimateRequestPlan()
    data class ShowLocalEstimate(val display: ScoreEstimateDisplayPlan) : ScoreEstimateRequestPlan()
    data class RequestEngineEstimate(
        val state: GameState,
        val profile: EngineProfile,
        val syncFirst: Boolean,
    ) : ScoreEstimateRequestPlan()
}

data class ScoreEstimateLaunchStateUpdate(
    val engineMessage: String? = null,
    val display: ScoreEstimateDisplayPlan? = null,
    val effect: GameSessionEffect.RunScoreEstimate? = null,
)

data class ScoreEstimateOperationToken(
    val operation: EngineOperationRequest,
)

sealed class ScoreEstimateWorkflowResult {
    data class Success(val display: ScoreEstimateDisplayPlan) : ScoreEstimateWorkflowResult()
    data class Failure(val error: Throwable) : ScoreEstimateWorkflowResult()
}

sealed class ScoreEstimateCompletionPlan {
    data class ApplySuccess(val display: ScoreEstimateDisplayPlan) : ScoreEstimateCompletionPlan()
    data class ApplyFailure(val failure: ScoreEstimateFailureDisplayPlan) : ScoreEstimateCompletionPlan()
    data class Discard(val discard: EngineOperationResultGuard.Discard) : ScoreEstimateCompletionPlan()
}

sealed class ScoreEstimateCompletionApplyPlan {
    data class ApplySuccess(val display: ScoreEstimateDisplayPlan) : ScoreEstimateCompletionApplyPlan()
    data class ApplyFailure(val failure: ScoreEstimateFailureDisplayPlan) : ScoreEstimateCompletionApplyPlan()
    data class Discard(val discard: EngineOperationResultGuard.Discard) : ScoreEstimateCompletionApplyPlan()
}
