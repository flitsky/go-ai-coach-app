package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot

internal data class GameSessionAnalysisState(
    val candidateMoves: List<CandidateMove>,
    val candidateText: String,
    val reviewAnalysis: MoveAnalysisSnapshot,
    val reviewCandidateMoves: List<CandidateMove>,
    val lastAnalysisKey: AnalysisCacheKey?,
) {
    fun clearTopMoveSpots(message: String? = null): GameSessionAnalysisState =
        copy(
            candidateMoves = emptyList(),
            candidateText = message ?: candidateText,
        )

    fun clearReviewAnalysis(state: GameState): GameSessionAnalysisState =
        copy(
            reviewAnalysis = MoveAnalysisSnapshot.empty(state),
            reviewCandidateMoves = emptyList(),
        )

    fun applyTopMoveAnalysisUpdate(
        update: TopMoveAnalysisUpdate,
        analysisKey: AnalysisCacheKey,
    ): GameSessionAnalysisState =
        copy(
            reviewAnalysis = update.snapshot,
            reviewCandidateMoves = update.reviewCandidateMoves,
            candidateText = update.candidateText,
            candidateMoves = update.candidateMoves,
            lastAnalysisKey = analysisKey,
        )

    companion object {
        fun empty(
            state: GameState,
            candidateText: String = "No analysis yet.",
        ): GameSessionAnalysisState =
            reset(
                candidateText = candidateText,
                reviewAnalysis = MoveAnalysisSnapshot.empty(state),
            )

        fun reset(
            candidateText: String,
            reviewAnalysis: MoveAnalysisSnapshot,
        ): GameSessionAnalysisState =
            GameSessionAnalysisState(
                candidateMoves = emptyList(),
                candidateText = candidateText,
                reviewAnalysis = reviewAnalysis,
                reviewCandidateMoves = emptyList(),
                lastAnalysisKey = null,
            )
    }
}
