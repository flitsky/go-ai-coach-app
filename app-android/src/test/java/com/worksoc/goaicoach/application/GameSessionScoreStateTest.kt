package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.score.*

import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameSessionScoreStateTest {
    @Test
    fun resetClearsEstimateAndUsesProvidedScoreDefaults() {
        val snapshot = ScoreSnapshot(moveNumber = 0, source = ScoreSnapshotSource.LocalAreaEstimate)

        val state = GameSessionScoreState.reset(
            scoreText = "No score estimate yet.",
            scoreSnapshots = listOf(snapshot),
            endgameLog = "No endgame result recorded.",
        )

        assertEquals("No score estimate yet.", state.scoreText)
        assertNull(state.scoreEstimate)
        assertEquals(listOf(snapshot), state.scoreSnapshots)
        assertEquals("No endgame result recorded.", state.endgameLog)
    }

    @Test
    fun scoreEstimateDisplayPlanUpdatesOnlyScoreFields() {
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = -3.5,
            whiteWinRate = 0.25,
            summary = "estimate",
        )
        val snapshot = ScoreSnapshot(moveNumber = 3, source = ScoreSnapshotSource.EngineEstimate)
        val original = GameSessionScoreState.reset(
            scoreText = "old",
            scoreSnapshots = emptyList(),
            endgameLog = "kept log",
        )

        val next = original.applyScoreEstimateDisplayPlan(
            ScoreEstimateDisplayPlan(
                scoreText = "B+3.5",
                scoreEstimate = estimate,
                scoreSnapshots = listOf(snapshot),
                engineMessage = "ignored by score state",
            ),
        )

        assertEquals("B+3.5", next.scoreText)
        assertEquals(estimate, next.scoreEstimate)
        assertEquals(listOf(snapshot), next.scoreSnapshots)
        assertEquals("kept log", next.endgameLog)
    }

    @Test
    fun scoreEstimateFailureDisplayPlanClearsOnlyCurrentEstimate() {
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = 1.0,
            whiteWinRate = 0.55,
            summary = "estimate",
        )
        val snapshot = ScoreSnapshot(moveNumber = 3, source = ScoreSnapshotSource.EngineEstimate)
        val original = GameSessionScoreState(
            scoreText = "old score text",
            scoreEstimate = estimate,
            scoreSnapshots = listOf(snapshot),
            endgameLog = "kept log",
        )

        val next = original.applyScoreEstimateFailureDisplayPlan(
            ScoreEstimateFailureDisplayPlan(engineMessage = "failed"),
        )

        assertEquals("old score text", next.scoreText)
        assertNull(next.scoreEstimate)
        assertEquals(listOf(snapshot), next.scoreSnapshots)
        assertEquals("kept log", next.endgameLog)
    }

    @Test
    fun finalScoreDisplayPlanUpdatesScoreAndEndgameLog() {
        val snapshot = ScoreSnapshot(moveNumber = 10, source = ScoreSnapshotSource.FinalScore)
        val original = GameSessionScoreState.reset(
            scoreText = "old",
            scoreSnapshots = emptyList(),
            endgameLog = "old log",
        )

        val next = original.applyFinalScoreDisplayPlan(
            FinalScoreDisplayPlan(
                gameState = GameState.empty(),
                scoreText = "Final: B+0.5",
                scoreEstimate = null,
                scoreSnapshots = listOf(snapshot),
                endgameLog = "final log",
                engineMessage = "ignored by score state",
                candidateText = "ignored by score state",
            ),
        )

        assertEquals("Final: B+0.5", next.scoreText)
        assertNull(next.scoreEstimate)
        assertEquals(listOf(snapshot), next.scoreSnapshots)
        assertEquals("final log", next.endgameLog)
    }

    @Test
    fun endgameFailureDisplayPlanOnlyUpdatesEndgameLog() {
        val snapshot = ScoreSnapshot(moveNumber = 4, source = ScoreSnapshotSource.EngineEstimate)
        val original = GameSessionScoreState(
            scoreText = "Score estimate not current.",
            scoreEstimate = null,
            scoreSnapshots = listOf(snapshot),
            endgameLog = "old log",
        )

        val next = original.applyEndgameFailureDisplayPlan(
            EndgameFailureDisplayPlan(
                endgameLog = "failure log",
                engineMessage = "ignored by score state",
                candidateText = "ignored by score state",
            ),
        )

        assertEquals("Score estimate not current.", next.scoreText)
        assertNull(next.scoreEstimate)
        assertEquals(listOf(snapshot), next.scoreSnapshots)
        assertEquals("failure log", next.endgameLog)
    }
}
