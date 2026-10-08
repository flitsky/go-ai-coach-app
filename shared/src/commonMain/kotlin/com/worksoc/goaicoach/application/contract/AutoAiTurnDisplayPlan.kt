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
    /** 이 수를 둔 AI가 기권을 제안하는가(백로그 #213) — 엔진 쪽 판단(`AutoAiTurnResult.offersResignation`)을 그대로 싣는다. */
    val offersResignation: Boolean = false,
)
