package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.match.applyAiTurn
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
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

internal class LocalEngineCoreSessionDelegate(
    private val coreApi: EngineCoreApi,
    private val clock: EngineClock = SystemEngineClock,
) {
    suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult =
        coreApi.startEngineSession(profile, state)

    suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStartupResult =
        coreApi.startNewEngineGame(profile, boardSize, ruleset)

    suspend fun syncAndAnalyzePosition(
        state: GameState,
        limit: AnalysisLimit,
    ): AnalysisResult {
        coreApi.syncToGameState(state)
        return coreApi.analyze(limit)
    }

    suspend fun syncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        coreApi.syncAndEstimateGraphScore(state, profile)

    suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        coreApi.configureSyncAndEstimateGraphScore(state, profile)

    suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
        analysisProvider: suspend (AnalysisLimit) -> AnalysisResult,
    ): AutoAiTurnResult {
        val aiPlayer = currentState.nextPlayer
        val turnProfile = playLevel.toEngineProfile(currentProfile, searchTimeSettings)
        coreApi.configure(turnProfile)
        coreApi.syncToGameState(currentState)
        val outcome = applyAiTurn(
            engineAdapter = coreApi,
            currentState = currentState,
            aiPlayer = aiPlayer,
            playLevel = playLevel,
            searchTimeSettings = searchTimeSettings,
            searchMode = searchMode,
            isolateSearchCache = isolateSearchCache,
            analysisProvider = analysisProvider,
        )
        val estimate = runCatching {
            coreApi.estimateScore(scoreGraphAnalysisLimit(turnProfile))
        }.getOrNull()
        return AutoAiTurnResult(
            turnOutcome = outcome,
            scoreEstimate = estimate,
            profile = turnProfile,
            playLevel = playLevel,
        )
    }

    suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult =
        coreApi.syncAfterHumanMove(
            afterMove = afterMove,
            profile = profile,
            move = move,
            previousReviewCandidates = previousReviewCandidates,
            clock = clock,
        )

    suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate =
        coreApi.estimateScoreForState(
            state = state,
            profile = profile,
            syncFirst = syncFirst,
        )

    suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution =
        coreApi.resolveEndgameForState(
            state = state,
            profile = profile,
            prePassCandidates = prePassCandidates,
        )

    suspend fun undoMove(): EngineStatus =
        coreApi.undoMove()

    suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile =
        coreApi.runStartupEngineBenchmark(
            restoreState = restoreState,
            nowMillis = nowMillis,
            onProgress = onProgress,
        )
}
