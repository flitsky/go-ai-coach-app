package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.MoveReviewMarker
import com.worksoc.goaicoach.application.PositionAnalysisCacheOptimizationPrompt
import com.worksoc.goaicoach.application.decidePromptVisibility
import com.worksoc.goaicoach.application.session.GameSessionControllerState
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchSeatSnapshot
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.turnStatusText
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor

internal data class GameScreenState(
    val gameState: GameState,
    val matchMode: MatchMode,
    val playerSetup: PlayerSetup,
    val playerSetupUi: PlayerSetupUiState,
    val autoPlayDelaySetting: AutoPlayDelaySetting,
    val searchTimeSettings: SearchTimeSettings,
    val searchTimeBenchmarkAverages: Map<Int, Double>,
    val playLevel: PlayLevelSetting,
    val matchSeats: MatchSeatSnapshot,
    val uxOptions: KaTrainUxOptions,
    val engine: EngineUiState,
    val analysis: AnalysisUiState,
    val score: ScoreUiState,
    val turnStatusText: String,
    val turnTimeText: String,
    val actionButtons: List<GameActionButtonState>,
    val resumePrompt: ResumePromptState?,
    val cacheOptimizationPrompt: PositionAnalysisCacheOptimizationPrompt?,
    val isGameEnded: Boolean,
    val endgameLog: String,
) {
    val nextPlayer: StoneColor
        get() = gameState.nextPlayer
}

internal data class GameScreenStateInput(
    val gameState: GameState,
    val matchMode: MatchMode,
    val playerSetup: PlayerSetup,
    val autoPlayDelaySetting: AutoPlayDelaySetting,
    val searchTimeSettings: SearchTimeSettings,
    val searchTimeBenchmarkAverages: Map<Int, Double>,
    val playLevel: PlayLevelSetting,
    val matchSeats: MatchSeatSnapshot,
    val uxOptions: KaTrainUxOptions,
    val engineName: String,
    val engineDiagnostic: String,
    val engineProfile: EngineProfile,
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    val engineMessage: String,
    val analysisPreset: AnalysisPreset,
    val analysisCacheStats: String,
    val topMovesEnabled: Boolean,
    val candidateMoves: List<CandidateMove>,
    val candidateText: String,
    val reviewAnalysis: MoveAnalysisSnapshot,
    val reviewCandidateMoves: List<CandidateMove>,
    val moveReviews: List<MoveReviewMarker>,
    val moveReviewText: String,
    val lastMoveText: String,
    val scoreText: String,
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
    val isScoreGraphExpanded: Boolean,
    val turnTimeText: String,
    val pendingSavedSession: SavedGameSnapshot?,
    val shouldShowResumePrompt: Boolean,
    val cacheOptimizationPrompt: PositionAnalysisCacheOptimizationPrompt?,
    val hasCompletedEngineStartup: Boolean,
    val isGameEnded: Boolean,
    val endgameLog: String,
)

internal fun buildGameScreenStateInput(
    controller: GameSessionControllerState,
    uxOptions: KaTrainUxOptions,
    engineName: String,
    engineDiagnostic: String,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    analysisCacheStats: String,
    isScoreGraphExpanded: Boolean,
    turnTimeText: String,
    hasCompletedEngineStartup: Boolean,
): GameScreenStateInput =
    GameScreenStateInput(
        gameState = controller.gameState,
        matchMode = controller.matchMode,
        playerSetup = controller.playerSetup,
        autoPlayDelaySetting = controller.settings.autoPlayDelaySetting,
        searchTimeSettings = controller.settings.searchTimeSettings,
        searchTimeBenchmarkAverages = controller.benchmark.searchTimeBenchmarkAverages,
        playLevel = controller.core.runtimeState.playLevel,
        matchSeats = controller.playerSetup.seatSnapshot(
            nextPlayer = controller.gameState.nextPlayer,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
        ),
        uxOptions = uxOptions,
        engineName = engineName,
        engineDiagnostic = engineDiagnostic,
        engineProfile = controller.core.runtimeState.engineProfile,
        isEngineReady = isEngineReady,
        isEngineBusy = isEngineBusy,
        engineMessage = controller.engineMessage,
        analysisPreset = controller.core.runtimeState.analysisPreset,
        analysisCacheStats = analysisCacheStats,
        topMovesEnabled = controller.settings.topMovesEnabled,
        candidateMoves = controller.core.analysisState.candidateMoves,
        candidateText = controller.core.analysisState.candidateText,
        reviewAnalysis = controller.core.analysisState.reviewAnalysis,
        reviewCandidateMoves = controller.core.analysisState.reviewCandidateMoves,
        moveReviews = controller.core.moveReviewState.moveReviews,
        moveReviewText = controller.core.moveReviewState.moveReviewText,
        lastMoveText = controller.core.moveReviewState.lastMoveText,
        scoreText = controller.core.scoreState.scoreText,
        scoreEstimate = controller.core.scoreState.scoreEstimate,
        scoreSnapshots = controller.core.scoreState.scoreSnapshots,
        isScoreGraphExpanded = isScoreGraphExpanded,
        turnTimeText = turnTimeText,
        pendingSavedSession = controller.savedSession.pendingSavedSession,
        shouldShowResumePrompt = controller.savedSession.shouldShowResumePrompt,
        cacheOptimizationPrompt = controller.positionCacheOptimization.prompt,
        hasCompletedEngineStartup = hasCompletedEngineStartup,
        isGameEnded = controller.isGameEnded,
        endgameLog = controller.core.scoreState.endgameLog,
    )

