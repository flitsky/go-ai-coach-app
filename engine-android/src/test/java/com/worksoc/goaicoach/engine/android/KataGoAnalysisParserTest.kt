package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KataGoAnalysisParserTest {
    @Test
    fun parsesKataGoInfoMoves() {
        val response = """
            info move C5 visits 2 winrate 0.182844 scoreLead 0.00741314 prior 0.2244 order 0 pv C5 G4 info move G5 visits 1 winrate 0.48 scoreLead -0.4 order 1 pv G5
            play C5
        """.trimIndent()

        val candidates = KataGoAnalysisParser.parseCandidates(
            response = response,
            player = StoneColor.White,
            boardSize = BoardSize.Nine,
        )

        assertEquals(2, candidates.size)
        assertEquals(2, candidates[0].visits)
        assertEquals(0.182844, candidates[0].winRate)
        assertEquals(0.00741314, candidates[0].scoreLead)
        assertEquals(0.2244, candidates[0].policyPrior)
        assertEquals("KataGo order 0", candidates[0].note)

        val firstMove = assertIs<Move.Play>(candidates[0].move)
        assertEquals(StoneColor.White, firstMove.player)
        assertEquals("C5", firstMove.coordinate.label(BoardSize.Nine))

        val secondMove = assertIs<Move.Play>(candidates[1].move)
        assertEquals("G5", secondMove.coordinate.label(BoardSize.Nine))
    }

    @Test
    fun normalizesLegacyIntegerWinRate() {
        val response = "info move D4 visits 10 winrate 5234 scoreLead 1.5 order 0 pv D4"

        val candidates = KataGoAnalysisParser.parseCandidates(
            response = response,
            player = StoneColor.Black,
            boardSize = BoardSize.Nine,
        )

        assertEquals(0.5234, candidates.single().winRate)
    }

    @Test
    fun respectsMaxCandidateCount() {
        val response = """
            info move C5 visits 2 winrate 0.5 scoreLead 0 order 0 pv C5
            info move D5 visits 2 winrate 0.4 scoreLead 0 order 1 pv D5
            info move E5 visits 2 winrate 0.3 scoreLead 0 order 2 pv E5
        """.trimIndent()

        val candidates = KataGoAnalysisParser.parseCandidates(
            response = response,
            player = StoneColor.Black,
            boardSize = BoardSize.Nine,
            maxCandidates = 2,
        )

        assertEquals(2, candidates.size)
    }

    @Test
    fun attachesPointLossFromTopCandidateForPlayerPerspective() {
        val blackCandidates = listOf(
            CandidateMove(move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("F6", BoardSize.Nine)), scoreLead = -2.0),
            CandidateMove(move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)), scoreLead = -0.5),
        )
        val whiteCandidates = listOf(
            CandidateMove(move = Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)), scoreLead = 12.0),
            CandidateMove(move = Move.Play(StoneColor.White, BoardCoordinate.fromLabel("E5", BoardSize.Nine)), scoreLead = 9.5),
        )

        val blackWithLoss = KataGoAnalysisParser.attachPointLoss(blackCandidates, StoneColor.Black)
        val whiteWithLoss = KataGoAnalysisParser.attachPointLoss(whiteCandidates, StoneColor.White)

        assertEquals(0.0, blackWithLoss[0].pointLoss)
        assertEquals(1.5, blackWithLoss[1].pointLoss)
        assertEquals(0.0, whiteWithLoss[0].pointLoss)
        assertEquals(2.5, whiteWithLoss[1].pointLoss)
    }

    @Test
    fun parsesRawPolicyCandidates() {
        val response = """
            symmetry 0
            whiteWin 0.213804
            policy
            0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01
            0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01
            0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01
            0.01 0.01 0.01 0.01 0.01 0.30 0.01 0.01 0.01
            0.01 0.01 0.01 0.01 0.50 0.20 0.01 0.01 0.01
            0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01
            0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01
            0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01
            0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01 0.01
            policyPass 0.000024
        """.trimIndent()

        val candidates = KataGoAnalysisParser.parsePolicyCandidates(
            response = response,
            player = StoneColor.Black,
            boardSize = BoardSize.Nine,
            maxCandidates = 2,
            excludedCoordinates = setOf(BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
        )

        assertEquals(2, candidates.size)
        assertEquals("F6", (candidates[0].move as Move.Play).coordinate.label(BoardSize.Nine))
        assertEquals(0.30, candidates[0].policyPrior)
        assertEquals("F5", (candidates[1].move as Move.Play).coordinate.label(BoardSize.Nine))
        assertEquals(0.20, candidates[1].policyPrior)
    }

    @Test
    fun parsesRawScoreEstimate() {
        val response = """
            whiteWin 0.62
            whiteLead 3.5
            whiteOwnership
            -0.20 -0.20 -0.20 -0.20 -0.20 -0.20 -0.20 -0.20 -0.20
            -0.05 -0.05 -0.05 -0.05 -0.05 -0.05 -0.05 -0.05 -0.05
            0.25 0.25 0.25 0.25 0.25 0.25 0.25 0.25 0.25
            0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00
            0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00
            0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00
            0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00
            0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00
            0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00
        """.trimIndent()

        val estimate = KataGoAnalysisParser.parseScoreEstimate(response, BoardSize.Nine)

        assertEquals(0.62, estimate.whiteWinRate)
        assertEquals(3.5, estimate.whiteScoreLead)
        assertEquals(9, estimate.ownership?.blackLikelyPoints)
        assertEquals(9, estimate.ownership?.whiteLikelyPoints)
        assertEquals(63, estimate.ownership?.neutralOrUnclearPoints)
    }

    @Test
    fun parsesFinalScore() {
        val finalScore = KataGoAnalysisParser.parseFinalScore("W+6.5")

        assertEquals(StoneColor.White, finalScore.winner)
        assertEquals(6.5, finalScore.margin)
        assertEquals("W+6.5", finalScore.rawScore)
    }
}
