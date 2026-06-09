package com.worksoc.goaicoach.presentation

import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.persistence.SavedGameSnapshot
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.application.MoveReviewMarker

internal data class GameScreenState(
    val gameState: GameState,
    val matchMode: MatchMode,
    val playerSetup: PlayerSetup,
    val playLevel: PlayLevelSetting,
    val uxOptions: KaTrainUxOptions,
    val engine: EngineUiState,
    val analysis: AnalysisUiState,
    val score: ScoreUiState,
    val resumePrompt: ResumePromptState?,
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
    val playLevel: PlayLevelSetting,
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
    val pendingSavedSession: SavedGameSnapshot?,
    val shouldShowResumePrompt: Boolean,
    val hasCompletedEngineStartup: Boolean,
    val isGameEnded: Boolean,
    val endgameLog: String,
)

internal fun buildGameScreenState(input: GameScreenStateInput): GameScreenState =
    GameScreenState(
        gameState = input.gameState,
        matchMode = input.matchMode,
        playerSetup = input.playerSetup,
        playLevel = input.playLevel,
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
        resumePrompt = input.pendingSavedSession
            ?.takeIf {
                input.shouldShowResumePrompt &&
                    input.hasCompletedEngineStartup &&
                    !input.isEngineBusy
            }
            ?.let(::ResumePromptState),
        isGameEnded = input.isGameEnded,
        endgameLog = input.endgameLog,
    )

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

internal data class KaTrainUxOptions(
    val showCoordinates: Boolean = true,
    val showMoveNumbers: Boolean = false,
    val showLastMoveRing: Boolean = true,
    val showOwnershipOverlay: Boolean = true,
)
