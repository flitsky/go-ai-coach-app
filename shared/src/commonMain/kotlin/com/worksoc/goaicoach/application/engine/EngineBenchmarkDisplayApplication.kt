package com.worksoc.goaicoach.application.engine

data class EngineBenchmarkDisplayPlan(
    val engineMessage: String,
    val candidateText: String,
)

internal fun engineBenchmarkWaitingDisplayPlan(): EngineBenchmarkDisplayPlan =
    EngineBenchmarkDisplayPlan(
        engineMessage = "엔진 벤치마크 시작 전 안정화 대기 중입니다.",
        candidateText = "Engine benchmark waiting for startup settle delay.",
    )

internal fun engineBenchmarkRunningDisplayPlan(
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

/**
 * ⚠️ 저장 위치(파일 경로)는 싣지 않는다 — 어댑터 상수로 정해지는 매체 관리라 포트 위로 올리지
 * 않는다(refactor backlog #86). 이 메시지는 앱 어디에서도 렌더되지 않고 디버그 리포트의
 * `DisplayedTexts` 절(`engineMessage:` 칸)에만 실린다.
 */
internal fun engineBenchmarkCompletedDisplayPlan(
    profile: EngineBenchmarkProfile,
): EngineBenchmarkDisplayPlan =
    EngineBenchmarkDisplayPlan(
        engineMessage = "Engine benchmark saved.",
        candidateText = "Engine benchmark complete.\n${profile.toSummaryText()}",
    )

internal fun engineBenchmarkFailureDisplayPlan(error: Throwable): EngineBenchmarkDisplayPlan =
    EngineBenchmarkDisplayPlan(
        engineMessage = "Engine benchmark failed: ${error.message ?: "unknown error"}",
        candidateText = "Engine benchmark failed. The app will continue with built-in defaults.",
    )