internal fun buildGameScreenState(input: GameScreenStateInput): GameScreenState {
    val promptVisibility = decidePromptVisibility(
        hasCompletedEngineStartup = input.hasCompletedEngineStartup,
        isEngineBusy = input.isEngineBusy,
        hasPendingSavedSession = input.pendingSavedSession != null,
        shouldShowResumePrompt = input.shouldShowResumePrompt,
        hasCacheOptimizationPrompt = input.cacheOptimizationPrompt != null,
    )
    return GameScreenState(
        gameState = input.gameState,
        matchMode = input.matchMode,
        playerSetup = input.playerSetup,
        playerSetupUi = buildPlayerSetupUiState(
            setup = input.playerSetup,
            autoPlayDelaySetting = input.autoPlayDelaySetting,
            engineName = input.engineName,
        ),
        autoPlayDelaySetting = input.autoPlayDelaySetting,
        searchTimeSettings = input.searchTimeSettings,
        searchTimeBenchmarkAverages = input.searchTimeBenchmarkAverages,
        playLevel = input.playLevel,
        matchSeats = input.matchSeats,
        uxOptions = input.uxOptions,
        engine = EngineUiState(
            name = input.engineName,
            diagnostic = input.engineDiagnostic,
            profile = input.engineProfile,
            isReady = input.isEngineReady,
            isBusy = input.isEngineBusy,
            message = input.engineMessage,
        ),
        analysis = AnalysisUiState(
            preset = input.analysisPreset,
            cacheStats = input.analysisCacheStats,
            topMovesEnabled = input.topMovesEnabled,
            candidateMoves = input.candidateMoves,
            candidateText = input.candidateText,
            reviewAnalysis = input.reviewAnalysis,
            reviewCandidateMoves = input.reviewCandidateMoves,
            moveReviews = input.moveReviews,
            moveReviewText = input.moveReviewText,
            lastMoveText = input.lastMoveText,
        ),
        score = ScoreUiState(
            text = input.scoreText,
            estimate = input.scoreEstimate,
            snapshots = input.scoreSnapshots,
            isGraphExpanded = input.isScoreGraphExpanded,
        ),
        turnStatusText = input.matchSeats.turnStatusText(input.isEngineBusy),
        turnTimeText = input.turnTimeText,
        actionButtons = buildGameActionButtonStates(input),
        resumePrompt = input.pendingSavedSession
            ?.takeIf { promptVisibility.showResumePrompt }
            ?.let(::ResumePromptState),
        cacheOptimizationPrompt = input.cacheOptimizationPrompt
            ?.takeIf { promptVisibility.showCacheOptimizationPrompt },
        isGameEnded = input.isGameEnded,
        endgameLog = input.endgameLog,
    )
}

internal data class EngineUiState(
    val name: String,
    val diagnostic: String,
    val profile: EngineProfile,
    val isReady: Boolean,
    val isBusy: Boolean,
    val message: String,
)

internal data class AnalysisUiState(
    val preset: AnalysisPreset,
    val cacheStats: String,
    val topMovesEnabled: Boolean,
    val candidateMoves: List<CandidateMove>,
    val candidateText: String,
    val reviewAnalysis: MoveAnalysisSnapshot,
    val reviewCandidateMoves: List<CandidateMove>,
    val moveReviews: List<MoveReviewMarker>,
    val moveReviewText: String,
    val lastMoveText: String,
)

internal data class ScoreUiState(
    val text: String,
    val estimate: ScoreEstimate?,
    val snapshots: List<ScoreSnapshot>,
    val isGraphExpanded: Boolean,
)

internal data class ResumePromptState(
    val snapshot: SavedGameSnapshot,
)

internal enum class GameActionButtonRole {
    Pass,
    Undo,
    TopMoves,
    Eval,
}

internal data class GameActionButtonState(
    val role: GameActionButtonRole,
    val label: String,
    val event: GameUiEvent,
    val enabled: Boolean,
    val isFilled: Boolean,
)

internal fun buildGameActionButtonStates(input: GameScreenStateInput): List<GameActionButtonState> {
    val canPlayOnBoard = !input.isGameEnded &&
        input.matchSeats.current.canAcceptBoardInput
    val topMovesButtonEnabled = !input.isGameEnded &&
        input.isEngineReady &&
        (!input.isEngineBusy || input.topMovesEnabled)

    return listOf(
        GameActionButtonState(
            role = GameActionButtonRole.Pass,
            label = "Pass",
            event = GameUiEvent.Pass,
            enabled = canPlayOnBoard,
            isFilled = true,
        ),
        GameActionButtonState(
            role = GameActionButtonRole.Undo,
            label = "Undo",
            event = GameUiEvent.UndoLastTurn,
            enabled = !input.isEngineBusy &&
                input.gameState.moves.isNotEmpty() &&
                (input.isEngineReady || input.matchMode == MatchMode.LocalTwoPlayer),
            isFilled = false,
        ),
        GameActionButtonState(
            role = GameActionButtonRole.TopMoves,
            label = "Top Moves",
            event = GameUiEvent.ToggleTopMoves,
            enabled = topMovesButtonEnabled,
            isFilled = input.topMovesEnabled,
        ),
        GameActionButtonState(
            role = GameActionButtonRole.Eval,
            label = "Eval",
            event = GameUiEvent.RequestScoreEstimate,
            enabled = !input.isEngineBusy &&
                (input.isEngineReady || input.matchMode == MatchMode.LocalTwoPlayer),
            isFilled = false,
        ),
    )
}

internal data class KaTrainUxOptions(
    val showCoordinates: Boolean = true,
    val showMoveNumbers: Boolean = false,
    val showLastMoveRing: Boolean = true,
    val showOwnershipOverlay: Boolean = true,
)
