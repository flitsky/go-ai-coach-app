package com.worksoc.goaicoach.application.analysis

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest

/**
 * Owns the position-analysis cache optimization UI flow: building the plan,
 * dismissing or accepting the prompt, and refreshing prompt visibility on state
 * changes.  State the screen also reads ([PositionAnalysisCacheOptimizationUiState])
 * lives in the caller and is reached through the accessors below.
 */
internal class PositionCacheOptimizationController(
    private val engineClient: EngineSessionClient,
    private val diagnosticEventLog: DiagnosticEventLogPort,
    private val currentGameState: () -> GameState,
    private val currentPlayerSetup: () -> PlayerSetup,
    private val currentSearchTimeSettings: () -> SearchTimeSettings,
    private val isEngineBusy: () -> Boolean,
    private val currentSessionGeneration: () -> Long,
    private val currentUiState: () -> PositionAnalysisCacheOptimizationUiState,
    private val onUiState: (PositionAnalysisCacheOptimizationUiState) -> Unit,
    private val onEngineMessage: (String) -> Unit,
    private val onAnalysisCandidateText: (String) -> Unit,
    private val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
) {
    private fun buildPlan() = buildPositionAnalysisCacheOptimizationPlan(
        finalState = currentGameState(),
        playerSetup = currentPlayerSetup(),
        searchTimeSettings = currentSearchTimeSettings(),
        qualityFor = { state, limit ->
            engineClient.positionAnalysisCacheQualityFor(
                state = state,
                limit = limit,
                searchMode = EngineSearchMode.JsonPositionAnalysis,
                nowMillis = System.currentTimeMillis(),
            )
        },
    )

    fun dismiss() {
        onUiState(currentUiState().dismiss(currentGameFingerprint = buildPlan().gameFingerprint))
    }

    fun accept() {
        runPositionAnalysisCacheOptimizationApplication(
            PositionAnalysisCacheOptimizationRunRequest(
                plan = buildPlan(),
                uiState = currentUiState(),
                isEngineBusy = isEngineBusy(),
                sessionGeneration = currentSessionGeneration(),
                engineClient = engineClient,
                diagnosticEventLog = diagnosticEventLog,
                currentUiStateProvider = currentUiState,
                applyUiState = onUiState,
                applyEngineMessage = onEngineMessage,
                applyCandidateText = onAnalysisCandidateText,
                launchEngineOperation = launchEngineOperation,
            ),
        )
    }

    fun refreshPromptIfNeeded(
        isGameEnded: Boolean,
        isEngineReady: Boolean,
        isEngineBusy: Boolean,
    ) {
        // This post-game experiment is disabled in the mobile flow. Avoid
        // reconstructing opening states on every screen update when no prompt
        // can be shown.
        if (!PostGamePositionAnalysisCacheOptimizationPromptEnabled) {
            onUiState(currentUiState().clearPrompt())
            return
        }
        onUiState(
            refreshPositionAnalysisCacheOptimizationPrompt(
                currentState = currentUiState(),
                isGameEnded = isGameEnded,
                isEngineReady = isEngineReady,
                isEngineBusy = isEngineBusy,
                plan = buildPlan(),
                isPromptEnabled = true,
            ),
        )
    }
}
