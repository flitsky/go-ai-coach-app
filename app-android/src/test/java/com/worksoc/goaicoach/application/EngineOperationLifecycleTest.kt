package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.operation.*
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineOperationLifecycleTest {

    private fun createRequest(operationId: String, kind: EngineOperationKind = EngineOperationKind.TopMoves): EngineOperationRequest {
        return EngineOperationRequest(
            operationId = operationId,
            kind = kind,
            sessionGeneration = 1L,
            boardFingerprint = "dummy",
            moveCount = 0,
            timeoutPolicy = EngineTimeoutPolicy(),
            fallbackPolicy = EngineFallbackPolicy.None,
            backendId = "local-engine"
        )
    }

    @Test
    fun startedTransitionTracksOperationAndMarksEngineBusy() {
        val request = createRequest("top_moves:g1")
        val next = applyEngineOperationLifecycleTransition(
            state = EngineOperationLifecycleState(),
            transition = EngineOperationLifecycleTransition.Started(request),
        )

        assertTrue(
            next.isEngineBusy,
        )
        assertTrue("top_moves:g1" in next.activeOperations.keys)
    }

    @Test
    fun completedTransitionOnlyClearsMatchingOperation() {
        val req1 = createRequest("top_moves:g1")
        val req2 = createRequest("score:g1", EngineOperationKind.ScoreEstimate)
        val state = EngineOperationLifecycleState(
            activeOperations = mapOf("top_moves:g1" to req1, "score:g1" to req2),
        )

        val next = applyEngineOperationLifecycleTransition(
            state = state,
            transition = EngineOperationLifecycleTransition.Completed("top_moves:g1"),
        )

        assertTrue(next.isEngineBusy)
        assertFalse("top_moves:g1" in next.activeOperations.keys)
        assertTrue("score:g1" in next.activeOperations.keys)
    }

    @Test
    fun resetClearsAllOperations() {
        val req1 = createRequest("a")
        val req2 = createRequest("b")
        val state = EngineOperationLifecycleState(
            activeOperations = mapOf("a" to req1, "b" to req2)
        )

        val next = applyEngineOperationLifecycleTransition(
            state = state,
            transition = EngineOperationLifecycleTransition.Reset,
        )

        assertFalse(next.isEngineBusy)
        assertTrue(next.activeOperations.isEmpty())
    }
}
