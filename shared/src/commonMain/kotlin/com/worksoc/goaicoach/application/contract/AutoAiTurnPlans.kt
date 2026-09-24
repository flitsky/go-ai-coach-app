package com.worksoc.goaicoach.application.contract

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting

data class AutoAiTurnRunPlan(
    val delayMillis: Long,
    val context: AutoAiTurnExecutionContext,
)

data class AutoAiTurnExecutionContext(
    val turnState: GameState,
    val aiPlayer: StoneColor,
    val playLevel: PlayLevelSetting,
    val analysisLimit: AnalysisLimit,
    val searchMode: EngineSearchMode,
    val isolateSearchCache: Boolean,
    val previousReviewCandidates: List<CandidateMove>,
)

sealed class AutoAiTurnEndgamePlan {
    data object None : AutoAiTurnEndgamePlan()
    data class Resolve(
        val state: GameState,
        val profile: EngineProfile,
        val prePassCandidates: List<CandidateMove>,
        val engineMessagePrefix: String,
        val successSource: String = "auto-ai-engine-dead-stone-cleanup",
        val failureSource: String = "auto-ai-engine-final-score-failed",
    ) : AutoAiTurnEndgamePlan()
}
