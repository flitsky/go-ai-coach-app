package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.movereview.*
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveReviewTest {
    @Test
    fun buildMoveReviewCreatesMarkerForMatchedCandidate() {
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val snapshot = MoveAnalysisSnapshot.from(
            state = GameState.empty(),
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, coordinate),
                    pointLoss = 0.2,
                    policyPrior = 0.84,
                ),
            ),
        )

        val review = buildMoveReview(
            move = Move.Play(StoneColor.Black, coordinate),
            analysis = snapshot,
            boardSize = BoardSize.Nine,
            moveNumber = 1,
        )

        assertEquals(coordinate, review.marker?.coordinate)
        assertEquals(MoveReviewTone.Excellent, review.marker?.tone)
        assertTrue(review.text.contains("E5 excellent"))
        assertTrue(review.text.contains("loss 0.2"))
        assertTrue(review.text.contains("policy 84%"))
    }

    @Test
    fun buildMoveReviewDoesNotCreateMarkerForPassOrMissingAnalysis() {
        val passReview = buildMoveReview(
            move = Move.Pass(StoneColor.Black),
            analysis = MoveAnalysisSnapshot.empty(GameState.empty()),
            boardSize = BoardSize.Nine,
            moveNumber = 1,
        )
        val playReview = buildMoveReview(
            move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
            analysis = MoveAnalysisSnapshot.empty(GameState.empty()),
            boardSize = BoardSize.Nine,
            moveNumber = 1,
        )

        assertNull(passReview.marker)
        assertTrue(passReview.text.contains("pass/resign"))
        assertNull(playReview.marker)
        assertTrue(playReview.text.contains("no pre-move analysis cache"))
    }

    @Test
    fun withReviewMarkerReplacesMarkerFromSameMoveNumber() {
        val first = MoveReviewMarker(
            coordinate = BoardCoordinate.fromLabel("D4", BoardSize.Nine),
            moveNumber = 3,
            tone = MoveReviewTone.Mistake,
        )
        val replacement = first.copy(
            coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            tone = MoveReviewTone.Excellent,
        )

        val markers = listOf(first).withReviewMarker(replacement)

        assertEquals(listOf(replacement), markers)
    }
}
