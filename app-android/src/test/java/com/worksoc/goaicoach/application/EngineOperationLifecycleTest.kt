package com.worksoc.goaicoach.application

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineOperationLifecycleTest {
    @Test
    fun startedTransitionMarksEngineBusy() {
        assertTrue(
            applyEngineOperationLifecycleTransition(
                isEngineBusy = false,
                transition = EngineOperationLifecycleTransition.Started,
            ),
        )
    }

    @Test
    fun completedTransitionMarksEngineIdle() {
        assertFalse(
            applyEngineOperationLifecycleTransition(
                isEngineBusy = true,
                transition = EngineOperationLifecycleTransition.Completed,
            ),
        )
    }
}
