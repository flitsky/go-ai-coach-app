package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot

internal data class EngineStartupDisplayPlan(
    val isEngineReady: Boolean,
    val scoreSnapshots: List<ScoreSnapshot>,
    val engineMessage: String,
    val candidateText: String?,
)

internal fun buildEngineStartupSuccessDisplayPlan(
    state: GameState,
    result: EngineStartupResult,
): EngineStartupDisplayPlan =
    EngineStartupDisplayPlan(
        isEngineReady = true,
        scoreSnapshots = listOf(result.scoreSnapshot ?: localScoreSnapshot(state)),
        engineMessage = result.message,
        candidateText = null,
    )

internal fun buildEngineStartupFailureDisplayPlan(
    errorMessage: String?,
    engineDiagnostic: String,
): EngineStartupDisplayPlan =
    EngineStartupDisplayPlan(
        isEngineReady = false,
        scoreSnapshots = emptyList(),
        engineMessage = "Engine initialization failed.\n${errorMessage ?: "Unknown error"}",
        candidateText = "2P test mode is still available.\n$engineDiagnostic",
    )
