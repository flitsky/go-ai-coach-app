package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.pointLossLabel
import com.worksoc.goaicoach.application.toCandidateText
import com.worksoc.goaicoach.application.topMoveDeltaScoreLabel
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveValueDisplayTest {
    @Test
    fun topMoveBoardLabelUsesKatrainStyleDeltaScore() {
        val candidate = CandidateMove(
            move = Move.Play(StoneColor.Black, BoardCoordinate(row = 3, column = 4)),
            scoreLead = -12.0,
            pointLoss = 0.2,
        )

        assertEquals("0.2", candidate.pointLossLabel())
        assertEquals("-0.2", candidate.topMoveDeltaScoreLabel())
    }

    @Test
    fun zeroPointLossDisplaysAsNeutralLoss() {
        val candidate = CandidateMove(
            move = Move.Play(StoneColor.White, BoardCoordinate(row = 3, column = 4)),
            pointLoss = 0.0,
        )

        assertEquals("0.0", candidate.pointLossLabel())
        assertEquals("0.0", candidate.topMoveDeltaScoreLabel())
    }

    @Test
    fun negativePointLossIsNotDisplayedAsNegativeLoss() {
        val candidate = CandidateMove(
            move = Move.Play(StoneColor.Black, BoardCoordinate(row = 3, column = 4)),
            pointLoss = -1.5,
        )

        assertEquals("0.0", candidate.pointLossLabel())
        assertEquals("0.0", candidate.topMoveDeltaScoreLabel())
    }

    @Test
    fun candidateTextShowsLossAndDoesNotExposeLeadByDefault() {
        val result = AnalysisResult(
            status = EngineStatus.ready("ready"),
            summary = "analysis complete",
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, BoardCoordinate(row = 3, column = 4)),
                    winRate = 0.26,
                    scoreLead = -0.8,
                    pointLoss = 0.2,
                    visits = 1,
                    policyPrior = 0.94,
                ),
            ),
        )

        val text = result.toCandidateText(BoardSize.Nine)

        assertTrue(text.contains("loss=0.2"))
        assertFalse(text.contains("lead="))
    }

    @Test
    fun candidateTextKeepsEngineOrderEvenWhenPointLossIsHigher() {
        val result = AnalysisResult(
            status = EngineStatus.ready("ready"),
            summary = "analysis complete",
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("F5", BoardSize.Nine)),
                    winRate = 0.74,
                    pointLoss = 0.3,
                    visits = 1,
                ),
                CandidateMove(
                    move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("B3", BoardSize.Nine)),
                    winRate = 0.56,
                    pointLoss = 0.0,
                    visits = 1,
                ),
            ),
        )

        val text = result.toCandidateText(BoardSize.Nine)

        assertTrue(text.contains("1. Black F5"))
        assertTrue(text.contains("2. Black B3"))
    }
}
