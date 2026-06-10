package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.match.applyAiTurn
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.TurnAnalysisPurpose
import com.worksoc.goaicoach.shared.turnAnalysisLimitFor

internal data class EngineStartupResult(
    val message: String,
    val scoreSnapshot: ScoreSnapshot?,
)

internal data class AutoAiTurnResult(
    val turnOutcome: TurnOutcome,
    val scoreEstimate: ScoreEstimate?,
    val profile: EngineProfile,
    val playLevel: PlayLevelSetting,
)

internal data class LocalEngineMoveResult(
    val estimate: ScoreEstimate? = null,
    val endgame: AiEndgameResolution? = null,
)

internal suspend fun EngineAdapter.startEngineSession(
    profile: EngineProfile,
    state: GameState,
): EngineStartupResult {
    val init = initialize(profile)
    val newGame = newGame(state.boardSize, state.ruleset)
    val estimate = runCatching {
        estimateScore(scoreGraphAnalysisLimit(profile))
    }.getOrNull()
    return EngineStartupResult(
        message = "Ready for ${state.boardSize.value}x${state.boardSize.value} match.\n${init.message}\n${newGame.message}",
        scoreSnapshot = estimate?.let { ScoreTimeline.fromEstimate(state.moves.size, it) },
    )
}

internal suspend fun EngineAdapter.startNewEngineGame(
    profile: EngineProfile,
    boardSize: BoardSize,
    ruleset: Ruleset,
): EngineStartupResult {
    configure(profile)
    val status = newGame(boardSize, ruleset)
    val estimate = runCatching {
        estimateScore(scoreGraphAnalysisLimit(profile))
    }.getOrNull()
    return EngineStartupResult(
        message = status.message,
        scoreSnapshot = estimate?.let { ScoreTimeline.fromEstimate(0, it) },
    )
}

internal suspend fun EngineAdapter.syncToGameState(state: GameState): EngineStatus {
    val status = newGame(state.boardSize, state.ruleset)
    state.moves.forEach { move ->
        playMove(move)
    }
    return status
}

internal suspend fun EngineAdapter.syncAndEstimateGraphScore(
    state: GameState,
    profile: EngineProfile,
): ScoreEstimate {
    syncToGameState(state)
    return estimateScore(scoreGraphAnalysisLimit(profile))
}

internal suspend fun EngineAdapter.configureSyncAndEstimateGraphScore(
    state: GameState,
    profile: EngineProfile,
): ScoreEstimate {
    configure(profile)
    return syncAndEstimateGraphScore(state, profile)
}

internal suspend fun EngineAdapter.runAutoAiTurn(
    currentState: GameState,
    playLevel: PlayLevelSetting,
    currentProfile: EngineProfile,
): AutoAiTurnResult {
    val aiPlayer = currentState.nextPlayer
    val turnProfile = playLevel.toEngineProfile(currentProfile)
    configure(turnProfile)
    syncToGameState(currentState)
    val outcome = applyAiTurn(
        engineAdapter = this,
        currentState = currentState,
        aiPlayer = aiPlayer,
        playLevel = playLevel,
    )
    val estimate = runCatching {
        estimateScore(scoreGraphAnalysisLimit(turnProfile))
    }.getOrNull()
    return AutoAiTurnResult(
        turnOutcome = outcome,
        scoreEstimate = estimate,
        profile = turnProfile,
        playLevel = playLevel,
    )
}

internal suspend fun EngineAdapter.syncAfterHumanMove(
    afterMove: GameState,
    profile: EngineProfile,
    move: Move,
    previousReviewCandidates: List<CandidateMove>,
): LocalEngineMoveResult {
    syncToGameState(afterMove)
    return if (afterMove.hasConsecutivePasses() || afterMove.isBoardFull()) {
        LocalEngineMoveResult(
            endgame = resolveAiEndgame(
                engineAdapter = this,
                originalState = afterMove,
                estimateLimit = scoreGraphAnalysisLimit(profile),
                prePassCandidates = if (move is Move.Pass) {
                    previousReviewCandidates
                } else {
                    emptyList()
                },
            ),
        )
    } else {
        LocalEngineMoveResult(
            estimate = estimateScore(scoreGraphAnalysisLimit(profile)),
        )
    }
}

internal suspend fun EngineAdapter.estimateScoreForState(
    state: GameState,
    profile: EngineProfile,
    syncFirst: Boolean,
): ScoreEstimate {
    if (syncFirst) {
        syncToGameState(state)
    }
    return estimateScore(profile.analysisLimit)
}

internal suspend fun EngineAdapter.resolveEndgameForState(
    state: GameState,
    profile: EngineProfile,
    prePassCandidates: List<CandidateMove>,
): AiEndgameResolution =
    resolveAiEndgame(
        engineAdapter = this,
        originalState = state,
        estimateLimit = scoreGraphAnalysisLimit(profile),
        prePassCandidates = prePassCandidates,
    )

internal fun scoreGraphAnalysisLimit(profile: EngineProfile): AnalysisLimit =
    profile.turnAnalysisLimitFor(TurnAnalysisPurpose.ScoreGraph)

internal fun localScoreSnapshot(state: GameState): ScoreSnapshot =
    ScoreTimeline.fromFinalScore(
        moveNumber = state.moves.size,
        finalScore = BoardScorer.score(state),
        source = ScoreSnapshotSource.LocalAreaEstimate,
    )
