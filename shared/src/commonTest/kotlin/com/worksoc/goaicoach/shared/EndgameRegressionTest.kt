package com.worksoc.goaicoach.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EndgameRegressionTest {
    @Test
    fun detectsRightLowerDeadWhitePairFromDebugLog() {
        val state = passDeadStonesDebugState()

        val deadStones = DeadStoneDetector.capturableDeadStones(state)
            .map { it.label(BoardSize.Nine) }
            .toSet()

        assertTrue("G2" in deadStones)
        assertTrue("H2" in deadStones)
    }

    @Test
    fun removingRightLowerDeadPairScoresLikeExplicitCapture() {
        val state = passDeadStonesDebugState()
        val rawScore = BoardAreaScorer.score(state)

        val cleaned = DeadStoneCleaner.apply(
            state = state,
            deadStoneCoordinates = listOf(point("G2"), point("H2")),
        ).state
        val explicitCapture = state.play(Move.Play(StoneColor.Black, point("G1")))
        val cleanedScore = BoardAreaScorer.score(cleaned)
        val explicitCaptureScore = BoardAreaScorer.score(explicitCapture)

        assertEquals(null, cleaned.stoneAt(point("G2")))
        assertEquals(null, cleaned.stoneAt(point("H2")))
        assertEquals(null, explicitCapture.stoneAt(point("G2")))
        assertEquals(null, explicitCapture.stoneAt(point("H2")))
        assertEquals(14, cleaned.capturedBy(StoneColor.Black))
        assertEquals(14, explicitCapture.capturedBy(StoneColor.Black))
        assertEquals(explicitCaptureScore.rawScore, cleanedScore.rawScore)
        assertTrue(rawScore.rawScore != cleanedScore.rawScore)
    }

    @Test
    fun passBeforeCleanupUsesPrePassTopMoveWhenLocalFinalFlipsWinner() {
        val finalState = passBeforeCleanupDebugState()
            .play(Move.Pass(StoneColor.Black))
        val rawLocalScore = BoardAreaScorer.score(finalState)
        val fallbackCleanup = DeadStoneCleaner.apply(
            state = finalState,
            deadStoneCoordinates = DeadStoneDetector.capturableDeadStones(finalState),
        )
        val cleanup = DeadStoneCleanupResult(
            state = finalState,
            removedStones = emptyList(),
        )
        val selection = EndgameScoreSelector.selectDisplayScore(
            cleanup = cleanup,
            localScore = rawLocalScore,
            engineEstimate = ScoreEstimate(
                status = EngineStatus.ready("Post-pass estimate"),
                whiteScoreLead = 3.046,
                summary = "Post-pass estimate",
            ),
            prePassCandidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, point("J5")),
                    scoreLead = 6.9,
                ),
                CandidateMove(
                    move = Move.Play(StoneColor.Black, point("G7")),
                    scoreLead = 6.8,
                ),
                CandidateMove(
                    move = Move.Pass(StoneColor.Black),
                    scoreLead = -4.1,
                ),
            ),
        )

        assertTrue(finalState.hasConsecutivePasses())
        assertEquals("W+4.5", rawLocalScore.rawScore)
        assertEquals("B+6.5", BoardAreaScorer.score(fallbackCleanup.state).rawScore)
        assertEquals(EndgameScoreSource.UnsettledPrePassTopMoveEstimate, selection.source)
        assertEquals("B+6.9?", selection.displayScore.rawScore)
    }

    private fun passDeadStonesDebugState(): GameState =
        GameState.empty(boardSize = BoardSize.Nine, ruleset = Ruleset.Chinese).copy(
            nextPlayer = StoneColor.Black,
            stones = mapOf(
                point("A9") to StoneColor.White,
                point("B9") to StoneColor.Black,
                point("C9") to StoneColor.Black,
                point("D9") to StoneColor.Black,
                point("F9") to StoneColor.Black,
                point("J9") to StoneColor.Black,
                point("A8") to StoneColor.White,
                point("B8") to StoneColor.White,
                point("C8") to StoneColor.Black,
                point("D8") to StoneColor.Black,
                point("E8") to StoneColor.Black,
                point("F8") to StoneColor.Black,
                point("G8") to StoneColor.Black,
                point("H8") to StoneColor.Black,
                point("J8") to StoneColor.Black,
                point("A7") to StoneColor.White,
                point("B7") to StoneColor.White,
                point("C7") to StoneColor.White,
                point("D7") to StoneColor.Black,
                point("E7") to StoneColor.White,
                point("F7") to StoneColor.Black,
                point("G7") to StoneColor.Black,
                point("H7") to StoneColor.White,
                point("J7") to StoneColor.Black,
                point("B6") to StoneColor.White,
                point("C6") to StoneColor.White,
                point("D6") to StoneColor.White,
                point("E6") to StoneColor.White,
                point("F6") to StoneColor.White,
                point("G6") to StoneColor.Black,
                point("H6") to StoneColor.White,
                point("J6") to StoneColor.White,
                point("C5") to StoneColor.White,
                point("D5") to StoneColor.White,
                point("F5") to StoneColor.White,
                point("G5") to StoneColor.White,
                point("H5") to StoneColor.White,
                point("J5") to StoneColor.White,
                point("C4") to StoneColor.White,
                point("D4") to StoneColor.White,
                point("E4") to StoneColor.White,
                point("F4") to StoneColor.Black,
                point("G4") to StoneColor.Black,
                point("H4") to StoneColor.White,
                point("J4") to StoneColor.Black,
                point("A3") to StoneColor.White,
                point("B3") to StoneColor.White,
                point("C3") to StoneColor.White,
                point("D3") to StoneColor.White,
                point("E3") to StoneColor.Black,
                point("G3") to StoneColor.Black,
                point("H3") to StoneColor.Black,
                point("J3") to StoneColor.Black,
                point("A2") to StoneColor.White,
                point("B2") to StoneColor.Black,
                point("C2") to StoneColor.White,
                point("D2") to StoneColor.Black,
                point("E2") to StoneColor.Black,
                point("F2") to StoneColor.Black,
                point("G2") to StoneColor.White,
                point("H2") to StoneColor.White,
                point("J2") to StoneColor.Black,
                point("A1") to StoneColor.White,
                point("B1") to StoneColor.Black,
                point("C1") to StoneColor.Black,
                point("D1") to StoneColor.Black,
                point("E1") to StoneColor.Black,
                point("H1") to StoneColor.Black,
            ),
            capturedByBlack = 12,
            capturedByWhite = 10,
        )

    private fun passBeforeCleanupDebugState(): GameState =
        GameState.empty(boardSize = BoardSize.Nine, ruleset = Ruleset.Chinese).copy(
            nextPlayer = StoneColor.Black,
            stones = mapOf(
                point("A9") to StoneColor.Black,
                point("B9") to StoneColor.Black,
                point("C9") to StoneColor.Black,
                point("D9") to StoneColor.Black,
                point("G9") to StoneColor.Black,
                point("A8") to StoneColor.White,
                point("B8") to StoneColor.White,
                point("C8") to StoneColor.Black,
                point("D8") to StoneColor.Black,
                point("F8") to StoneColor.Black,
                point("H8") to StoneColor.Black,
                point("B7") to StoneColor.White,
                point("C7") to StoneColor.White,
                point("D7") to StoneColor.Black,
                point("E7") to StoneColor.Black,
                point("F7") to StoneColor.Black,
                point("H7") to StoneColor.Black,
                point("B6") to StoneColor.White,
                point("C6") to StoneColor.White,
                point("D6") to StoneColor.White,
                point("E6") to StoneColor.Black,
                point("F6") to StoneColor.White,
                point("G6") to StoneColor.Black,
                point("H6") to StoneColor.White,
                point("J6") to StoneColor.Black,
                point("B5") to StoneColor.White,
                point("C5") to StoneColor.White,
                point("D5") to StoneColor.Black,
                point("E5") to StoneColor.Black,
                point("F5") to StoneColor.White,
                point("G5") to StoneColor.Black,
                point("H5") to StoneColor.White,
                point("B4") to StoneColor.White,
                point("D4") to StoneColor.White,
                point("E4") to StoneColor.Black,
                point("F4") to StoneColor.White,
                point("G4") to StoneColor.Black,
                point("H4") to StoneColor.Black,
                point("A3") to StoneColor.White,
                point("B3") to StoneColor.White,
                point("D3") to StoneColor.White,
                point("E3") to StoneColor.White,
                point("F3") to StoneColor.White,
                point("G3") to StoneColor.Black,
                point("A2") to StoneColor.White,
                point("C2") to StoneColor.White,
                point("D2") to StoneColor.White,
                point("E2") to StoneColor.White,
                point("F2") to StoneColor.Black,
                point("H2") to StoneColor.Black,
                point("B1") to StoneColor.White,
                point("C1") to StoneColor.White,
                point("D1") to StoneColor.White,
                point("E1") to StoneColor.Black,
                point("F1") to StoneColor.Black,
                point("G1") to StoneColor.Black,
            ),
            moves = listOf(Move.Pass(StoneColor.White)),
            capturedByBlack = 5,
            capturedByWhite = 6,
        )

    private fun point(label: String): BoardCoordinate =
        BoardCoordinate.fromLabel(label, BoardSize.Nine)
}
