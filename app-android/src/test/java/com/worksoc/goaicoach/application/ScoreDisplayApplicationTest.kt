package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.DeadStoneCleanupResult
import com.worksoc.goaicoach.shared.EndgameScoreSource
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreDisplayApplicationTest {
    @Test
    fun engineEstimateDisplayPlanRecordsScoreSnapshotAndMessage() {
        val state = GameState.empty()
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = -3.5,
            whiteWinRate = 0.25,
            summary = "KataGo estimate",
        )

        val plan = buildEngineEstimateDisplayPlan(
            state = state,
            estimate = estimate,
            previousSnapshots = emptyList(),
        )

        assertEquals(estimate, plan.scoreEstimate)
        assertEquals("estimated", plan.engineMessage)
        assertTrue(plan.scoreText.contains("Black win: 75%"))
        assertEquals(0, plan.scoreSnapshots.single().moveNumber)
        assertEquals(ScoreSnapshotSource.EngineEstimate, plan.scoreSnapshots.single().source)
    }

    @Test
    fun localScoreEstimateDisplayPlanClearsEngineEstimateAndRecordsLocalSnapshot() {
        val state = GameState.empty()

        val plan = buildLocalScoreEstimateDisplayPlan(
            state = state,
            previousSnapshots = listOf(ScoreSnapshot(moveNumber = 0, source = ScoreSnapshotSource.EngineEstimate)),
            engineMessage = "local refreshed",
        )

        assertNull(plan.scoreEstimate)
        assertEquals("local refreshed", plan.engineMessage)
        assertTrue(plan.scoreText.contains("Final:"))
        assertEquals(ScoreSnapshotSource.LocalAreaEstimate, plan.scoreSnapshots.single().source)
    }

    @Test
    fun resolvedEndgameDisplayPlanBuildsFinalScoreLogAndCandidateText() {
        val state = GameState.empty()
        val finalScore = FinalScoreResult(
            status = EngineStatus.ready("final complete"),
            rawScore = "B+0.5",
            winner = StoneColor.Black,
            margin = 0.5,
            summary = "Final score",
        )
        val resolution = AiEndgameResolution(
            cleanup = DeadStoneCleanupResult(state = state, removedStones = emptyList()),
            finalScore = finalScore,
            scoreSource = EndgameScoreSource.CleanedLocalArea,
            localFinalScore = finalScore,
            deadStonesResult = null,
            deadStonesError = null,
            locallyInferredDeadStones = emptyList(),
            engineScoreEstimate = null,
            engineScoreEstimateError = null,
            engineFinalScore = null,
            engineFinalScoreError = null,
            prePassCandidates = emptyList(),
        )

        val plan = buildResolvedEndgameDisplayPlan(
            source = "test-endgame",
            originalState = state,
            resolution = resolution,
            previousSnapshots = emptyList(),
            engineMessagePrefix = "prefix",
        )

        assertEquals(state, plan.gameState)
        assertNull(plan.scoreEstimate)
        assertTrue(plan.scoreText.contains("Final: B+0.5"))
        assertTrue(plan.endgameLog.contains("source=test-endgame"))
        assertTrue(plan.engineMessage.startsWith("prefix\n"))
        assertTrue(plan.candidateText.contains("Game ended after pass/pass"))
        assertEquals(ScoreSnapshotSource.FinalScore, plan.scoreSnapshots.single().source)
    }
}
