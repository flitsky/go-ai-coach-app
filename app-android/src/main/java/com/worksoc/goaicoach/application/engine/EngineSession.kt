package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.application.endgame.resolveAiEndgame
import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.match.MatchReferee
import com.worksoc.goaicoach.match.applyAiTurn
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
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

internal const val AssistantJudgeDeadStonesTimeCapMillis: Long = 2_000L
internal const val AssistantJudgeFinalScoreTimeCapMillis: Long = 1_000L
internal const val AssistantJudgeEndgameTotalTimeCapMillis: Long =
    AssistantJudgeDeadStonesTimeCapMillis + AssistantJudgeFinalScoreTimeCapMillis

internal fun EngineProfile.withAssistantJudgeDeadStonesTimeCap(): EngineProfile =
    copy(
        analysisLimit = analysisLimit.copy(timeMillis = AssistantJudgeDeadStonesTimeCapMillis),
    )

internal fun EngineProfile.withAssistantJudgeFinalScoreTimeCap(): EngineProfile =
    copy(
        analysisLimit = analysisLimit.copy(timeMillis = AssistantJudgeFinalScoreTimeCapMillis),
    )

internal suspend fun EngineCoreApi.startEngineSession(
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

internal suspend fun EngineCoreApi.startNewEngineGame(
    profile: EngineProfile,
    boardSize: BoardSize,
    ruleset: Ruleset,
): EngineStartupResult {
    // A fresh process is intentional here. KataGo's GTP search tree can survive
    // clear_board across repeated games, causing the next game to replay nearly
    // instantly from retained search data.
    stop()
    initialize(profile)
    val status = newGame(boardSize, ruleset)
    val estimate = runCatching {
        estimateScore(scoreGraphAnalysisLimit(profile))
    }.getOrNull()
    return EngineStartupResult(
        message = status.message,
        scoreSnapshot = estimate?.let { ScoreTimeline.fromEstimate(0, it) },
    )
}

internal suspend fun EngineCoreApi.syncToGameState(state: GameState): EngineStatus {
    val status = newGame(state.boardSize, state.ruleset)
    state.moves.forEach { move ->
        playMove(move)
    }
    return status
}

internal suspend fun EngineCoreApi.syncAndEstimateGraphScore(
    state: GameState,
    profile: EngineProfile,
): ScoreEstimate {
    syncToGameState(state)
    return estimateScore(scoreGraphAnalysisLimit(profile))
}

internal suspend fun EngineCoreApi.configureSyncAndEstimateGraphScore(
    state: GameState,
    profile: EngineProfile,
): ScoreEstimate {
    configure(profile)
    return syncAndEstimateGraphScore(state, profile)
}

internal suspend fun EngineCoreApi.runAutoAiTurn(
    currentState: GameState,
    playLevel: PlayLevelSetting,
    currentProfile: EngineProfile,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    searchMode: EngineSearchMode = EngineSearchMode.GtpStatefulFast,
    isolateSearchCache: Boolean = false,
): AutoAiTurnResult {
    val aiPlayer = currentState.nextPlayer
    val turnProfile = playLevel.toEngineProfile(currentProfile, searchTimeSettings)
    configure(turnProfile)
    syncToGameState(currentState)
    val outcome = applyAiTurn(
        engineAdapter = this,
        currentState = currentState,
        aiPlayer = aiPlayer,
        playLevel = playLevel,
        searchTimeSettings = searchTimeSettings,
        searchMode = searchMode,
        isolateSearchCache = isolateSearchCache,
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

internal suspend fun EngineCoreApi.syncAfterHumanMove(
    afterMove: GameState,
    profile: EngineProfile,
    move: Move,
    previousReviewCandidates: List<CandidateMove>,
): LocalEngineMoveResult {
    val syncReplayStartMillis = System.currentTimeMillis()
    syncToGameState(afterMove)
    val syncReplayMs = System.currentTimeMillis() - syncReplayStartMillis
    return if (MatchReferee.shouldResolveEndgame(afterMove)) {
        val deadStonesProfile = profile.withAssistantJudgeDeadStonesTimeCap()
        val finalScoreProfile = profile.withAssistantJudgeFinalScoreTimeCap()
        LocalEngineMoveResult(
            endgame = resolveAiEndgame(
                engineAdapter = this,
                originalState = afterMove,
                estimateLimit = scoreGraphAnalysisLimit(deadStonesProfile),
                prePassCandidates = if (move is Move.Pass) {
                    previousReviewCandidates
                } else {
                    emptyList()
                },
                syncReplayMs = syncReplayMs,
                assistantJudgeDeadStonesProfile = deadStonesProfile,
                assistantJudgeFinalScoreProfile = finalScoreProfile,
            ),
        )
    } else {
        LocalEngineMoveResult(
            estimate = estimateScore(scoreGraphAnalysisLimit(profile)),
        )
    }
}

internal suspend fun EngineCoreApi.estimateScoreForState(
    state: GameState,
    profile: EngineProfile,
    syncFirst: Boolean,
): ScoreEstimate {
    if (syncFirst) {
        syncToGameState(state)
    }
    return estimateScore(profile.analysisLimit)
}

internal suspend fun EngineCoreApi.resolveEndgameForState(
    state: GameState,
    profile: EngineProfile,
    prePassCandidates: List<CandidateMove>,
): AiEndgameResolution {
    val deadStonesProfile = profile.withAssistantJudgeDeadStonesTimeCap()
    val finalScoreProfile = profile.withAssistantJudgeFinalScoreTimeCap()
    return resolveAiEndgame(
        engineAdapter = this,
        originalState = state,
        estimateLimit = scoreGraphAnalysisLimit(deadStonesProfile),
        prePassCandidates = prePassCandidates,
        assistantJudgeDeadStonesProfile = deadStonesProfile,
        assistantJudgeFinalScoreProfile = finalScoreProfile,
    )
}

internal fun scoreGraphAnalysisLimit(profile: EngineProfile): AnalysisLimit =
    profile.turnAnalysisLimitFor(TurnAnalysisPurpose.ScoreGraph)

internal fun localScoreSnapshot(state: GameState): ScoreSnapshot =
    ScoreTimeline.fromFinalScore(
        moveNumber = state.moves.size,
        finalScore = BoardScorer.score(state),
        source = ScoreSnapshotSource.LocalAreaEstimate,
    )
