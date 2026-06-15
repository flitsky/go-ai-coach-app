package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.StartupBenchmarkAction
import com.worksoc.goaicoach.application.engine.startupBenchmarkAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StartupBenchmarkDecisionTest {
    @Test
    fun skipsUntilStartupCompletedReadyAndSupported() {
        assertEquals(StartupBenchmarkAction.Skip, action(hasCompletedStartup = false))
        assertEquals(StartupBenchmarkAction.Skip, action(isEngineReady = false))
        assertEquals(StartupBenchmarkAction.Skip, action(supportsDeviceBenchmark = false))
        assertEquals(StartupBenchmarkAction.Skip, action(hasCheckedBenchmark = true))
    }

    @Test
    fun appliesStoredProfileWhenUsableProfileExists() {
        assertEquals(
            StartupBenchmarkAction.ApplyStoredProfile,
            action(hasUsableProfile = true),
        )
    }

    @Test
    fun runsBenchmarkWhenNoUsableProfile() {
        assertEquals(
            StartupBenchmarkAction.RunBenchmark,
            action(hasUsableProfile = false),
        )
    }

    @Test
    fun doesNotEvaluateProfileLookupWhenGatesFail() {
        var evaluated = false
        val result = startupBenchmarkAction(
            hasCompletedStartup = false,
            isEngineReady = true,
            supportsDeviceBenchmark = true,
            hasCheckedBenchmark = false,
            hasUsableProfile = { evaluated = true; true },
        )
        assertEquals(StartupBenchmarkAction.Skip, result)
        assertFalse("profile lookup must be short-circuited when skipping", evaluated)
    }

    private fun action(
        hasCompletedStartup: Boolean = true,
        isEngineReady: Boolean = true,
        supportsDeviceBenchmark: Boolean = true,
        hasCheckedBenchmark: Boolean = false,
        hasUsableProfile: Boolean = false,
    ): StartupBenchmarkAction =
        startupBenchmarkAction(
            hasCompletedStartup = hasCompletedStartup,
            isEngineReady = isEngineReady,
            supportsDeviceBenchmark = supportsDeviceBenchmark,
            hasCheckedBenchmark = hasCheckedBenchmark,
            hasUsableProfile = { hasUsableProfile },
        )
}
