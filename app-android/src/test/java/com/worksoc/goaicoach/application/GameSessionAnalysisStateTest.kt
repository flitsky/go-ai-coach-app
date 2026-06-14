package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GameSessionAnalysisStateTest {
    @Test
    fun resetCreatesEmptyDisplayAndReviewState() {
        val state = GameState.empty()
        val analysis = GameSessionAnalysisState.empty(state)

        assertEquals("No analysis yet.", analysis.candidateText)
        assertEquals(emptyList<CandidateMove>(), analysis.candidateMoves)
        assertEquals(emptyList<CandidateMove>(), analysis.reviewCandidateMoves)
        assertFalse(analysis.reviewAnalysis.hasEngineCandidates)
        assertNull(analysis.lastAnalysisKey)
    }

    @Test
    fun clearTopMoveSpotsOnlyClearsDisplayedCandidatesAndOptionalText() {
        val state = GameState.empty()
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val original = GameSessionAnalysisState(
            candidateMoves = listOf(candidate),
            candidateText = "old text",
            reviewAnalysis = MoveAnalysisSnapshot.from(state, listOf(candidate)),
            reviewCandidateMoves = listOf(candidate),
            lastAnalysisKey = analysisKeyFor(
                state = state,
                preset = AnalysisPreset.Lite,
                limit = EngineProfile().analysisLimit,
                deep = false,
            ),
        )

        val clearedWithoutMessage = original.clearTopMoveSpots()
        val clearedWithMessage = original.clearTopMoveSpots("new text")

        assertEquals(emptyList<CandidateMove>(), clearedWithoutMessage.candidateMoves)
        assertEquals("old text", clearedWithoutMessage.candidateText)
        assertEquals(original.reviewAnalysis, clearedWithoutMessage.reviewAnalysis)
        assertEquals(original.reviewCandidateMoves, clearedWithoutMessage.reviewCandidateMoves)
        assertEquals(original.lastAnalysisKey, clearedWithoutMessage.lastAnalysisKey)
        assertEquals("new text", clearedWithMessage.candidateText)
    }

    @Test
    fun clearReviewAnalysisKeepsTopMoveDisplayAndClearsReviewSnapshot() {
        val state = GameState.empty()
        val nextState = state.play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("D4", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val original = GameSessionAnalysisState(
            candidateMoves = listOf(candidate),
            candidateText = "candidate text",
            reviewAnalysis = MoveAnalysisSnapshot.from(state, listOf(candidate)),
            reviewCandidateMoves = listOf(candidate),
            lastAnalysisKey = null,
        )

        val cleared = original.clearReviewAnalysis(nextState)

        assertEquals(listOf(candidate), cleared.candidateMoves)
        assertEquals("candidate text", cleared.candidateText)
        assertEquals(emptyList<CandidateMove>(), cleared.reviewCandidateMoves)
        assertFalse(cleared.reviewAnalysis.hasEngineCandidates)
        assertEquals(StoneColor.White, cleared.reviewAnalysis.player)
    }

    @Test
    fun applyTopMoveAnalysisUpdateReplacesAnalysisDisplayAndCacheKey() {
        val state = GameState.empty()
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val snapshot = MoveAnalysisSnapshot.from(state, listOf(candidate))
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val update = TopMoveAnalysisUpdate(
            snapshot = snapshot,
            reviewCandidateMoves = snapshot.candidatesForReview(),
            candidateMoves = snapshot.candidatesForDisplay(),
            candidateText = "updated text",
            engineMessage = "engine ready",
            cachedResult = null,
        )

        val applied = GameSessionAnalysisState.empty(state)
            .applyTopMoveAnalysisUpdate(update, plan.analysisKey)

        assertEquals(snapshot, applied.reviewAnalysis)
        assertEquals(snapshot.candidatesForReview(), applied.reviewCandidateMoves)
        assertEquals(snapshot.candidatesForDisplay(), applied.candidateMoves)
        assertEquals("updated text", applied.candidateText)
        assertEquals(plan.analysisKey, applied.lastAnalysisKey)
    }

    @Test
    fun applyTopMoveAnalysisFailureClearsReviewAndOptionallyDisplayedMoves() {
        val state = GameState.empty()
        val targetState = state.play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("D4", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val analysisKey = analysisKeyFor(
            state = state,
            preset = AnalysisPreset.Lite,
            limit = EngineProfile().analysisLimit,
            deep = false,
        )
        val original = GameSessionAnalysisState(
            candidateMoves = listOf(candidate),
            candidateText = "previous text",
            reviewAnalysis = MoveAnalysisSnapshot.from(state, listOf(candidate)),
            reviewCandidateMoves = listOf(candidate),
            lastAnalysisKey = analysisKey,
        )

        val hidden = original.applyTopMoveAnalysisFailureDisplayPlan(
            TopMoveAnalysisFailureDisplayPlan(
                targetState = targetState,
                engineMessage = "failed",
                clearDisplayedTopMoves = false,
            ),
        )
        val shown = original.applyTopMoveAnalysisFailureDisplayPlan(
            TopMoveAnalysisFailureDisplayPlan(
                targetState = targetState,
                engineMessage = "failed",
                clearDisplayedTopMoves = true,
                candidateText = "Top Moves analysis failed.",
            ),
        )

        assertEquals(listOf(candidate), hidden.candidateMoves)
        assertEquals("previous text", hidden.candidateText)
        assertEquals(emptyList<CandidateMove>(), hidden.reviewCandidateMoves)
        assertFalse(hidden.reviewAnalysis.hasEngineCandidates)
        assertNull(hidden.lastAnalysisKey)
        assertEquals(StoneColor.White, hidden.reviewAnalysis.player)

        assertEquals(emptyList<CandidateMove>(), shown.candidateMoves)
        assertEquals("Top Moves analysis failed.", shown.candidateText)
        assertEquals(emptyList<CandidateMove>(), shown.reviewCandidateMoves)
        assertFalse(shown.reviewAnalysis.hasEngineCandidates)
        assertNull(shown.lastAnalysisKey)
    }
}
