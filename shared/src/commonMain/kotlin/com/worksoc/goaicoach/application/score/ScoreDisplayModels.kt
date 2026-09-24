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
    /**
     * [whiteAreaWithKomi]에 들어 있는 면적계가 접바둑 보정(refactor backlog #89) — 결과 대화상자가
     * 백 줄에 "+ 접바둑 보정 N"으로 밝힌다.
     *
     * ⚠️ **기본값 0은 옛 저장본을 위한 것이다.** 이 필드가 생기기 전에 저장된 판정은 보정 없이
     * 계가됐으므로 0으로 읽혀야 그 판정의 합계와 맞는다(`GameSessionStore`가 `optDouble(…, 0.0)`로
     * 흡수한다 — 스키마 번호는 그대로다, 함정 69).
     */
    val whiteHandicapBonus: Double = 0.0,
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
