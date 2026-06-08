package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UndoApplicationTest {
    @Test
    fun localTwoPlayerUndoPlanReplaysOneMoveBackAndRecordsLocalSnapshot() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))

        val plan = buildLocalTwoPlayerUndoPlan(
            currentState = state,
            scoreSnapshots = listOf(ScoreSnapshot(moveNumber = 2, source = ScoreSnapshotSource.EngineEstimate)),
        )

        assertEquals(1, plan.gameState.moves.size)
        assertEquals("Black E5", plan.lastMoveText)
        assertEquals("Captured: Black 0 / White 0", plan.candidateText)
        assertEquals("Score estimate not current.", plan.scoreText)
        assertEquals("Move review cleared by undo.", plan.moveReviewText)
        assertEquals(1, plan.scoreSnapshots.single().moveNumber)
        assertFalse(plan.reviewAnalysis.hasEngineCandidates)
    }

    @Test
    fun engineUndoPlanTrimsStateTimelineAndMoveReviewMarkers() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("F5", BoardSize.Nine)))
        val markers = listOf(
            MoveReviewMarker(
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
                moveNumber = 1,
                tone = MoveReviewTone.Excellent,
            ),
            MoveReviewMarker(
                coordinate = BoardCoordinate.fromLabel("F5", BoardSize.Nine),
                moveNumber = 3,
                tone = MoveReviewTone.Blunder,
            ),
        )

        val plan = buildEngineUndoPlan(
            currentState = state,
            undoCount = 2,
            previousMoveReviews = markers,
            scoreSnapshots = listOf(
                ScoreSnapshot(moveNumber = 1, source = ScoreSnapshotSource.EngineEstimate),
                ScoreSnapshot(moveNumber = 3, source = ScoreSnapshotSource.EngineEstimate),
            ),
        )

        assertEquals(1, plan.gameState.moves.size)
        assertEquals("Black E5", plan.lastMoveText)
        assertEquals("Undo cleared current Top Moves.", plan.candidateText)
        assertEquals(listOf(markers.first()), plan.moveReviews)
        assertEquals(1, plan.scoreSnapshots.single().moveNumber)
        assertFalse(plan.reviewAnalysis.hasEngineCandidates)
    }
}
