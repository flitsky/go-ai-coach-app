package com.worksoc.goaicoach.application.engine

/** What the startup flow should do about an automatic device benchmark. */
internal enum class StartupBenchmarkAction {
    Skip,
    ApplyStoredProfile,
    RunBenchmark,
}

/**
 * Pure decision for the startup device-benchmark check.
 *
 * [hasUsableProfile] is evaluated lazily so the (potentially I/O-backed) store
 * lookup only runs once the cheap gates have passed, matching the original
 * short-circuit behavior.
 */
internal fun startupBenchmarkAction(
    hasCompletedStartup: Boolean,
    isEngineReady: Boolean,
    supportsDeviceBenchmark: Boolean,
    hasCheckedBenchmark: Boolean,
    hasUsableProfile: () -> Boolean,
): StartupBenchmarkAction =
    when {
        !hasCompletedStartup || !isEngineReady || !supportsDeviceBenchmark || hasCheckedBenchmark ->
            StartupBenchmarkAction.Skip
        hasUsableProfile() -> StartupBenchmarkAction.ApplyStoredProfile
        else -> StartupBenchmarkAction.RunBenchmark
    }
