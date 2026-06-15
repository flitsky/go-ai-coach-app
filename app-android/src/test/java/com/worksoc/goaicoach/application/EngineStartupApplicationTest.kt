package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.*
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineStartupApplicationTest {
    @Test
    fun successPlanUsesEngineSnapshotWhenAvailable() {
        val engineSnapshot = ScoreSnapshot(
            moveNumber = 0,
            whiteScoreLead = 1.5,
            whiteWinRate = 0.55,
            source = ScoreSnapshotSource.EngineEstimate,
        )

        val plan = buildEngineStartupSuccessDisplayPlan(
            state = GameState.empty(),
            result = EngineStartupResult(
                message = "ready",
                scoreSnapshot = engineSnapshot,
            ),
        )

        assertTrue(plan.isEngineReady)
        assertEquals("ready", plan.engineMessage)
        assertEquals(listOf(engineSnapshot), plan.scoreSnapshots)
        assertNull(plan.candidateText)
    }

    @Test
    fun successPlanFallsBackToLocalSnapshot() {
        val plan = buildEngineStartupSuccessDisplayPlan(
            state = GameState.empty(),
            result = EngineStartupResult(
                message = "ready",
                scoreSnapshot = null,
            ),
        )

        assertTrue(plan.isEngineReady)
        assertEquals(ScoreSnapshotSource.LocalAreaEstimate, plan.scoreSnapshots.single().source)
    }

    @Test
    fun failurePlanKeepsExistingScoreTimelineAndShowsDiagnostic() {
        val plan = buildEngineStartupFailureDisplayPlan(
            errorMessage = "missing model",
            engineDiagnostic = "diagnostic",
        )

        assertFalse(plan.isEngineReady)
        assertEquals(emptyList<ScoreSnapshot>(), plan.scoreSnapshots)
        assertEquals("Engine initialization failed.\nmissing model", plan.engineMessage)
        assertEquals("2P test mode is still available.\ndiagnostic", plan.candidateText)
    }

    @Test
    fun startupWorkflowResultBuildsDisplayPlan() {
        val state = GameState.empty()

        val success = buildEngineStartupDisplayPlan(
            state = state,
            result = EngineStartupWorkflowResult.Success(
                EngineStartupResult(
                    message = "ready",
                    scoreSnapshot = null,
                ),
            ),
            engineDiagnostic = "diagnostic",
        )
        val failure = buildEngineStartupDisplayPlan(
            state = state,
            result = EngineStartupWorkflowResult.Failure(
                IllegalStateException("startup failed"),
            ),
            engineDiagnostic = "diagnostic",
        )

        assertTrue(success.isEngineReady)
        assertEquals("ready", success.engineMessage)
        assertFalse(failure.isEngineReady)
        assertEquals("Engine initialization failed.\nstartup failed", failure.engineMessage)
    }
}
