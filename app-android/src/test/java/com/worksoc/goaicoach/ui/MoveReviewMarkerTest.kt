package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.MoveReviewTone
import com.worksoc.goaicoach.application.moveReviewToneFor
import com.worksoc.goaicoach.application.topMoveDisplayToneFor
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveReviewMarkerTest {
    @Test
    fun topMoveDisplayUsesAbsoluteToneWhenBestCandidateIsNotSevereLoss() {
        assertEquals(
            MoveReviewTone.Excellent,
            topMoveDisplayToneFor(pointLoss = 0.4, bestShownPointLoss = 0.4, worstShownPointLoss = 7.0),
        )
        assertEquals(
            MoveReviewTone.Inaccuracy,
            topMoveDisplayToneFor(pointLoss = 2.5, bestShownPointLoss = 0.4, worstShownPointLoss = 7.0),
        )
        assertEquals(
            MoveReviewTone.Blunder,
            topMoveDisplayToneFor(pointLoss = 7.0, bestShownPointLoss = 0.4, worstShownPointLoss = 7.0),
        )
    }

    @Test
    fun topMoveDisplayNormalizesSparseHighLossCandidatesToYellowOrangeRed() {
        assertEquals(
            MoveReviewTone.Inaccuracy,
            topMoveDisplayToneFor(pointLoss = 6.9, bestShownPointLoss = 6.9, worstShownPointLoss = 10.5),
        )
        assertEquals(
            MoveReviewTone.Mistake,
            topMoveDisplayToneFor(pointLoss = 8.0, bestShownPointLoss = 6.9, worstShownPointLoss = 10.5),
        )
        assertEquals(
            MoveReviewTone.Mistake,
            topMoveDisplayToneFor(pointLoss = 8.6, bestShownPointLoss = 6.9, worstShownPointLoss = 10.5),
        )
        assertEquals(
            MoveReviewTone.Blunder,
            topMoveDisplayToneFor(pointLoss = 10.5, bestShownPointLoss = 6.9, worstShownPointLoss = 10.5),
        )
    }

    @Test
    fun topMoveDisplayUsesYellowWhenOnlyOneHighLossCandidateIsShown() {
        assertEquals(
            MoveReviewTone.Inaccuracy,
            topMoveDisplayToneFor(pointLoss = 6.9, bestShownPointLoss = 6.9, worstShownPointLoss = 6.9),
        )
    }

    @Test
    fun moveReviewToneRemainsAbsoluteForPlayedMoveFeedback() {
        assertEquals(MoveReviewTone.Blunder, moveReviewToneFor(pointLoss = 6.9))
        assertEquals(
            MoveReviewTone.Unknown,
            topMoveDisplayToneFor(pointLoss = null, bestShownPointLoss = 6.9, worstShownPointLoss = 10.5),
        )
    }
}
