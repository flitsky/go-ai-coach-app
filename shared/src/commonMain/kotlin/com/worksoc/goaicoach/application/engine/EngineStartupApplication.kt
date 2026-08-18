package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.engine.EngineStartupResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot

data class EngineStartupDisplayPlan(
    val isEngineReady: Boolean,
    val scoreSnapshots: List<ScoreSnapshot>,
    val engineMessage: String,
    val candidateText: String?,
)

fun buildEngineStartupSuccessDisplayPlan(
    state: GameState,
    result: EngineStartupResult,
): EngineStartupDisplayPlan =
    EngineStartupDisplayPlan(
        isEngineReady = true,
        // No moves have been played on `state` yet (handicap stones only, if any), so there is
        // nothing meaningful to show if the engine didn't return an estimate - falling back to a
        // local flood-fill territory estimate here would misreport the whole empty board as one
        // side's territory (see the B+157.5 misdisplay). This is the engine BOOTSTRAP step, not
        // the "new game" reset - it fires on every app start / engine (re)init, well before the
        // player ever taps "새 대국", which is why it was easy to miss as the actual source.
        scoreSnapshots = result.scoreSnapshot?.let { listOf(it) } ?: emptyList(),
        engineMessage = result.message,
        candidateText = null,
    )

fun buildEngineStartupFailureDisplayPlan(
    errorMessage: String?,
    engineDiagnostic: String,
): EngineStartupDisplayPlan =
    EngineStartupDisplayPlan(
        isEngineReady = false,
        scoreSnapshots = emptyList(),
        engineMessage = "Engine initialization failed.\n${errorMessage ?: "Unknown error"}",
        candidateText = "2P test mode is still available.\n$engineDiagnostic",
    )

fun buildEngineStartupDisplayPlan(
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
