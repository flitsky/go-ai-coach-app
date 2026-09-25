package com.worksoc.goaicoach.application.contract

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile

internal data class HumanEngineSyncRunPlan(
    val afterMove: GameState,
    val profile: EngineProfile,
    val move: Move,
    val previousReviewCandidates: List<CandidateMove>,
)
