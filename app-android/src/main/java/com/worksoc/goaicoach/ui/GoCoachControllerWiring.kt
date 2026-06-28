package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.score.ScoreEstimateController
import com.worksoc.goaicoach.application.session.GameSessionDisplayStateApplier
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest

internal fun buildScoreEstimateController(
    engineClient: EngineSessionClient,
    diagnosticEventLog: DiagnosticEventLogPort,
    currentGameState: () -> GameState,
    currentScoreSnapshots: () -> List<ScoreSnapshot>,
    isEngineReady: () -> Boolean,
    isEngineBusy: () -> Boolean,
    currentMatchMode: () -> MatchMode,
    currentEngineProfile: () -> EngineProfile,
    currentSessionGeneration: () -> Long,
    launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    onEngineMessage: (String) -> Unit,
    displayStateApplier: GameSessionDisplayStateApplier,
    appendDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
): ScoreEstimateController =
    ScoreEstimateController(
        engineClient = engineClient,
        diagnosticEventLog = diagnosticEventLog,
        currentGameState = currentGameState,
        currentScoreSnapshots = currentScoreSnapshots,
        isEngineReady = isEngineReady,
        isEngineBusy = isEngineBusy,
        currentMatchMode = currentMatchMode,
        currentEngineProfile = currentEngineProfile,
        currentSessionGeneration = currentSessionGeneration,
        launchEngineOperation = launchEngineOperation,
        onEngineMessage = onEngineMessage,
        onScoreEstimateDisplayPlan = displayStateApplier::applyScoreEstimateDisplayPlan,
        onScoreEstimateFailureDisplayPlan = displayStateApplier::applyScoreEstimateFailureDisplayPlan,
        appendDiscardLog = appendDiscardLog,
    )
