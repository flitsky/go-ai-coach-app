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

internal data class ScoreEstimateDisplayPlan(
    val scoreText: String,
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
    val engineMessage: String,
)

internal data class ScoreEstimateStateResult(
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
)

internal data class ScoreEstimateFailureDisplayPlan(
    val engineMessage: String,
)

internal data class FinalScoreDisplayPlan(
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

internal data class FinalScoreStateResult(
    val gameState: GameState,
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
    val endgameLog: String,
    val endgameTimingSummary: String? = null,
    val judgement: FinalScoreJudgement? = null,
)

internal data class FinalScoreJudgement(
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

internal data class EndgameFailureDisplayPlan(
    val endgameLog: String,
    val engineMessage: String,
    val candidateText: String,
)

internal sealed class ScoreEstimateRequestPlan {
    data class ShowMessage(val message: String) : ScoreEstimateRequestPlan()
    data class ShowLocalEstimate(val display: ScoreEstimateDisplayPlan) : ScoreEstimateRequestPlan()
    data class RequestEngineEstimate(
        val state: GameState,
        val profile: EngineProfile,
        val syncFirst: Boolean,
    ) : ScoreEstimateRequestPlan()
}

internal data class ScoreEstimateLaunchStateUpdate(
    val engineMessage: String? = null,
    val display: ScoreEstimateDisplayPlan? = null,
    val effect: GameSessionEffect.RunScoreEstimate? = null,
)

internal data class ScoreEstimateOperationToken(
    val operation: EngineOperationRequest,
)

internal sealed class ScoreEstimateWorkflowResult {
    data class Success(val display: ScoreEstimateDisplayPlan) : ScoreEstimateWorkflowResult()
    data class Failure(val error: Throwable) : ScoreEstimateWorkflowResult()
}

internal sealed class ScoreEstimateCompletionPlan {
    data class ApplySuccess(val display: ScoreEstimateDisplayPlan) : ScoreEstimateCompletionPlan()
    data class ApplyFailure(val failure: ScoreEstimateFailureDisplayPlan) : ScoreEstimateCompletionPlan()
    data class Discard(val discard: EngineOperationResultGuard.Discard) : ScoreEstimateCompletionPlan()
}

internal sealed class ScoreEstimateCompletionApplyPlan {
    data class ApplySuccess(val display: ScoreEstimateDisplayPlan) : ScoreEstimateCompletionApplyPlan()
    data class ApplyFailure(val failure: ScoreEstimateFailureDisplayPlan) : ScoreEstimateCompletionApplyPlan()
    data class Discard(val discard: EngineOperationResultGuard.Discard) : ScoreEstimateCompletionApplyPlan()
}
