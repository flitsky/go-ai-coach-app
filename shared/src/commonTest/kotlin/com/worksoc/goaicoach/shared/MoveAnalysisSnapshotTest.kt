package com.worksoc.goaicoach.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MoveAnalysisSnapshotTest {
    @Test
    fun keepsAllLegalMovesEvenWhenEngineReturnsFewCandidates() {
        val state = GameState.empty(BoardSize.Nine, Ruleset.Japanese)
        val candidates = listOf(
            CandidateMove(
                move = Move.Play(StoneColor.Black, point("E5")),
                pointLoss = 0.0,
                scoreLead = -0.5,
            ),
            CandidateMove(
                move = Move.Play(StoneColor.Black, point("D5")),
                policyPrior = 0.24,
            ),
        )

        val snapshot = MoveAnalysisSnapshot.from(state, candidates)

        assertEquals(81, snapshot.legalPlayCount)
        assertEquals(1, snapshot.scoredPlayCount)
        assertEquals(1, snapshot.policyOnlyPlayCount)
        assertEquals(79, snapshot.legalOnlyPlayCount)
        assertEquals(2, snapshot.sourceCandidateCount)
        assertEquals(1, snapshot.candidatesForDisplay().size)
        assertEquals(point("E5"), (snapshot.candidatesForDisplay()[0].move as Move.Play).coordinate)
        assertNotNull(snapshot.candidateAt(point("A1")))
        assertNull(snapshot.candidateAt(point("A1"))?.pointLoss)
    }

    @Test
    fun excludesOccupiedAndIllegalMovesFromSnapshot() {
        val state = GameState.empty(BoardSize.Nine, Ruleset.Japanese).copy(
            nextPlayer = StoneColor.White,
            stones = mapOf(
                point("E5") to StoneColor.Black,
                point("D5") to StoneColor.Black,
                point("F5") to StoneColor.Black,
                point("E4") to StoneColor.Black,
                point("E6") to StoneColor.Black,
            ),
        )

        val snapshot = MoveAnalysisSnapshot.from(
            state = state,
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.White, point("E5")),
                    pointLoss = 0.0,
                ),
            ),
        )

        assertNull(snapshot.candidateAt(point("E5")))
    }

    private fun point(label: String): BoardCoordinate =
        BoardCoordinate.fromLabel(label, BoardSize.Nine)
}
