package com.worksoc.goaicoach.application.contract

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot

data class ScoreEstimateDisplayPlan(
    val scoreText: String,
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
    val engineMessage: String,
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
