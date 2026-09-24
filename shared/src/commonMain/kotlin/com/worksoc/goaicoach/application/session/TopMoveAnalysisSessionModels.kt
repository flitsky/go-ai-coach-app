package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.analysis.CachedAnalysisResult
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.policy.MoveAnalysisSnapshot

data class TopMoveAnalysisUpdate(
    val snapshot: MoveAnalysisSnapshot,
    val reviewCandidateMoves: List<CandidateMove>,
    val candidateMoves: List<CandidateMove>,
    val candidateText: String,
    val engineMessage: String,
    val cachedResult: CachedAnalysisResult?,
    val undoRestoreResult: CachedAnalysisResult? = null,
)

data class TopMoveAnalysisFailureDisplayPlan(
    val targetState: GameState,
    val engineMessage: String,
    val clearDisplayedTopMoves: Boolean,
    val candidateText: String? = null,
)
