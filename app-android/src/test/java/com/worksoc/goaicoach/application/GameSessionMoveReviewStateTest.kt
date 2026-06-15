package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.undo.*

import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.autoai.*

import com.worksoc.goaicoach.application.score.*

import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class GameSessionMoveReviewStateTest {
    @Test
    fun resetCreatesEmptyMoveReviewState() {
        val state = GameSessionMoveReviewState.reset(
            moveReviewText = "No move review yet.",
            lastMoveText = "None",
        )

        assertEquals("No move review yet.", state.moveReviewText)
        assertEquals(emptyList<MoveReviewMarker>(), state.moveReviews)
        assertEquals("None", state.lastMoveText)
    }

    @Test
    fun applyHumanMoveLocalResultReplacesReviewTextMarkersAndLastMove() {
        val marker = MoveReviewMarker(
            coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            moveNumber = 1,
            tone = MoveReviewTone.Excellent,
        )
        val result = HumanMoveLocalResult(
            afterMove = GameState.empty(),
            moveReview = MoveReviewResult(
                marker = marker,
                text = "Move review: E5 excellent.",
            ),
            moveReviews = listOf(marker),
            lastMoveText = "Black E5",
            capturedText = "Captured: Black 0 / White 0",
            localScoreSnapshot = localScoreSnapshot(GameState.empty()),
            localFinalScore = null,
        )

        val next = GameSessionMoveReviewState
            .reset(moveReviewText = "old", lastMoveText = "old last")
            .applyHumanMoveLocalResult(result)

        assertEquals("Move review: E5 excellent.", next.moveReviewText)
        assertEquals(listOf(marker), next.moveReviews)
        assertEquals("Black E5", next.lastMoveText)
    }

    @Test
    fun applyUndoLocalStatePlanUsesUndoReviewSnapshot() {
        val marker = MoveReviewMarker(
            coordinate = BoardCoordinate.fromLabel("D4", BoardSize.Nine),
            moveNumber = 3,
            tone = MoveReviewTone.Good,
        )
        val undo = UndoLocalStatePlan(
            gameState = GameState.empty(),
            candidateText = "Undo cleared current Top Moves.",
            reviewAnalysis = MoveAnalysisSnapshot.empty(GameState.empty()),
            scoreText = "Score estimate not current.",
            scoreSnapshots = emptyList(),
            moveReviewText = "Move review cleared by undo.",
            moveReviews = listOf(marker),
            lastMoveText = "Black D4",
            endgameLog = "Endgame log cleared by undo.",
        )

        val next = GameSessionMoveReviewState
            .reset(moveReviewText = "old", lastMoveText = "old last")
            .applyUndoLocalStatePlan(undo)

        assertEquals("Move review cleared by undo.", next.moveReviewText)
        assertEquals(listOf(marker), next.moveReviews)
        assertEquals("Black D4", next.lastMoveText)
    }

    @Test
    fun applyAutoAiTurnDisplayPlanOnlyUpdatesLastMove() {
        val marker = MoveReviewMarker(
            coordinate = BoardCoordinate.fromLabel("C3", BoardSize.Nine),
            moveNumber = 2,
            tone = MoveReviewTone.Unknown,
        )
        val original = GameSessionMoveReviewState(
            moveReviewText = "existing review",
            moveReviews = listOf(marker),
            lastMoveText = "White C3",
        )
        val display = AutoAiTurnDisplayPlan(
            playLevel = PlayLevelSetting(),
            profile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            gameState = GameState.empty(),
            turnEngineMessage = "AI moved",
            candidateText = "candidate",
            lastMoveText = "Black E5",
            scoreDisplay = ScoreEstimateDisplayPlan(
                scoreText = "Score estimate not current.",
                scoreEstimate = null,
                scoreSnapshots = emptyList(),
                engineMessage = "score",
            ),
            shouldResolveEndgame = false,
            endgamePrePassCandidates = emptyList(),
            nextAnalysisState = GameState.empty(),
        )

        val next = original.applyAutoAiTurnDisplayPlan(display)

        assertEquals("existing review", next.moveReviewText)
        assertEquals(listOf(marker), next.moveReviews)
        assertEquals("Black E5", next.lastMoveText)
    }
}
