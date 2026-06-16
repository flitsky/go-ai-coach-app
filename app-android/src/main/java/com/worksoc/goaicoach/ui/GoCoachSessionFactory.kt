package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.autoai.AutoAiTurnUiState
import com.worksoc.goaicoach.application.engine.EngineBenchmarkStorePort
import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.preferences.InitialUserPreferencesPlan
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import com.worksoc.goaicoach.application.session.*

internal fun buildInitialSessionState(
    initialPlan: InitialUserPreferencesPlan,
    engineDiagnostic: String,
    benchmarkStore: EngineBenchmarkStorePort,
): GameSessionControllerState = buildGameSessionControllerState(
    gameState = initialPlan.gameState,
    isGameEnded = false,
    analysisState = GameSessionAnalysisState.empty(
        state = initialPlan.gameState,
        candidateText = engineDiagnostic,
    ),
    scoreState = GameSessionScoreState.reset(
        scoreText = "No score estimate yet.",
        scoreSnapshots = listOf(localScoreSnapshot(initialPlan.gameState)),
        endgameLog = "No endgame result recorded.",
    ),
    runtimeState = GameSessionRuntimeState(
        playLevel = initialPlan.runtime.playLevel,
        engineProfile = initialPlan.runtime.engineProfile,
        analysisPreset = initialPlan.runtime.analysisPreset,
    ),
    moveReviewState = GameSessionMoveReviewState.reset(
        moveReviewText = "No move review yet.",
        lastMoveText = "None",
    ),
    engineMessage = "Engine not initialized.",
    settings = initialPlan.toGameSessionSettingsState(),
    benchmark = EngineBenchmarkUiState.initial(
        benchmarkText = benchmarkStore.loadText(),
        profile = benchmarkStore.load(),
    ),
    savedSession = SavedSessionUiState(),
    autoAiTurn = AutoAiTurnUiState(),
    positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
)
