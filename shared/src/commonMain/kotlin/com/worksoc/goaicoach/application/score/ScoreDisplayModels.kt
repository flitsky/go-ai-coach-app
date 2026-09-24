package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.contract.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.shared.policy.EngineOperationResultGuard
import com.worksoc.goaicoach.application.contract.GameSessionEffect
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor

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
    val handicapCount: Int = 0,
)

data class EndgameFailureDisplayPlan(
    val endgameLog: String,
    val engineMessage: String,
    val candidateText: String,
)

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
