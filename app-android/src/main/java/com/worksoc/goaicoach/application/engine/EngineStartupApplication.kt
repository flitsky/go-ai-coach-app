package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.EngineStartupResult
import com.worksoc.goaicoach.application.localScoreSnapshot
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

internal fun buildEngineStartupDisplayPlan(
    state: GameState,
    result: EngineStartupWorkflowResult,
    engineDiagnostic: String,
): EngineStartupDisplayPlan =
    when (result) {
        is EngineStartupWorkflowResult.Success ->
            buildEngineStartupSuccessDisplayPlan(
                state = state,
                result = result.result,
            )

        is EngineStartupWorkflowResult.Failure ->
            buildEngineStartupFailureDisplayPlan(
                errorMessage = result.error.message,
                engineDiagnostic = engineDiagnostic,
            )
    }
