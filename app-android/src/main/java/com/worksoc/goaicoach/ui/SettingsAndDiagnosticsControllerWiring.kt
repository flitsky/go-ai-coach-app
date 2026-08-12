package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.debugreport.DebugReportController
import com.worksoc.goaicoach.application.engine.EngineBenchmarkController
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSettingsController
import com.worksoc.goaicoach.application.session.RuntimePlayLevelSelection
import com.worksoc.goaicoach.application.undo.UndoController
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.SearchTimeSettings

internal fun wireSettingsController(
    context: GoCoachAppWiringContext,
    undoController: UndoController,
): GameSettingsController =
    GameSettingsController(
        currentGameState = { context.gameState() },
        currentPlayerSetup = { context.playerSetup() },
        currentEngineProfile = { context.runtimeState().engineProfile },
        currentSearchTimeSettings = { context.sessionSnapshot().settings.searchTimeSettings },
        currentAnalysisState = { context.analysisState() },
        currentAutoPlayDelaySetting = { context.sessionSnapshot().settings.autoPlayDelaySetting },
        currentSettingsState = { context.settingsState() },
        isGameEnded = { context.isGameEnded() },
        defaultPlayLevel = context.defaultPlayLevel,
        isEngineBusy = { context.isEngineBusy() },
        runtimeEventLog = context.runtimeEventLog,
        currentRuntimeLogContext = context::currentRuntimeLogContext,
        onEngineMessage = { message -> context.setEngineMessage(message) },
        applyPlayerSetup = { setup: PlayerSetup -> context.setSettingsState(context.settingsState().applyPlayerSetup(setup)) },
        applyCoreSessionState = context::applyCoreSessionState,
        currentCoreSessionState = { context.sessionSnapshot().core },
        applyRuntimePlayLevelSelection = { selection: RuntimePlayLevelSelection -> context.setRuntimeState(context.runtimeState().applySelection(selection)) },
        applyAnalysisState = { analysis: GameSessionAnalysisState -> context.setAnalysisState(analysis) },
        applySettingsAutoPlayDelay = { setting: AutoPlayDelaySetting -> context.setSettingsState(context.settingsState().applyAutoPlayDelay(setting)) },
        applySettingsSearchTimeSettings = { settings: SearchTimeSettings -> context.setSettingsState(context.settingsState().applySearchTimeSettings(settings)) },
        applySettingsBoardSize = { size: BoardSize -> context.setSettingsState(context.settingsState().applyBoardSize(size)) },
        applySettingsHandicapCount = { count: Int -> context.setSettingsState(context.settingsState().applyHandicap(count)) },
        applySettingsKomi = { komi: Double -> context.setSettingsState(context.settingsState().applyKomi(komi)) },
        clearUndoEngineInterventionQuietWindow = undoController::clearQuietWindow,
    )

internal fun wireDebugReportController(context: GoCoachAppWiringContext): DebugReportController =
    DebugReportController(
        engineName = context.engineName(),
        engineDiagnostic = context.engineDiagnostic(),
        runtimeEventLog = context.runtimeEventLog,
        diagnosticEventLog = context.diagnosticEventLog,
        clipboard = context.clipboardPort,
        mirror = context.debugReportMirror,
        userNotice = context.userNoticePort,
        currentControllerState = { context.sessionSnapshot() },
        isEngineReady = { context.isEngineReady() },
        isEngineBusy = { context.isEngineBusy() },
        analysisCacheStatsText = { "${context.analysisCache.statsText()}, ${context.undoAnalysisRestoreCache.statsText()}" },
        positionAnalysisCacheStatsText = context.engineClient::positionAnalysisCacheStatsText,
        turnTimeText = { context.turnTimeState().summaryText() },
        turnTimeDebugText = { nowMillis -> context.turnTimeState().debugText(nowMillis) },
        onEngineMessage = { message -> context.setEngineMessage(message) },
        currentSavedSessionJson = { context.sessionStore.readRawJson() },
    )

internal fun wireBenchmarkController(context: GoCoachAppWiringContext): EngineBenchmarkController =
    EngineBenchmarkController(
        scope = context.scope,
        engineClient = context.engineClient,
        store = context.benchmarkStore,
        diagnosticEventLog = context.diagnosticEventLog,
        lifecycleCallbacks = { context.lifecycleController.callbacks() },
        currentState = { context.gameState() },
        sessionGeneration = { context.runtimeState().sessionGeneration },
        isEngineReady = { context.isEngineReady() },
        isEngineBusy = { context.isEngineBusy() },
        currentBenchmarkUiState = { context.benchmarkUiState() },
        onBenchmarkUiState = { state -> context.setBenchmarkUiState(state) },
        onEngineMessage = { message -> context.setEngineMessage(message) },
        onDisplayPlan = { plan -> context.displayStateApplier.applyEngineBenchmarkDisplayPlan(plan) },
    )
