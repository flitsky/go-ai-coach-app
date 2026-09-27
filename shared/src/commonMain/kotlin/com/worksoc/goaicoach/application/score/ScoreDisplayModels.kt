package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.contract.GameSessionEffect
import com.worksoc.goaicoach.application.contract.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.engine.EngineOperationBusy
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineOperationResultGuard
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot

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

internal data class ScoreEstimateLaunchStateUpdate(
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

    /** 엔진이 다른 오퍼레이션을 하고 있어 기다리지 않고 포기했다(refactor backlog #15) — 실패가 아니다. */
    data class Busy(val busy: EngineOperationBusy) : ScoreEstimateWorkflowResult()
}

sealed class ScoreEstimateCompletionPlan {
    data class ApplySuccess(val display: ScoreEstimateDisplayPlan) : ScoreEstimateCompletionPlan()
    data class ApplyFailure(val failure: ScoreEstimateFailureDisplayPlan) : ScoreEstimateCompletionPlan()
    data class Discard(val discard: EngineOperationResultGuard.Discard) : ScoreEstimateCompletionPlan()

    /**
     * 엔진이 바빠 형세 추정이 포기했다(refactor backlog #15) — 요청 때 엔진이 바빴던 것과 **같은 문구만** 보인다
     * ([ScoreEstimateBusyMessage]). 실패([ApplyFailure])처럼 지난 형세를 지우지 않는다. 다시 누르면 된다 — 1회권은
     * 그 수 안에서 이미 치렀으므로 다시 누르는 것은 무료다(`GamePlaySection.featureGated`의 `isPaidForMove`).
     */
    data class ShowBusyMessage(val message: String) : ScoreEstimateCompletionPlan()
}

sealed class ScoreEstimateCompletionApplyPlan {
    data class ApplySuccess(val display: ScoreEstimateDisplayPlan) : ScoreEstimateCompletionApplyPlan()
    data class ApplyFailure(val failure: ScoreEstimateFailureDisplayPlan) : ScoreEstimateCompletionApplyPlan()
    data class Discard(val discard: EngineOperationResultGuard.Discard) : ScoreEstimateCompletionApplyPlan()

    /** [ScoreEstimateCompletionPlan.ShowBusyMessage]와 같다 — 문구만 보인다. */
    data class ShowBusyMessage(val message: String) : ScoreEstimateCompletionApplyPlan()
}
