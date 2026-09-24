package com.worksoc.goaicoach.application.contract

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting

data class AutoAiTurnDisplayPlan(
    val playLevel: PlayLevelSetting,
    val profile: EngineProfile,
    val analysisPreset: AnalysisPreset,
    val gameState: GameState,
    val turnEngineMessage: String,
    val candidateText: String,
    val lastMoveText: String,
    val scoreDisplay: ScoreEstimateDisplayPlan,
    val shouldResolveEndgame: Boolean,
    val endgamePrePassCandidates: List<CandidateMove>,
    val nextAnalysisState: GameState?,
)
