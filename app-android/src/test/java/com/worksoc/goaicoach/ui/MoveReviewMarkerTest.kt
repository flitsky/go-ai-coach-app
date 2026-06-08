package com.worksoc.goaicoach.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveReviewMarkerTest {
    @Test
    fun topMoveDisplayUsesAbsoluteToneWhenBestCandidateIsNotSevereLoss() {
        assertEquals(
            MoveReviewTone.Excellent,
            topMoveDisplayToneFor(pointLoss = 0.4, bestShownPointLoss = 0.4),
        )
        assertEquals(
            MoveReviewTone.Inaccuracy,
            topMoveDisplayToneFor(pointLoss = 2.5, bestShownPointLoss = 0.4),
        )
        assertEquals(
            MoveReviewTone.Blunder,
            topMoveDisplayToneFor(pointLoss = 7.0, bestShownPointLoss = 0.4),
        )
    }

    @Test
    fun topMoveDisplayNormalizesSparseHighLossCandidatesRelativeToBestShownMove() {
        assertEquals(
            MoveReviewTone.Good,
            topMoveDisplayToneFor(pointLoss = 6.9, bestShownPointLoss = 6.9),
        )
        assertEquals(
            MoveReviewTone.Inaccuracy,
            topMoveDisplayToneFor(pointLoss = 8.0, bestShownPointLoss = 6.9),
        )
        assertEquals(
            MoveReviewTone.Mistake,
            topMoveDisplayToneFor(pointLoss = 8.6, bestShownPointLoss = 6.9),
        )
        assertEquals(
            MoveReviewTone.Blunder,
            topMoveDisplayToneFor(pointLoss = 10.5, bestShownPointLoss = 6.9),
        )
    }

    @Test
    fun moveReviewToneRemainsAbsoluteForPlayedMoveFeedback() {
        assertEquals(MoveReviewTone.Blunder, moveReviewToneFor(pointLoss = 6.9))
        assertEquals(MoveReviewTone.Unknown, topMoveDisplayToneFor(pointLoss = null, bestShownPointLoss = 6.9))
    }
}
