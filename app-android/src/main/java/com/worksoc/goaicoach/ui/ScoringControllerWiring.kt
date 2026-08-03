package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.analysis.PositionCacheOptimizationController
import com.worksoc.goaicoach.application.score.ScoreEstimateController
import com.worksoc.goaicoach.application.score.ScoringRuleChangePlan
import com.worksoc.goaicoach.application.score.ScoringRuleController
import com.worksoc.goaicoach.application.topmoves.TopMovesController

internal fun wireCacheOptController(context: GoCoachAppWiringContext): PositionCacheOptimizationController =
    PositionCacheOptimizationController(
        engineClient = context.engineClient,
        diagnosticEventLog = context.diagnosticEventLog,
        currentGameState = { context.gameState() },
        currentPlayerSetup = { context.playerSetup() },
        currentSearchTimeSettings = { context.sessionSnapshot().settings.searchTimeSettings },
        isEngineBusy = { context.isEngineBusy() },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        currentUiState = { context.positionCacheOptimizationState() },
        onUiState = { state -> context.setPositionCacheOptimizationState(state) },
        onEngineMessage = { message -> context.setEngineMessage(message) },
        onAnalysisCandidateText = { message -> context.setAnalysisState(context.analysisState().copy(candidateText = message)) },
        launchEngineOperation = { operation, block -> context.lifecycleController.launchTracked(operation) { block() } },
    )

internal fun wireScoreEstimateController(context: GoCoachAppWiringContext): ScoreEstimateController =
    ScoreEstimateController(
        engineClient = context.engineClient,
        diagnosticEventLog = context.diagnosticEventLog,
        currentGameState = { context.gameState() },
        currentScoreSnapshots = { context.scoreState().scoreSnapshots },
        isEngineReady = { context.isEngineReady() },
        isEngineBusy = { context.isEngineBusy() },
        currentMatchMode = { context.matchMode() },
        currentEngineProfile = { context.runtimeState().engineProfile },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        launchEngineOperation = { operation, block -> context.lifecycleController.launchTracked(operation) { block() } },
        onEngineMessage = { message -> context.setEngineMessage(message) },
        onScoreEstimateDisplayPlan = context.displayStateApplier::applyScoreEstimateDisplayPlan,
        onScoreEstimateFailureDisplayPlan = context.displayStateApplier::applyScoreEstimateFailureDisplayPlan,
        appendDiscardLog = context.lifecycleController::appendDiscardLog,
    )

internal fun wireScoringRuleController(
    context: GoCoachAppWiringContext,
    topMovesController: TopMovesController,
): ScoringRuleController =
    ScoringRuleController(
        engineClient = context.engineClient,
        diagnosticEventLog = context.diagnosticEventLog,
        currentGameState = { context.gameState() },
        currentMatchMode = { context.matchMode() },
        isEngineReady = { context.isEngineReady() },
        isEngineBusy = { context.isEngineBusy() },
        currentScoreSnapshots = { context.scoreState().scoreSnapshots },
        currentEngineProfile = { context.runtimeState().engineProfile },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        timeoutPolicy = context::engineProfileTimeoutPolicy,
        onEngineMessage = { message -> context.setEngineMessage(message) },
        applyScoringRuleChangePlan = { ruleChange: ScoringRuleChangePlan ->
            context.applyCoreSessionState(context.sessionSnapshot().core.applyScoringRuleChangePlan(ruleChange))
        },
        applyScoreSyncCompletionApplyPlan = context.displayStateApplier::applyScoreSyncCompletion,
        requestFollowUpAnalysis = { state -> topMovesController.requestAnalysis(state, automatic = true) },
        launchEngineOperation = { operation, block -> context.lifecycleController.launchTracked(operation) { block() } },
        appendDiscardLog = context.lifecycleController::appendDiscardLog,
    )
