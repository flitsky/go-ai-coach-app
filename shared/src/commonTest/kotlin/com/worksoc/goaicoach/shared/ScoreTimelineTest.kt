package com.worksoc.goaicoach.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ScoreTimelineTest {
    @Test
    fun recordReplacesSameMoveNumberAndKeepsOrder() {
        val snapshots = listOf(
            ScoreSnapshot(moveNumber = 2, whiteScoreLead = 1.0, source = ScoreSnapshotSource.EngineEstimate),
            ScoreSnapshot(moveNumber = 0, whiteScoreLead = -3.0, source = ScoreSnapshotSource.EngineEstimate),
        )

        val updated = ScoreTimeline.record(
            snapshots,
            ScoreSnapshot(moveNumber = 2, whiteScoreLead = 4.5, source = ScoreSnapshotSource.FinalScore),
        )

        assertEquals(listOf(0, 2), updated.map { it.moveNumber })
        assertEquals(4.5, updated.last().whiteScoreLead)
        assertEquals(ScoreSnapshotSource.FinalScore, updated.last().source)
    }

    @Test
    fun trimAfterDropsFutureSnapshots() {
        val snapshots = listOf(
            ScoreSnapshot(moveNumber = 0, whiteScoreLead = 0.0, source = ScoreSnapshotSource.EngineEstimate),
            ScoreSnapshot(moveNumber = 2, whiteScoreLead = 1.0, source = ScoreSnapshotSource.EngineEstimate),
            ScoreSnapshot(moveNumber = 4, whiteScoreLead = 2.0, source = ScoreSnapshotSource.EngineEstimate),
        )

        assertEquals(
            listOf(0, 2),
            ScoreTimeline.trimAfter(snapshots, moveNumber = 2).map { it.moveNumber },
        )
    }

    @Test
    fun finalScoreSnapshotUsesWhiteLeadSign() {
        val blackWin = FinalScoreResult(
            status = EngineStatus.ready("done"),
            rawScore = "B+9.5",
            winner = StoneColor.Black,
            margin = 9.5,
            summary = "done",
        )
        val whiteWin = blackWin.copy(rawScore = "W+2.5", winner = StoneColor.White, margin = 2.5)

        assertEquals(-9.5, ScoreTimeline.fromFinalScore(10, blackWin).whiteScoreLead)
        assertEquals(2.5, ScoreTimeline.fromFinalScore(10, whiteWin).whiteScoreLead)
    }
}
