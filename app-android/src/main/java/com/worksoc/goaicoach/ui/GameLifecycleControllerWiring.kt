package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.savedgame.SavedGameRestorePlan
import com.worksoc.goaicoach.application.savedgame.SavedSessionController
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.application.session.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.startgame.GameSessionResetPlan
import com.worksoc.goaicoach.application.startgame.NewGameController
import com.worksoc.goaicoach.application.runtime.runtimeGameResetLog
import com.worksoc.goaicoach.application.topmoves.TopMovesController

/**
 * 대국 시작/재개 컨트롤러 그룹 — 둘 다 새 국면으로 리셋한 뒤 [topMovesController]로 후속
 * 분석을 트리거한다는 점에서 착수 흐름 그룹과 얽혀 있다.
 */
internal fun wireNewGameController(
    context: GoCoachAppWiringContext,
    topMovesController: TopMovesController,
): NewGameController =
    NewGameController(
        engineClient = context.engineClient,
        diagnosticEventLog = context.diagnosticEventLog,
        runtimeEventLog = context.runtimeEventLog,
        defaultPlayLevel = context.defaultPlayLevel,
        isEngineReady = { context.isEngineReady() },
        isEngineBusy = { context.isEngineBusy() },
        currentGameState = { context.gameState() },
        currentPlayerSetup = { context.playerSetup() },
        currentEngineProfile = { context.runtimeState().engineProfile },
        currentSearchTimeSettings = { context.sessionSnapshot().settings.searchTimeSettings },
        currentBoardSize = { context.settingsState().boardSize },
        currentHandicapCount = { context.settingsState().handicapCount },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        currentScoreState = { context.scoreState() },
        currentRuntimeLogContext = context::currentRuntimeLogContext,
        cancelStaleOperations = context.lifecycleController::evictAllOperations,
        launchEngineOperation = { operation, block -> context.lifecycleController.launchTracked(operation) { block() } },
        applyGameSessionResetPlan = { reset: GameSessionResetPlan ->
            context.clearUndoEngineInterventionQuietWindow()
            context.undoAnalysisRestoreCache.clear()
            context.setPositionCacheOptimizationState(context.positionCacheOptimizationState().clearPrompt())
            context.applyCoreSessionState(context.sessionSnapshot().core.applyGameSessionResetPlan(reset))
            context.setTurnTimeState(
                GameSessionTurnTimeState.reset(
                    state = reset.gameState,
                    nowMillis = System.currentTimeMillis(),
                )
            )
            context.runtimeEventLog.append(
                runtimeGameResetLog(
                    context = context.currentRuntimeLogContext(),
                    reset = reset,
                )
            )
        },
        applyRuntimePlayLevelSelection = { selection: RuntimePlayLevelSelection -> context.setRuntimeState(context.runtimeState().applySelection(selection)) },
        replaceScoreState = { state -> context.setScoreState(state) },
        requestFollowUpAnalysis = { state -> topMovesController.requestAnalysis(state, automatic = true) },
        onEngineMessage = { message -> context.setEngineMessage(message) },
    )

internal fun wireSavedSessionController(
    context: GoCoachAppWiringContext,
    topMovesController: TopMovesController,
): SavedSessionController =
    SavedSessionController(
        engineClient = context.engineClient,
        diagnosticEventLog = context.diagnosticEventLog,
        defaultPlayLevel = context.defaultPlayLevel,
        isEngineBusy = { context.isEngineBusy() },
        isEngineReady = { context.isEngineReady() },
        currentSearchTimeSettings = { context.sessionSnapshot().settings.searchTimeSettings },
        currentEngineProfile = { context.runtimeState().engineProfile },
        currentSessionGeneration = { context.runtimeState().sessionGeneration },
        timeoutPolicy = context::engineProfileTimeoutPolicy,
        currentGameState = { context.gameState() },
        onEngineMessage = { message -> context.setEngineMessage(message) },
        applySavedGameRestorePlan = { restore: SavedGameRestorePlan ->
            context.clearUndoEngineInterventionQuietWindow()
            context.undoAnalysisRestoreCache.clear()
            context.setPositionCacheOptimizationState(context.positionCacheOptimizationState().clearPrompt())
            context.setSettingsState(
                context.settingsState().applySavedGameRestore(
                    restoredSetup = restore.playerSetup,
                    restoredTopMovesEnabled = restore.topMovesEnabled,
                )
            )
            context.applyCoreSessionState(context.sessionSnapshot().core.applySavedGameRestorePlan(restore))
            context.setTurnTimeState(
                GameSessionTurnTimeState.reset(
                    state = restore.gameState,
                    nowMillis = System.currentTimeMillis(),
                )
            )
        },
        launchEngineOperation = { operation, block -> context.lifecycleController.launchTracked(operation) { block() } },
        applyScoreSyncCompletion = context.displayStateApplier::applyScoreSyncCompletion,
        requestFollowUpAnalysis = { state -> topMovesController.requestAnalysis(state, automatic = true) },
    )
