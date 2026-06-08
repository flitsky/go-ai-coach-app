package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanMoveApplicationTest {
    @Test
    fun applyHumanMoveLocallyReturnsAfterMoveReviewAndCapturedSummary() {
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val beforeMove = GameState.empty()
        val reviewAnalysis = MoveAnalysisSnapshot.from(
            state = beforeMove,
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, coordinate),
                    pointLoss = 0.0,
                ),
            ),
        )

        val result = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = Move.Play(StoneColor.Black, coordinate),
            reviewAnalysis = reviewAnalysis,
            previousMoveReviews = emptyList(),
        ).getOrThrow()

        assertEquals(StoneColor.White, result.afterMove.nextPlayer)
        assertEquals("Black E5", result.lastMoveText)
        assertEquals("Captured: Black 0 / White 0", result.capturedText)
        assertEquals(1, result.localScoreSnapshot.moveNumber)
        assertEquals(coordinate, result.moveReviews.single().coordinate)
        assertTrue(result.moveReview.text.contains("E5 excellent"))
        assertNull(result.localFinalScore)
    }

    @Test
    fun applyHumanMoveLocallyProducesLocalFinalScoreAfterConsecutivePasses() {
        val beforeMove = GameState.empty()
            .play(Move.Pass(StoneColor.Black))

        val result = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = Move.Pass(StoneColor.White),
            reviewAnalysis = MoveAnalysisSnapshot.empty(beforeMove),
            previousMoveReviews = emptyList(),
        ).getOrThrow()

        assertTrue(result.afterMove.hasConsecutivePasses())
        assertNotNull(result.localFinalScore)
        assertEquals("Move review: pass/resign has no board spot evaluation.", result.moveReview.text)
    }

    @Test
    fun applyHumanMoveLocallyFailsForIllegalMove() {
        val occupied = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val beforeMove = GameState.empty()
            .play(Move.Play(StoneColor.Black, occupied))

        val result = applyHumanMoveLocally(
            beforeMove = beforeMove,
            move = Move.Play(StoneColor.White, occupied),
            reviewAnalysis = MoveAnalysisSnapshot.empty(beforeMove),
            previousMoveReviews = emptyList(),
        )

        assertTrue(result.isFailure)
    }
}
