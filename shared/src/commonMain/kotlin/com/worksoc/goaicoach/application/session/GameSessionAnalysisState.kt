package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.contract.AnalysisCacheKey
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.policy.MoveAnalysisSnapshot

data class GameSessionAnalysisState(
    val candidateMoves: List<CandidateMove>,
    val candidateText: String,
    val reviewAnalysis: MoveAnalysisSnapshot,
    val reviewCandidateMoves: List<CandidateMove>,
    val lastAnalysisKey: AnalysisCacheKey?,
    val sideAnalysisTexts: Map<StoneColor, String> = emptyMap(),
) {
    internal fun clearTopMoveSpots(message: String? = null): GameSessionAnalysisState =
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
            sideAnalysisTexts = sideAnalysisTexts.withAnalysisText(
                player = update.snapshot.player,
                text = update.candidateText,
            ),
        )

    fun applyTopMoveAnalysisFailureDisplayPlan(
        failure: TopMoveAnalysisFailureDisplayPlan,
    ): GameSessionAnalysisState =
        copy(
            candidateMoves = if (failure.clearDisplayedTopMoves) emptyList() else candidateMoves,
            candidateText = failure.candidateText ?: candidateText,
            reviewAnalysis = MoveAnalysisSnapshot.empty(failure.targetState),
            reviewCandidateMoves = emptyList(),
            lastAnalysisKey = null,
        )

    fun recordSideAnalysisText(
        player: StoneColor,
        text: String,
    ): GameSessionAnalysisState =
        copy(sideAnalysisTexts = sideAnalysisTexts.withAnalysisText(player, text))

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
                sideAnalysisTexts = emptyMap(),
            )
    }
}

private fun Map<StoneColor, String>.withAnalysisText(
    player: StoneColor,
    text: String,
): Map<StoneColor, String> =
    if (text.isBlank()) {
        this
    } else {
        this + (player to text)
    }
