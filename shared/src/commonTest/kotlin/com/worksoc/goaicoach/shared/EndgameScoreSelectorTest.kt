package com.worksoc.goaicoach.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class EndgameScoreSelectorTest {
    @Test
    fun selectsUnsettledEngineEstimateWhenLocalAreaConflictsWithoutDeadStoneCleanup() {
        val localScore = FinalScoreResult(
            status = EngineStatus.ready("Local score"),
            rawScore = "W+3.5",
            winner = StoneColor.White,
            margin = 3.5,
            blackArea = 35.0,
            whiteAreaWithKomi = 38.5,
            komi = 6.5,
            summary = "Local area",
        )
        val engineEstimate = ScoreEstimate(
            status = EngineStatus.ready("Estimate"),
            whiteScoreLead = -28.527,
            whiteWinRate = 0.01,
            summary = "Estimate",
        )

        val selection = EndgameScoreSelector.selectDisplayScore(
            cleanup = DeadStoneCleanupResult(GameState.empty(), emptyList()),
            localScore = localScore,
            engineEstimate = engineEstimate,
        )

        assertEquals(EndgameScoreSource.UnsettledEngineEstimate, selection.source)
        assertEquals(StoneColor.Black, selection.displayScore.winner)
        assertEquals("B+28.5?", selection.displayScore.rawScore)
    }

    @Test
    fun keepsCleanedLocalAreaWhenDeadStonesWereRemoved() {
        val localScore = FinalScoreResult(
            status = EngineStatus.ready("Local score"),
            rawScore = "B+12.5",
            winner = StoneColor.Black,
            margin = 12.5,
            blackArea = 50.0,
            whiteAreaWithKomi = 37.5,
            komi = 6.5,
            summary = "Local area",
        )
        val cleanup = DeadStoneCleanupResult(
            state = GameState.empty(),
            removedStones = listOf(
                DeadStoneRemoval(
                    coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
                    color = StoneColor.White,
                ),
            ),
        )

        val selection = EndgameScoreSelector.selectDisplayScore(
            cleanup = cleanup,
            localScore = localScore,
            engineEstimate = ScoreEstimate(
                status = EngineStatus.ready("Estimate"),
                whiteScoreLead = -40.0,
                summary = "Estimate",
            ),
        )

        assertEquals(EndgameScoreSource.CleanedLocalArea, selection.source)
        assertEquals(localScore, selection.displayScore)
    }

    @Test
    fun selectsUnsettledPrePassTopMoveEstimateWhenPassFinalConflictsWithBestContinuation() {
        val localScore = FinalScoreResult(
            status = EngineStatus.ready("Local score"),
            rawScore = "W+4.5",
            winner = StoneColor.White,
            margin = 4.5,
            blackArea = 38.0,
            whiteAreaWithKomi = 42.5,
            komi = 6.5,
            summary = "Local area",
        )
        val cleanup = DeadStoneCleanupResult(
            state = GameState.empty(),
            removedStones = listOf(
                DeadStoneRemoval(
                    coordinate = BoardCoordinate.fromLabel("B1", BoardSize.Nine),
                    color = StoneColor.White,
                ),
            ),
        )
        val prePassCandidates = listOf(
            CandidateMove(
                move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("J5", BoardSize.Nine)),
                scoreLead = 6.9,
            ),
            CandidateMove(
                move = Move.Pass(StoneColor.Black),
                scoreLead = -4.1,
            ),
        )

        val selection = EndgameScoreSelector.selectDisplayScore(
            cleanup = cleanup,
            localScore = localScore,
            engineEstimate = ScoreEstimate(
                status = EngineStatus.ready("Estimate"),
                whiteScoreLead = 3.0,
                summary = "Post-pass estimate",
            ),
            prePassCandidates = prePassCandidates,
        )

        assertEquals(EndgameScoreSource.UnsettledPrePassTopMoveEstimate, selection.source)
        assertEquals(StoneColor.Black, selection.displayScore.winner)
        assertEquals("B+6.9?", selection.displayScore.rawScore)
    }
}
