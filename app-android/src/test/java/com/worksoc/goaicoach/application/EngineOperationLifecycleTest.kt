package com.worksoc.goaicoach.application

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineOperationLifecycleTest {
    @Test
    fun startedTransitionTracksOperationAndMarksEngineBusy() {
        val next = applyEngineOperationLifecycleTransition(
            state = EngineOperationLifecycleState(),
            transition = EngineOperationLifecycleTransition.Started("top_moves:g1"),
        )

        assertTrue(
            next.isEngineBusy,
        )
        assertTrue("top_moves:g1" in next.activeOperationIds)
    }

    @Test
    fun completedTransitionOnlyClearsMatchingOperation() {
        val state = EngineOperationLifecycleState(
            activeOperationIds = setOf("top_moves:g1", "score:g1"),
        )

        val next = applyEngineOperationLifecycleTransition(
            state = state,
            transition = EngineOperationLifecycleTransition.Completed("top_moves:g1"),
        )

        assertTrue(next.isEngineBusy)
        assertFalse("top_moves:g1" in next.activeOperationIds)
        assertTrue("score:g1" in next.activeOperationIds)
    }

    @Test
    fun resetClearsAllOperations() {
        val state = EngineOperationLifecycleState(activeOperationIds = setOf("a", "b"))

        val next = applyEngineOperationLifecycleTransition(
            state = state,
            transition = EngineOperationLifecycleTransition.Reset,
        )

        assertFalse(next.isEngineBusy)
        assertTrue(next.activeOperationIds.isEmpty())
    }
}
