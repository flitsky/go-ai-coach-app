package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.runtime.RuntimeLogContext

fun GameSessionControllerState.toRuntimeLogContext(
    engineName: String,
    engineDiagnostic: String,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    analysisCacheStats: String,
    turnTimeText: String,
): RuntimeLogContext =
    RuntimeLogContext(
        engineName = engineName,
        engineDiagnostic = engineDiagnostic,
        playerSetup = playerSetup,
        gameState = gameState,
        runtimeState = core.runtimeState,
        autoPlayDelaySetting = settings.autoPlayDelaySetting,
        searchTimeSettings = settings.searchTimeSettings,
        topMovesEnabled = settings.topMovesEnabled,
        isEngineReady = isEngineReady,
        isEngineBusy = isEngineBusy,
        isGameEnded = isGameEnded,
        isAutoAiTurnPending = isAutoAiTurnPending,
        shouldShowResumePrompt = shouldShowResumePrompt,
        analysisCacheStats = analysisCacheStats,
        moveAnalysisCoverage = core.analysisState.reviewAnalysis.coverageSummary(),
        scoreText = core.scoreState.scoreText,
        turnTimeText = turnTimeText,
    )
