package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.autoai.AutoAiTurnUiState
import com.worksoc.goaicoach.application.engine.EngineBenchmarkStorePort
import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.preferences.InitialUserPreferencesPlan
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import com.worksoc.goaicoach.application.session.GameSessionAnalysisState
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.application.session.GameSessionMoveReviewState
import com.worksoc.goaicoach.application.session.GameSessionRuntimeState
import com.worksoc.goaicoach.application.session.GameSessionScoreState
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.application.session.buildGameSessionControllerState
import com.worksoc.goaicoach.application.session.toGameSessionSettingsState

internal fun buildInitialSessionState(
    initialPlan: InitialUserPreferencesPlan,
    engineDiagnostic: String,
    benchmarkStore: EngineBenchmarkStorePort,
): GameSessionControllerState = buildGameSessionControllerState(
    gameState = initialPlan.gameState,
    // 앱 시작은 대국 전 설정/미리보기 상태다. 사용자가 "새 게임"을 눌러야
    // 실제 대국과 자동 턴 처리가 시작된다.
    isGameEnded = true,
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
    engineMessage = "Ready to start a new game.",
    turnTimeState = GameSessionTurnTimeState.reset(
        state = initialPlan.gameState,
        nowMillis = System.currentTimeMillis(),
    ),
    settings = initialPlan.toGameSessionSettingsState(),
    benchmark = EngineBenchmarkUiState.initial(
        benchmarkText = benchmarkStore.loadText(),
        profile = benchmarkStore.load(),
    ),
    savedSession = SavedSessionUiState(),
    autoAiTurn = AutoAiTurnUiState(),
    positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
)
