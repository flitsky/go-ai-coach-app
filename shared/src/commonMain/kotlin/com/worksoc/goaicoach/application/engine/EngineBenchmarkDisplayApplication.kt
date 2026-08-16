package com.worksoc.goaicoach.application.engine

data class EngineBenchmarkDisplayPlan(
    val engineMessage: String,
    val candidateText: String,
)

fun engineBenchmarkWaitingDisplayPlan(): EngineBenchmarkDisplayPlan =
    EngineBenchmarkDisplayPlan(
        engineMessage = "엔진 벤치마크 시작 전 안정화 대기 중입니다.",
        candidateText = "Engine benchmark waiting for startup settle delay.",
    )

fun engineBenchmarkRunningDisplayPlan(
    samplesPerVisit: Int = EngineBenchmarkDefaultSamplesPerVisit,
): EngineBenchmarkDisplayPlan =
    EngineBenchmarkDisplayPlan(
        engineMessage = "최초 실행환경에서 최적 플레이를 위해 벤치마크 테스트가 진행중입니다.",
        candidateText = "Engine benchmark running: B16/B32/B64, $samplesPerVisit samples each.",
    )

fun EngineBenchmarkProgress.toEngineBenchmarkDisplayPlan(): EngineBenchmarkDisplayPlan =
    EngineBenchmarkDisplayPlan(
        engineMessage = stageText,
        candidateText = "Engine benchmark running: $progressText, $sampleText.",
    )

fun engineBenchmarkCompletedDisplayPlan(
    profile: EngineBenchmarkProfile,
    storePath: String,
): EngineBenchmarkDisplayPlan =
    EngineBenchmarkDisplayPlan(
        engineMessage = "Engine benchmark saved to $storePath.",
        candidateText = "Engine benchmark complete.\n${profile.toSummaryText()}",
    )

fun engineBenchmarkFailureDisplayPlan(error: Throwable): EngineBenchmarkDisplayPlan =
    EngineBenchmarkDisplayPlan(
        engineMessage = "Engine benchmark failed: ${error.message ?: "unknown error"}",
        candidateText = "Engine benchmark failed. The app will continue with built-in defaults.",
    )
