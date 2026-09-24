package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.movereview.MoveReviewResult
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot

data class HumanMoveLocalResult(
    val afterMove: GameState,
    val moveReview: MoveReviewResult,
    val moveReviews: List<MoveReviewMarker>,
    val lastMoveText: String,
    val capturedText: String,
    val localScoreSnapshot: ScoreSnapshot,
    val localFinalScore: FinalScoreResult?,
)

data class HumanEngineSyncFailurePlan(
    val scoreSnapshots: List<ScoreSnapshot>,
    val candidateText: String,
    val engineMessage: String,
)
